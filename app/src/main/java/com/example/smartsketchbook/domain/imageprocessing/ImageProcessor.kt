package com.example.smartsketchbook.domain.imageprocessing

import android.graphics.Bitmap
import java.nio.ByteBuffer

interface ImageProcessor {
    suspend fun preprocessBitmap(drawingBitmap: Bitmap): ByteBuffer
}


