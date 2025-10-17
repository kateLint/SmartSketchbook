package com.example.smartsketchbook.domain.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.FloatBuffer

/**
 * Basic preprocessing for ML: resize to a small, fixed size.
 * Future work: grayscale conversion, background inversion, normalization.
 */
object BitmapPreprocessor {

    /**
     * Resize [original] to [targetSize] x [targetSize]. Default is 28x28.
     */
    fun preprocessBitmap(
        original: Bitmap,
        targetSize: Int = 28
    ): Bitmap {
        if (targetSize <= 0) return original

        val srcW = original.width.coerceAtLeast(1)
        val srcH = original.height.coerceAtLeast(1)

        // Scale so the longest side fits targetSize, preserving aspect
        val scale = targetSize.toFloat() / maxOf(srcW, srcH).toFloat()
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)

        val scaled = if (srcW == scaledW && srcH == scaledH) original
        else Bitmap.createScaledBitmap(original, scaledW, scaledH, true)

        // Create centered target bitmap
        val target = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE) // background; change if model expects black

        val left = ((targetSize - scaledW) / 2f)
        val top = ((targetSize - scaledH) / 2f)
        canvas.drawBitmap(scaled, left, top, null)

        // Ensure consistent config
        return if (target.config != Bitmap.Config.ARGB_8888) {
            target.copy(Bitmap.Config.ARGB_8888, false)
        } else target
    }

    /**
     * Convert a bitmap to a normalized FloatArray in [0, 1].
     * channels=1 produces grayscale (HWC order -> [y,x,1]); channels=3 produces RGB (HWC -> [y,x,3]).
     */
    fun bitmapToFloatArray(bitmap: Bitmap, channels: Int = 1): FloatArray {
        require(channels == 1 || channels == 3) { "channels must be 1 (grayscale) or 3 (RGB)" }
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val out = FloatArray(width * height * channels)
        var outIdx = 0
        var pIdx = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = pixels[pIdx++]
                val r = (c shr 16 and 0xFF)
                val g = (c shr 8 and 0xFF)
                val b = (c and 0xFF)
                if (channels == 1) {
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    out[outIdx++] = gray
                } else {
                    out[outIdx++] = r / 255.0f
                    out[outIdx++] = g / 255.0f
                    out[outIdx++] = b / 255.0f
                }
            }
        }
        return out
    }

    /**
     * Draw scaled+centered [original] into [target] which must be pre-allocated to targetSize x targetSize.
     * Background is white.
     */
    fun preprocessInto(
        original: Bitmap,
        target: Bitmap,
        targetSize: Int = 28,
        centerByMass: Boolean = true,
        fitFraction: Float = 0.9f
    ) {
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)

        val srcW = original.width.coerceAtLeast(1)
        val srcH = original.height.coerceAtLeast(1)
        val base = maxOf(srcW, srcH).toFloat()
        val scale = (targetSize * fitFraction) / base
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)
        val targetCenter = (targetSize - 1) / 2f
        val scaled = if (srcW == scaledW && srcH == scaledH) original
        else Bitmap.createScaledBitmap(original, scaledW, scaledH, true)

        var left = (targetSize - scaledW) / 2f
        var top = (targetSize - scaledH) / 2f

        if (centerByMass) {
            // Compute intensity centroid (using inverted grayscale to emphasize strokes)
            val px = IntArray(scaledW * scaledH)
            scaled.getPixels(px, 0, scaledW, 0, 0, scaledW, scaledH)
            var sum = 0f
            var sumX = 0f
            var sumY = 0f
            var idx = 0
            for (y in 0 until scaledH) {
                for (x in 0 until scaledW) {
                    val c = px[idx++]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    val intensity = 1f - gray
                    sum += intensity
                    sumX += x * intensity
                    sumY += y * intensity
                }
            }
            if (sum > 1e-6f) {
                val cx = sumX / sum
                val cy = sumY / sum
                // Place centroid at target center
                left = targetCenter - cx
                top = targetCenter - cy
            }
        }

        canvas.drawBitmap(scaled, left, top, null)
    }

    /**
     * Write normalized pixels from [bitmap] into [dest] FloatBuffer (HWC order). Buffer position is rewound.
     */
    fun bitmapToFloatBuffer(bitmap: Bitmap, channels: Int, dest: FloatBuffer) {
        require(channels == 1 || channels == 3)
        dest.rewind()
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[idx++]
                val r = (c shr 16 and 0xFF)
                val g = (c shr 8 and 0xFF)
                val b = (c and 0xFF)
                if (channels == 1) {
                    val gray = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                    // Invert and apply gentle contrast curve (no hard threshold)
                    val inv = 1f - gray
                    val boosted = (inv * 1.15f).coerceIn(0f, 1f)
                    val curved = kotlin.math.sqrt(boosted)
                    dest.put(curved)
                } else {
                    dest.put(r / 255.0f)
                    dest.put(g / 255.0f)
                    dest.put(b / 255.0f)
                }
            }
        }
        dest.rewind()
    }
}


