package com.example.smartsketchbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.smartsketchbook.ui.state.SketchbookUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import android.graphics.Path as AndroidPath
import android.graphics.Bitmap
import com.example.smartsketchbook.utils.BitmapConverter
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * ViewModel for the sketchbook screen, responsible for UI-related data and state.
 *
 * The @HiltViewModel annotation is used by Hilt to generate the necessary factory
 * and allow the injection of dependencies (if any) via the @Inject constructor.
 */
@HiltViewModel
class SketchbookViewModel @Inject constructor() : ViewModel() {

    // Holds all UI-related state for the Sketchbook screen.
    private val _uiState: MutableStateFlow<SketchbookUiState> = MutableStateFlow(SketchbookUiState())

    // Read-only state exposed to the UI.
    val uiState: StateFlow<SketchbookUiState> = _uiState

    // Drawing state: list of finished paths and the in-progress path
    private val _paths: MutableStateFlow<List<AndroidPath>> = MutableStateFlow(emptyList())
    val paths: StateFlow<List<AndroidPath>> = _paths

    private val _currentPath: MutableStateFlow<AndroidPath?> = MutableStateFlow(null)
    val currentPath: StateFlow<AndroidPath?> = _currentPath

    // Example function to update the status (for demonstration, though not strictly required by the prompt)
    fun updateStatus(newStatus: String) {
        _uiState.value = _uiState.value.copy(statusMessage = newStatus)
    }

    fun clearCanvas() {
        viewModelScope.launch {
            _paths.value = emptyList()
            _currentPath.value = null
        }
    }

    fun undoLast() {
        viewModelScope.launch {
            if (_paths.value.isNotEmpty()) {
                _paths.value = _paths.value.dropLast(1)
            }
        }
    }

    fun startStroke(x: Float, y: Float) {
        val path = AndroidPath()
        path.moveTo(x, y)
        _currentPath.value = path
    }

    fun addPoint(x: Float, y: Float) {
        _currentPath.value?.lineTo(x, y)
    }

    fun endStroke() {
        _currentPath.value?.let { finished ->
            _paths.value = _paths.value + finished
        }
        _currentPath.value = null
    }

    fun exportBitmap(
        canvasWidth: Int,
        canvasHeight: Int,
        strokeWidth: Float,
        onExported: (Bitmap) -> Unit
    ) {
        val bitmap = BitmapConverter.toMonochrome28x28(
            width = canvasWidth,
            height = canvasHeight,
            paths = _paths.value,
            currentPath = _currentPath.value,
            strokeWidth = strokeWidth
        )
        onExported(bitmap)
    }
}