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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartsketchbook.ui.components.DrawingCanvas
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel
import com.example.smartsketchbook.ui.util.captureComposableToBitmap

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
    Column(modifier = modifier.padding(16.dp)) {
        Row {
            Button(onClick = { viewModel.clearCanvas() }) { Text("Clear") }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { viewModel.onCaptureDrawing() }) { Text("Classify") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Sketchbook Status: ${state.statusMessage}")

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
        }

        LaunchedEffect(viewModel, captureSize.value) {
            viewModel.captureRequests.collect {
                val size = captureSize.value
                if (size.width > 0 && size.height > 0) {
                    val bmp = captureComposableToBitmap(
                        context = context,
                        widthPx = size.width,
                        heightPx = size.height
                    ) {
                        CapturableDrawing(viewModel = viewModel)
                    }
                    viewModel.onBitmapCaptured(bmp)
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
    val paths by viewModel.paths.collectAsState()
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
            paths.forEach { path ->
                nativeCanvas.drawPath(path, androidPaint)
            }
            activePath?.path?.let { composePath ->
                val androidPath = composePath.asAndroidPath()
                nativeCanvas.drawPath(androidPath, androidPaint)
            }
        }
    }
}