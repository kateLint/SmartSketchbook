package com.example.smartsketchbook.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

object BitmapConverter {

    /**
     * Renders provided paths onto a bitmap with given width/height and converts to 28x28 grayscale.
     * The output is white strokes on black background, suitable for common digit/quick-draw models.
     */
    fun toMonochrome28x28(
        width: Int,
        height: Int,
        paths: List<Path>,
        currentPath: Path?,
        strokeWidth: Float
    ): Bitmap {
        val baseWidth = if (width <= 0) 1 else width
        val baseHeight = if (height <= 0) 1 else height

        val baseBitmap = Bitmap.createBitmap(baseWidth, baseHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baseBitmap)

        // Black background
        canvas.drawColor(Color.BLACK)

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.strokeWidth = strokeWidth
        }

        fun drawPathIfNotEmpty(path: Path?) {
            if (path != null && !path.isEmpty) {
                canvas.drawPath(path, paint)
            }
        }

        paths.forEach { drawPathIfNotEmpty(it) }
        drawPathIfNotEmpty(currentPath)

        // Scale to 28x28 using matrix (keeps aspect ratio by fitting, then center crop)
        val targetSize = 28
        val scaled = scaleAspectFit(baseBitmap, targetSize, targetSize)
        val cropped = centerCrop(scaled, targetSize, targetSize)

        // Ensure grayscale (already white on black; but normalize via ColorMatrix if needed later)
        return cropped
    }

    private fun scaleAspectFit(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = minOf(targetW / src.width.toFloat(), targetH / src.height.toFloat())
        val newW = (src.width * scale).toInt().coerceAtLeast(1)
        val newH = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun centerCrop(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        if (src.width == targetW && src.height == targetH) return src
        val x = ((src.width - targetW) / 2).coerceAtLeast(0)
        val y = ((src.height - targetH) / 2).coerceAtLeast(0)
        val cw = minOf(targetW, src.width - x)
        val ch = minOf(targetH, src.height - y)
        return Bitmap.createBitmap(src, x, y, cw, ch)
    }
}


