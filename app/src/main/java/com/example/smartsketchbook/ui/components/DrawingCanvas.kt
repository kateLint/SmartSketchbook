package com.example.smartsketchbook.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel

@Composable
fun DrawingCanvas(
    onDrawFinished: (Bitmap) -> Unit,
    viewModel: SketchbookViewModel,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 24f,
    strokeColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    val paths by viewModel.paths.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val androidPaint = remember(strokeWidth, strokeColor) {
        Paint().apply {
            isAntiAlias = true
            color = strokeColor.toArgb()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.strokeWidth = strokeWidth
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor)
            .onSizeChanged { canvasSize = it }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start: Offset ->
                            viewModel.startStroke(start.x, start.y)
                        },
                        onDrag = { change, _ ->
                            val position = change.position
                            viewModel.addPoint(position.x, position.y)
                            change.consume()
                        },
                        onDragEnd = {
                            viewModel.endStroke()
                            // Optional: auto-predict on finger release
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                viewModel.exportBitmap(
                                    canvasWidth = canvasSize.width,
                                    canvasHeight = canvasSize.height,
                                    strokeWidth = strokeWidth,
                                    onExported = onDrawFinished
                                )
                            }
                        },
                        onDragCancel = {
                            viewModel.endStroke()
                        }
                    )
                }
        ) {
            // Background
            drawRect(color = backgroundColor)

            // Draw paths using Android canvas for parity with bitmap rendering
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                paths.forEach { path ->
                    nativeCanvas.drawPath(path, androidPaint)
                }
                currentPath?.let { path ->
                    nativeCanvas.drawPath(path, androidPaint)
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            Button(onClick = { viewModel.undoLast() }) {
                Text(text = "Undo")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = { viewModel.clearCanvas() }) {
                Text(text = "Clear")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                enabled = canvasSize.width > 0 && canvasSize.height > 0,
                onClick = {
                    viewModel.exportBitmap(
                        canvasWidth = canvasSize.width,
                        canvasHeight = canvasSize.height,
                        strokeWidth = strokeWidth,
                        onExported = onDrawFinished
                    )
                }
            ) {
                Text(text = "Finish")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                enabled = canvasSize.width > 0 && canvasSize.height > 0,
                onClick = {
                    viewModel.exportBitmap(
                        canvasWidth = canvasSize.width,
                        canvasHeight = canvasSize.height,
                        strokeWidth = strokeWidth,
                        onExported = onDrawFinished
                    )
                }
            ) {
                Text(text = "Predict")
            }
        }
    }
}
