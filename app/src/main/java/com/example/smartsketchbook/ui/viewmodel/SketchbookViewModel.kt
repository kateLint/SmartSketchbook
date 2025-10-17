package com.example.smartsketchbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.state.DrawingPath
import com.example.smartsketchbook.ui.state.RenderedPath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import android.graphics.Path as AndroidPath
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.smartsketchbook.utils.BitmapConverter
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.example.smartsketchbook.domain.ml.BitmapPreprocessor
import com.example.smartsketchbook.domain.ml.SketchClassifier
import com.example.smartsketchbook.domain.ml.ClassificationResult
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * ViewModel for the sketchbook screen, responsible for UI-related data and state.
 *
 * The @HiltViewModel annotation is used by Hilt to generate the necessary factory
 * and allow the injection of dependencies (if any) via the @Inject constructor.
 */
@HiltViewModel
class SketchbookViewModel @Inject constructor(
    private val classifier: SketchClassifier
) : ViewModel() {

    // Holds all UI-related state for the Sketchbook screen.
    private val _uiState: MutableStateFlow<SketchbookUiState> = MutableStateFlow(SketchbookUiState())

    // Read-only state exposed to the UI.
    val uiState: StateFlow<SketchbookUiState> = _uiState

    // Drawing state: list of finished colored paths and the in-progress path
    private val _renderedPaths: MutableStateFlow<List<RenderedPath>> = MutableStateFlow(emptyList())
    val renderedPaths: StateFlow<List<RenderedPath>> = _renderedPaths

    private val _currentPath: MutableStateFlow<AndroidPath?> = MutableStateFlow(null)
    val currentPath: StateFlow<AndroidPath?> = _currentPath
    private var currentPathColorInt: Int = Color.Black.toArgb()

    // New: activePath holds the points of the stroke currently being drawn
    val activePath: MutableStateFlow<DrawingPath?> = MutableStateFlow(null)

    // One-shot requests to UI to capture a bitmap of the drawing
    private val _captureRequests: MutableSharedFlow<Unit> = MutableSharedFlow(extraBufferCapacity = 1)
    val captureRequests: SharedFlow<Unit> = _captureRequests

    // For debugging/previewing the preprocessed bitmap
    private val _classifiedBitmap: MutableStateFlow<Bitmap?> = MutableStateFlow(null)
    val classifiedBitmap: StateFlow<Bitmap?> = _classifiedBitmap

    private val _classificationResult: MutableStateFlow<ClassificationResult?> = MutableStateFlow(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult

    // Loading state during classification
    val isClassifying: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // Current drawing color
    val currentDrawingColor: MutableStateFlow<Color> = MutableStateFlow(Color.Black)
    fun setDrawingColor(color: Color) { currentDrawingColor.value = color }

    // A/B test: stroke width variant
    val isStrokeWidthVariantA: MutableStateFlow<Boolean> = MutableStateFlow(kotlin.random.Random.Default.nextBoolean())

    // CPU thread count control
    val cpuThreadCount: MutableStateFlow<Int> = MutableStateFlow(classifier.defaultCpuThreads())
    fun setCpuThreads(threads: Int) { cpuThreadCount.value = threads }

    // Hardware delegate status
    private val _hardwareDelegateStatus: MutableStateFlow<String> = MutableStateFlow("Initializing")
    val hardwareDelegateStatus: StateFlow<String> = _hardwareDelegateStatus

    // One-shot user messages (snackbar)
    val userMessages: MutableSharedFlow<String> = MutableSharedFlow(extraBufferCapacity = 1)

    // First-use tip
    val showInitialTip: MutableStateFlow<Boolean> = MutableStateFlow(true)

    init {
        // Reinitialize interpreter when CPU thread count changes
        viewModelScope.launch {
            cpuThreadCount.collect { t ->
                classifier.reinitializeForCpuThreads(t)
                _hardwareDelegateStatus.value = classifier.getDelegateStatus()
            }
        }
        _hardwareDelegateStatus.value = classifier.getDelegateStatus()
    }

    // Track previous touch point for path smoothing
    private var lastPoint: Offset? = null

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
            _renderedPaths.value = emptyList()
            _currentPath.value = null
            activePath.value = null
            _classifiedBitmap.value = null
            _classificationResult.value = null
            isClassifying.value = false
        }
    }

    fun undoLast() {
        viewModelScope.launch {
            if (_renderedPaths.value.isNotEmpty()) {
                _renderedPaths.value = _renderedPaths.value.dropLast(1)
            }
        }
    }

    fun startStroke(x: Float, y: Float) {
        val androidPath = AndroidPath()
        androidPath.moveTo(x, y)
        _currentPath.value = androidPath

        val composePath = ComposePath().apply { moveTo(x, y) }
        activePath.value = DrawingPath(path = composePath)
        currentPathColorInt = currentDrawingColor.value.toArgb()
        lastPoint = Offset(x, y)
    }

    fun addPoint(x: Float, y: Float) {
        val previous = lastPoint ?: Offset(x, y)
        val current = Offset(x, y)
        val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)

        // Smooth segment with quadratic Bezier (control=previous, end=mid)
        _currentPath.value?.quadTo(previous.x, previous.y, mid.x, mid.y)

        val newPath = ComposePath().apply {
            activePath.value?.path?.let { existing -> addPath(existing) }
            quadraticBezierTo(previous.x, previous.y, mid.x, mid.y)
        }
        activePath.value = DrawingPath(path = newPath)
        lastPoint = current
    }

    fun endStroke() {
        _currentPath.value?.let { finished ->
            _renderedPaths.value = _renderedPaths.value + RenderedPath(path = finished, colorInt = currentPathColorInt)
        }
        _currentPath.value = null
        activePath.value = null
        lastPoint = null
    }

    // Unified motion handler called by UI
    fun handleMotionEvent(offset: androidx.compose.ui.geometry.Offset, action: MotionAction) {
        when (action) {
            is MotionAction.Start -> {
                val androidPath = AndroidPath().apply { moveTo(offset.x, offset.y) }
                _currentPath.value = androidPath

                val composePath = ComposePath().apply { moveTo(offset.x, offset.y) }
                activePath.value = DrawingPath(path = composePath)
                lastPoint = Offset(offset.x, offset.y)
            }
            is MotionAction.Move -> {
                val previous = lastPoint ?: Offset(offset.x, offset.y)
                val current = Offset(offset.x, offset.y)
                val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)

                _currentPath.value?.quadTo(previous.x, previous.y, mid.x, mid.y)

                val newPath = ComposePath().apply {
                    activePath.value?.path?.let { existing -> addPath(existing) }
                    quadraticBezierTo(previous.x, previous.y, mid.x, mid.y)
                }
                activePath.value = DrawingPath(path = newPath)
                lastPoint = current
            }
            is MotionAction.End -> {
                _currentPath.value?.let { finished ->
                    _renderedPaths.value = _renderedPaths.value + RenderedPath(path = finished, colorInt = currentPathColorInt)
                }
                _currentPath.value = null
                activePath.value = null
                lastPoint = null
            }
        }
    }

    // Called when the user finishes drawing and wants to classify
    fun onCaptureDrawing() {
        viewModelScope.launch {
            isClassifying.value = true
            // Clear in-progress stroke points
            activePath.value = null
            // Signal UI to capture the composable into a Bitmap
            _captureRequests.tryEmit(Unit)
        }
    }

    // Call after receiving a captured drawing bitmap from UI
    fun onBitmapCaptured(captured: Bitmap) {
        viewModelScope.launch {
            try {
                val (preprocessed, logits) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val targetSize = classifier.modelInputSpec().inputWidth
                    val prep = BitmapPreprocessor.preprocessBitmap(captured, targetSize = targetSize)
                    val out = classifier.classify(prep)
                    prep to out
                }
                _classifiedBitmap.value = preprocessed
                val result = classifier.processOutput(logits)
                _classificationResult.value = result
                updateStatus("${result.label} (${String.format("%.2f%%", result.confidence * 100f)})")
                if (showInitialTip.value) showInitialTip.value = false
                if (result.confidence < 0.60f) {
                    userMessages.tryEmit("Warning: Low Confidence. Please draw clearer.")
                }
                android.util.Log.d(
                    "Analytics",
                    "User in Variant ${if (isStrokeWidthVariantA.value) "A (Thick)" else "B (Medium)"} - Prediction: ${result.label}"
                )
            } catch (t: Throwable) {
                val msg = t.message ?: t.javaClass.simpleName
                updateStatus("Classification failed: $msg")
            } finally {
                isClassifying.value = false
            }
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
            paths = _renderedPaths.value.map { it.path },
            currentPath = _currentPath.value,
            strokeWidth = strokeWidth
        )
        onExported(bitmap)
    }

    fun getDrawingBounds(): RectF? {
        val allPaths = buildList {
            addAll(_renderedPaths.value.map { it.path })
            _currentPath.value?.let { add(it) }
        }
        if (allPaths.isEmpty()) return null

        var minLeft = Float.POSITIVE_INFINITY
        var minTop = Float.POSITIVE_INFINITY
        var maxRight = Float.NEGATIVE_INFINITY
        var maxBottom = Float.NEGATIVE_INFINITY

        val tmp = RectF()
        allPaths.forEach { p ->
            p.computeBounds(tmp, true)
            if (tmp.left < minLeft) minLeft = tmp.left
            if (tmp.top < minTop) minTop = tmp.top
            if (tmp.right > maxRight) maxRight = tmp.right
            if (tmp.bottom > maxBottom) maxBottom = tmp.bottom
        }

        if (!minLeft.isFinite() || !minTop.isFinite() || !maxRight.isFinite() || !maxBottom.isFinite()) return null
        return RectF(minLeft, minTop, maxRight, maxBottom)
    }
}