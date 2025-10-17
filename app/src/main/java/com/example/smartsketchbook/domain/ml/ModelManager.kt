package com.example.smartsketchbook.domain.ml

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.io.FileOutputStream

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("model_meta", Context.MODE_PRIVATE)
    }

    fun getSavedModelVersion(id: String): Int = prefs.getInt(versionKey(id), 0)

    fun saveModelVersion(id: String, version: Int) {
        prefs.edit().putInt(versionKey(id), version).apply()
    }

    fun checkLocalModelStatus(model: AvailableModel): Boolean {
        // Simulated remote version: always one higher than static list
        val remoteVersion = model.version + 1
        val localVersion = getSavedModelVersion(model.id)
        return localVersion < remoteVersion
    }

    private fun versionKey(id: String) = "model_version_$id"

    suspend fun downloadModel(model: AvailableModel, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = ModelDownloader.targetDir(context)
        val outFile = File(dir, model.fileName)
        context.assets.open(model.fileName).use { input ->
            FileOutputStream(outFile).use { output ->
                val total = input.available().toLong().coerceAtLeast(1L)
                val buf = ByteArray(16 * 1024)
                var copied = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r <= 0) break
                    output.write(buf, 0, r)
                    copied += r
                    onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    delay(50)
                }
                output.flush()
            }
        }
        outFile
    }
}


