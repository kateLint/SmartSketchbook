package com.example.smartsketchbook.domain.ml

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object ModelDownloader {
    fun targetDir(context: Context): File = File(context.filesDir, "models").apply { if (!exists()) mkdirs() }

    // Simulated download: copy from assets to internal storage
    fun downloadModel(context: Context, assetFileName: String, targetFileName: String): File {
        val dir = targetDir(context)
        val outFile = File(dir, targetFileName)
        if (outFile.exists() && outFile.length() > 0L) return outFile
        context.assets.open(assetFileName).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        return outFile
    }
}


