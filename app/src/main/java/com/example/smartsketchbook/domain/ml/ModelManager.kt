package com.example.smartsketchbook.domain.ml

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

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
}


