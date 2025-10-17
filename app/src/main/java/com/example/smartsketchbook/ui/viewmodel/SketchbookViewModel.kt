package com.example.smartsketchbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.state.DrawingPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import android.graphics.Path as AndroidPath
import android.graphics.Bitmap
import com.example.smartsketchbook.utils.BitmapConverter
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.smartsketchbook.domain.ml.BitmapPreprocessor

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

    // New: activePath holds the points of the stroke currently being drawn
    val activePath: MutableStateFlow<DrawingPath?> = MutableStateFlow(null)

    // One-shot requests to UI to capture a bitmap of the drawing
    private val _captureRequests: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    // For debugging/previewing the preprocessed bitmap
    private val _classifiedBitmap: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
    val classifiedBitmap: StateFlow<Bitmap?> = _classifiedBitmap

    // Motion actions to drive stroke creation from UI
    sealed class MotionAction {
        object Start : MotionAction()
        object Move : MotionAction()
        object End : MotionAction()
    }

    // Example function to update the status (for demonstration, though not strictly required by the prompt)
    fun updateStatus(newStatus: String) {
        _uiState.value = _uiState.value.copy(statusMessage = newStatus)
    }

    fun clearCanvas() {
        viewModelScope.launch {
            _paths.value = emptyList()
            _currentPath.value = null
            activePath.value = null
            _classifiedBitmap.value = null
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
        activePath.value = DrawingPath(points = listOf(androidx.compose.ui.geometry.Offset(x, y)))
    }

    fun addPoint(x: Float, y: Float) {
        _currentPath.value?.lineTo(x, y)
        activePath.value = activePath.value?.let { current ->
            current.copy(points = current.points + androidx.compose.ui.geometry.Offset(x, y))
        }
    }

    fun endStroke() {
        _currentPath.value?.let { finished ->
            _paths.value = _paths.value + finished
        }
        _currentPath.value = null
        activePath.value = null
    }

    // Unified motion handler called by UI
    fun handleMotionEvent(offset: androidx.compose.ui.geometry.Offset, action: MotionAction) {
        when (action) {
            is MotionAction.Start -> {
                val path = AndroidPath()
                path.moveTo(offset.x, offset.y)
                _currentPath.value = path
                activePath.value = DrawingPath(points = listOf(offset))
            }
            is MotionAction.Move -> {
                _currentPath.value?.lineTo(offset.x, offset.y)
                activePath.value = activePath.value?.let { current ->
                    current.copy(points = current.points + offset)
                } ?: DrawingPath(points = listOf(offset))
            }
            is MotionAction.End -> {
                _currentPath.value?.let { finished ->
                    _paths.value = _paths.value + finished
                }
                _currentPath.value = null
                activePath.value = null
            }
        }
    }

    // Called when the user finishes drawing and wants to classify
    fun onCaptureDrawing() {
        viewModelScope.launch {
            // Clear in-progress stroke points
            activePath.value = null
            // Signal UI to capture the composable into a Bitmap
            _captureRequests.tryEmit(Unit)
        }
    }

    // Call after receiving a captured drawing bitmap from UI
    fun onBitmapCaptured(captured: Bitmap) {
        viewModelScope.launch {
            val preprocessed = BitmapPreprocessor.preprocessBitmap(captured, targetSize = 28)
            _classifiedBitmap.value = preprocessed
        }
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