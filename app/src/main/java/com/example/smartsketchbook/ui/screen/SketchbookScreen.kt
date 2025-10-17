package com.example.smartsketchbook.ui.screen

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartsketchbook.ui.components.DrawingCanvas
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel
import com.example.smartsketchbook.ui.util.captureComposableToBitmap
import com.example.smartsketchbook.ui.util.CanvasRenderer
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun SketchbookRoute(
    modifier: Modifier = Modifier,
    viewModel: SketchbookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SketchbookScreen(state = uiState, modifier = modifier)
}

@Composable
fun SketchbookScreen(
    state: SketchbookUiState,
    modifier: Modifier = Modifier
) {
    val viewModel: SketchbookViewModel = hiltViewModel()
    val context = LocalContext.current
    val captureSize = remember { mutableStateOf(IntSize.Zero) }
    val classified by viewModel.classifiedBitmap.collectAsState()
    val result by viewModel.classificationResult.collectAsState()
    val isClassifying by viewModel.isClassifying.collectAsState()
            val isDownloading by viewModel.isDownloadingModel.collectAsState()
    val strokeWidthPx = with(LocalDensity.current) { 12.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
    Column(modifier = modifier.padding(innerPadding).padding(16.dp)) {
        Row {
            Button(onClick = { viewModel.clearCanvas() }) { Text("Clear") }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { viewModel.onCaptureDrawing() }, enabled = !isClassifying) { Text(if (isClassifying) "Capturing..." else "Capture") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Sketchbook Status: ${state.statusMessage}")
            Spacer(modifier = Modifier.width(12.dp))
            classified?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Preprocessed Thumbnail",
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, Color.Gray)
                        .padding(2.dp)
                )
            }
        }

        // Settings moved to Settings screen

        result?.let {
            val percent = String.format("%.1f%%", (it.confidence * 100f))
            Text(
                text = "${it.label} (Confidence: $percent)",
                modifier = Modifier.padding(top = 8.dp)
            )
            ConfidenceBarDisplay(scores = it.scores, topIndex = it.top3Indices.firstOrNull() ?: 0)
        }
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(420.dp)
                .onSizeChanged { captureSize.value = it }
        ) {
            DrawingCanvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                viewModel = viewModel,
                onDrawFinished = { bitmap: Bitmap ->
                    viewModel.updateStatus(
                        "Captured ${bitmap.width}x${bitmap.height}"
                    )
                }
            )
            if (isClassifying) {
                Box(modifier = Modifier
                    .matchParentSize()) {
                    CircularProgressIndicator(modifier = Modifier
                        .align(Alignment.Center))
                }
            }
                        if (isDownloading) {
                            Box(modifier = Modifier
                                .matchParentSize()) {
                                CircularProgressIndicator(modifier = Modifier
                                    .align(Alignment.Center))
                            }
                        }
        }

        LaunchedEffect(viewModel, captureSize.value) {
            viewModel.captureRequests.collect {
                val size = captureSize.value
                if (size.width > 0 && size.height > 0) {
                    // Avoid offscreen ComposeView to prevent windowRecomposer crash; render via Android canvas
                    val bmp = CanvasRenderer.renderToBitmap(
                        widthPx = size.width,
                        heightPx = size.height,
                        renderedPaths = viewModel.renderedPaths.value,
                        activeComposePath = viewModel.activePath.value?.path,
                        strokeWidthPx = strokeWidthPx
                    )
                    val bounds = viewModel.getDrawingBounds()
                    val finalBitmap = bounds?.let { rect ->
                        // Inflate bounds by stroke width/2 + small padding to avoid cutting strokes
                        val pad = (strokeWidthPx / 2f) + 2f
                        val left = floor(rect.left - pad).toInt().coerceIn(0, bmp.width - 1)
                        val top = floor(rect.top - pad).toInt().coerceIn(0, bmp.height - 1)
                        val right = ceil(rect.right + pad).toInt().coerceIn(left + 1, bmp.width)
                        val bottom = ceil(rect.bottom + pad).toInt().coerceIn(top + 1, bmp.height)
                        val width = (right - left).coerceAtLeast(1)
                        val height = (bottom - top).coerceAtLeast(1)
                        Bitmap.createBitmap(bmp, left, top, width, height)
                    } ?: bmp
                    viewModel.onBitmapCaptured(finalBitmap)
                }
            }
        }
        LaunchedEffect(result) {
            if (result != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        LaunchedEffect(Unit) {
            viewModel.userMessages.collect { msg ->
                val result = snackbarHostState.showSnackbar(message = msg, actionLabel = "Download", duration = SnackbarDuration.Short)
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    // Trigger model update using current selection
                    viewModel.handleModelUpdate()
                }
            }
        }
    }
    }
}

@Composable
private fun CapturableDrawing(
    viewModel: SketchbookViewModel,
    strokeWidthDp: Dp = 12.dp,
    strokeColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val renderedPaths by viewModel.renderedPaths.collectAsState()
    val activePath by viewModel.activePath.collectAsState()
    val strokeWidthPx = with(LocalDensity.current) { strokeWidthDp.toPx() }

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
    ) {
        // Background
        drawRect(color = backgroundColor)

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val androidPaint = Paint().apply {
                isAntiAlias = true
                color = strokeColor.toArgb()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                this.strokeWidth = strokeWidthPx
            }
            renderedPaths.forEach { rendered ->
                androidPaint.color = rendered.colorInt
                nativeCanvas.drawPath(rendered.path, androidPaint)
            }
            activePath?.path?.let { composePath ->
                val androidPath = composePath.asAndroidPath()
                nativeCanvas.drawPath(androidPath, androidPaint)
            }
        }
    }
}