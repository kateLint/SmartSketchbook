package com.example.smartsketchbook.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import com.example.smartsketchbook.ui.state.RenderedPath

object CanvasRenderer {
    fun renderToBitmap(
        widthPx: Int,
        heightPx: Int,
        renderedPaths: List<RenderedPath>,
        activeComposePath: androidx.compose.ui.graphics.Path?,
        strokeWidthPx: Float,
        strokeColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Black,
        backgroundColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
    ): Bitmap {
        val w = if (widthPx > 0) widthPx else 1
        val h = if (heightPx > 0) heightPx else 1
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(backgroundColor.toArgb())

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.strokeWidth = strokeWidthPx
        }

        renderedPaths.forEach { rp ->
            paint.color = rp.colorInt
            canvas.drawPath(rp.path, paint)
        }

        activeComposePath?.let { cp ->
            paint.color = strokeColor.toArgb()
            val androidPath: Path = cp.asAndroidPath()
            canvas.drawPath(androidPath, paint)
        }

        return bmp
    }
}


