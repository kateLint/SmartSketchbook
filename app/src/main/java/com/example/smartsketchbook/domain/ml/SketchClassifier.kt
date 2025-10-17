package com.example.smartsketchbook.domain.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import android.os.Build
import androidx.core.os.TraceCompat
import androidx.core.graphics.scale

/**
 * Loads a TensorFlow Lite model from assets and provides a minimal inference API.
 */
@Singleton
class SketchClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private var config: ModelConfig
) {
    data class InputSpec(val inputWidth: Int, val inputHeight: Int, val inputChannels: Int)
    fun modelInputSpec(): InputSpec = InputSpec(config.inputWidth, config.inputHeight, config.inputChannels)

    private var nnApiDelegate: Delegate? = null
    private var gpuDelegate: Delegate? = null
    private var interpreter: Interpreter = buildInterpreter()
    @Volatile private var delegateStatus: String = "Initializing"

    fun getDelegateStatus(): String = delegateStatus

    private fun buildInterpreter(cpuThreads: Int? = null): Interpreter {
        val options = Interpreter.Options()
        // Broad compatibility/perf options. Wrapped in try/catch to avoid API mismatch.
        try { options.setUseXNNPACK(true) } catch (_: Throwable) {}
        try { options.setAllowFp16PrecisionForFp32(true) } catch (_: Throwable) {}
        // Some TF Lite versions expose control-flow improvements
        try { options.javaClass.getMethod("setUseNewControlFlow", Boolean::class.javaPrimitiveType).invoke(options, true) } catch (_: Throwable) {}
        // NOTE: If model uses Select TF Ops, include the Select-TF-Ops runtime dependency and/or delegate.
        // TODO: If model uses Select TF Ops, need to include the TFLite-Select-TF-Ops dependency and potentially a corresponding delegate.
        // Skip delegates on emulators to avoid known GL/NNAPI issues
        val onEmulator = isProbablyEmulator()
        // Try NNAPI first on Android 9+ (API 28)
        var nnapiAdded = false
        if (!onEmulator && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val nn = NnApiDelegate()
                options.addDelegate(nn)
                nnApiDelegate = nn
                nnapiAdded = true
                Log.i("SketchClassifier", "NNAPI Delegate enabled.")
            } catch (e: Exception) {
                Log.e("SketchClassifier", "NNAPI Delegate failed, will try GPU.", e)
                try { nnApiDelegate?.javaClass?.getMethod("close")?.invoke(nnApiDelegate) } catch (_: Exception) {}
                nnApiDelegate = null
            }
        }
        // If NNAPI not added, try GPU delegate via reflection to avoid hard dependency crashes
        if (!nnapiAdded && !onEmulator) {
            try {
                val compatClazz = Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
                val compat = compatClazz.getConstructor().newInstance()
                val isSupported = compatClazz.getMethod("isDelegateSupportedOnThisDevice").invoke(compat) as Boolean
                if (isSupported) {
                    val gdClazz = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                    val delegate = gdClazz.getConstructor().newInstance() as Delegate
                    options.addDelegate(delegate)
                    gpuDelegate = delegate
                    Log.i("SketchClassifier", "GPU Delegate enabled.")
                    delegateStatus = "GPU Active"
                } else {
                    Log.i("SketchClassifier", "GPU Delegate not supported on this device. Using CPU.")
                }
            } catch (t: Throwable) {
                Log.e("SketchClassifier", "GPU Delegate unavailable, falling back to CPU.", t)
                gpuDelegate = null
            }
        }
        try {
            val interp = Interpreter(resolveModelBuffer(config.modelFileName), options)
            val inputDt = interp.getInputTensor(0).dataType()
            Log.i("SketchClassifier", "Input DataType: $inputDt, model=${config.modelFileName}")
            if (nnapiAdded) delegateStatus = "NNAPI Active"
            if (!nnapiAdded && gpuDelegate != null) delegateStatus = "GPU Active"
            // Warm-up: run a few dummy inferences to eliminate cold-start cost
            try { warmUp(interp) } catch (tw: Throwable) { Log.w("SketchClassifier", "Warm-up failed", tw) }
            return interp
        } catch (eFirst: Exception) {
            Log.e("SketchClassifier", "Failed to init interpreter with current delegates. Trying fallbacks...", eFirst)
            // Close any existing delegates
            try { nnApiDelegate?.javaClass?.getMethod("close")?.invoke(nnApiDelegate) } catch (_: Exception) {}
            try { gpuDelegate?.javaClass?.getMethod("close")?.invoke(gpuDelegate) } catch (_: Exception) {}
            nnApiDelegate = null
            gpuDelegate = null

            // Try GPU-only
            val gpuOptions = Interpreter.Options()
            try {
                val compatClazz = Class.forName("org.tensorflow.lite.gpu.CompatibilityList")
                val compat = compatClazz.getConstructor().newInstance()
                val isSupported = compatClazz.getMethod("isDelegateSupportedOnThisDevice").invoke(compat) as Boolean
                if (isSupported) {
                    val gdClazz = Class.forName("org.tensorflow.lite.gpu.GpuDelegate")
                    val delegate = gdClazz.getConstructor().newInstance() as Delegate
                    gpuOptions.addDelegate(delegate)
                    gpuDelegate = delegate
                    Log.i("SketchClassifier", "GPU fallback enabled.")
                }
            } catch (t: Throwable) {
                Log.e("SketchClassifier", "GPU fallback unavailable; will try CPU.", t)
                gpuDelegate = null
            }
            try {
            val interp = Interpreter(resolveModelBuffer(config.modelFileName), gpuOptions)
                val inputDt = interp.getInputTensor(0).dataType()
                Log.i("SketchClassifier", "Input DataType (GPU fallback): $inputDt")
                try { warmUp(interp) } catch (tw: Throwable) { Log.w("SketchClassifier", "Warm-up failed (GPU)", tw) }
                delegateStatus = "GPU Active"
                return interp
            } catch (eGpu: Exception) {
                Log.e("SketchClassifier", "GPU fallback failed; trying CPU.", eGpu)
                try { gpuDelegate?.javaClass?.getMethod("close")?.invoke(gpuDelegate) } catch (_: Exception) {}
                gpuDelegate = null
            }

            // CPU-only
            val cpuOptions = Interpreter.Options()
            val effectiveThreads = cpuThreads ?: defaultCpuThreads()
            cpuOptions.setNumThreads(effectiveThreads)
            try { cpuOptions.setUseXNNPACK(true) } catch (_: Throwable) {}
            Log.i("SketchClassifier", "Falling back to multi-threaded CPU ($effectiveThreads threads).")
            val interp = Interpreter(resolveModelBuffer(config.modelFileName), cpuOptions)
            val inputDt = interp.getInputTensor(0).dataType()
            Log.i("SketchClassifier", "Input DataType (CPU): $inputDt")
            try { warmUp(interp) } catch (tw: Throwable) { Log.w("SketchClassifier", "Warm-up failed (CPU)", tw) }
            delegateStatus = "CPU Multi-threaded Active ($effectiveThreads)"
            return interp
        }
    }

    // Pre-allocated reusable buffers and bitmaps
    private var targetSize: Int = config.inputWidth // assume square or use both dims
    private var reusableInputBitmap: Bitmap = Bitmap.createBitmap(config.inputWidth, config.inputHeight, Bitmap.Config.ARGB_8888)
    private var inputFloatBuffer: FloatBuffer? = null
    private var inputFloatByteBuffer: ByteBuffer? = null
    private var inputByteBuffer: ByteBuffer? = null
    private var outputArray: FloatArray? = null
    private var outputArray2D: Array<FloatArray>? = null
    // Toggle to simulate INT8 path during testing (set true to force int8 pipeline)
    private var forceSimulateInt8: Boolean = false

    /**
     * Run inference with a simple single-input/single-output signature.
     * Adjust types/shapes based on your model.
     */
    fun run(input: Any, output: Any) {
        interpreter.run(input, output)
    }

    fun close() {
        // Close delegates before interpreter
        try { nnApiDelegate?.javaClass?.getMethod("close")?.invoke(nnApiDelegate) } catch (_: Exception) {}
        try { gpuDelegate?.javaClass?.getMethod("close")?.invoke(gpuDelegate) } catch (_: Exception) {}
        interpreter.close()
    }

    // Placeholder for where custom ops would be registered from native code (via JNI/NDK)
    fun registerCustomOp(opName: String, nativeHandle: Long) {
        Log.i("SketchClassifier", "registerCustomOp requested for $opName (handle=$nativeHandle). This is a placeholder; real registration requires native code.")
    }

    @Synchronized
    fun loadModel(newConfig: ModelConfig, availableModel: AvailableModel) {
        // Cleanup
        try { nnApiDelegate?.javaClass?.getMethod("close")?.invoke(nnApiDelegate) } catch (_: Exception) {}
        try { gpuDelegate?.javaClass?.getMethod("close")?.invoke(gpuDelegate) } catch (_: Exception) {}
        try { interpreter.close() } catch (_: Exception) {}
        nnApiDelegate = null
        gpuDelegate = null

        // Update config and buffers
        config = newConfig
        targetSize = config.inputWidth
        reusableInputBitmap = Bitmap.createBitmap(config.inputWidth, config.inputHeight, Bitmap.Config.ARGB_8888)
        inputFloatBuffer = null
        inputFloatByteBuffer = null
        inputByteBuffer = null
        outputArray = null
        outputArray2D = null

        // Build interpreter with delegates + warm-up
        interpreter = buildInterpreter()
    }

    fun processOutput(outputArray: FloatArray): ClassificationResult {
        if (outputArray.isEmpty()) return ClassificationResult(label = "N/A", confidence = 0f, scores = outputArray, top3Indices = emptyList())
        val indices = outputArray.indices.toList()
        val top3 = indices.sortedByDescending { outputArray[it] }.take(3)
        val maxIndex = top3.first()
        val maxValue = outputArray[maxIndex]
        // Prefer dynamic labels when available via AvailableModel; fallback to static MNIST labels
        val dynamicLabels = try { AvailableModels.All.firstOrNull { it.fileName == config.modelFileName }?.labels } catch (_: Throwable) { null }
        val labelStr = (dynamicLabels?.getOrNull(maxIndex)) ?: ModelLabels.MNIST_LABELS.getOrNull(maxIndex) ?: maxIndex.toString()
        val label = "Predicted: $labelStr"
        return ClassificationResult(label = label, confidence = maxValue, scores = outputArray, top3Indices = top3)
    }

    private fun loadModelFileFromAssets(context: Context, modelPath: String): MappedByteBuffer {
        val afd = context.assets.openFd(modelPath)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val channel: FileChannel = fis.channel
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    private fun loadModelFileFromDisk(filePath: String): MappedByteBuffer {
        FileInputStream(filePath).use { fis ->
            val channel: FileChannel = fis.channel
            val size = channel.size()
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
        }
    }

    private fun resolveModelBuffer(modelFileNameOrPath: String): MappedByteBuffer {
        return if (modelFileNameOrPath.startsWith("/")) {
            loadModelFileFromDisk(modelFileNameOrPath)
        } else {
            loadModelFileFromAssets(context, modelFileNameOrPath)
        }
    }

    /**
     * Converts [bitmap] into the interpreter's expected input tensor format and runs inference.
     * Returns the first output tensor as a flattened FloatArray (normalized if quantized).
     */
    fun classify(bitmap: Bitmap): FloatArray {
        TraceCompat.beginSection("SketchClassifier:EndToEnd")
        try {
        // Infer input tensor shape and type
        val inputTensor = interpreter.getInputTensor(0)
        val inShape = inputTensor.shape() // e.g., [1, height, width, channels]
        var inType = inputTensor.dataType()
        if (forceSimulateInt8) inType = DataType.UINT8
        val inHeight = if (inShape.size >= 2) inShape[1] else config.inputHeight
        val inWidth = if (inShape.size >= 3) inShape[2] else config.inputWidth
        val inChannels = if (inShape.size >= 4) inShape[3] else config.inputChannels

        // Validate input shape against config and expected buffers
        if (inShape.size < 4 || inShape[0] != 1 || inWidth != config.inputWidth || inHeight != config.inputHeight || inChannels != config.inputChannels) {
            val shapeStr = inShape.joinToString(prefix = "[", postfix = "]")
            throw IllegalStateException("Model input shape $shapeStr does not match config (${config.inputWidth}x${config.inputHeight}x${config.inputChannels}, batch=1)")
        }

        // Prepare input bitmap at expected size using reusable target when matches classifier target
        val inputBitmap = if (inWidth == targetSize && inHeight == targetSize) {
            val fit = if (inChannels == 3) 0.85f else 0.75f
            BitmapPreprocessor.preprocessInto(bitmap, reusableInputBitmap, targetSize, centerByMass = true, fitFraction = fit)
            reusableInputBitmap
        } else if (bitmap.width != inWidth || bitmap.height != inHeight) {
            bitmap.scale(inWidth, inHeight)
        } else bitmap

        // Allocate input buffer; prefer FloatBuffer for FLOAT32 models
        val numInputElems = inWidth * inHeight * inChannels
        var inputFloatView: FloatBuffer? = null
        val inputBuffer: ByteBuffer = when (inType) {
            DataType.FLOAT32 -> {
                val byteBuf = if (inputFloatByteBuffer == null || inputFloatByteBuffer!!.capacity() != numInputElems * 4) {
                    inputFloatByteBuffer = ByteBuffer.allocateDirect(numInputElems * 4).order(ByteOrder.nativeOrder())
                    inputFloatByteBuffer!!
                } else {
                    inputFloatByteBuffer!!.rewind(); inputFloatByteBuffer!!
                }
                val floatView: FloatBuffer = byteBuf.asFloatBuffer()
                BitmapPreprocessor.bitmapToFloatBuffer(inputBitmap, channels = inChannels, dest = floatView)
                // Log first few normalized values for verification (should be in [0,1])
                floatView.rewind()
                val previewCount = minOf(5, floatView.remaining())
                val preview = FloatArray(previewCount)
                floatView.get(preview)
                val previewStr = preview.joinToString(prefix = "[", postfix = "]") { String.format("%.3f", it) }
                Log.d("SketchClassifier", "Input preview: $previewStr")
                // Reset position before interpreter read
                floatView.rewind()
                inputFloatView = floatView
                byteBuf.rewind()
                byteBuf
            }
            DataType.UINT8, DataType.INT8 -> {
                val byteBuf = if (inputByteBuffer == null || inputByteBuffer!!.capacity() != numInputElems) {
                    inputByteBuffer = ByteBuffer.allocateDirect(numInputElems).order(ByteOrder.nativeOrder())
                    inputByteBuffer!!
                } else {
                    inputByteBuffer!!.rewind(); inputByteBuffer!!
                }
                val pixels = IntArray(inWidth * inHeight)
                inputBitmap.getPixels(pixels, 0, inWidth, 0, 0, inWidth, inHeight)
                var idx = 0
                for (y in 0 until inHeight) {
                    for (x in 0 until inWidth) {
                        val c = pixels[idx++]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        if (inChannels == 1) {
                            val gray = (0.299f * r + 0.587f * g + 0.114f * b)
                            // Keep polarity consistent with float path (white digit on black): intensity = 255 - gray
                            val v = (255f - gray).toInt().coerceIn(0, 255)
                            byteBuf.put(v.toByte())
                        } else if (inChannels == 3) {
                            byteBuf.put(r.toByte())
                            byteBuf.put(g.toByte())
                            byteBuf.put(b.toByte())
                        } else {
                            throw IllegalArgumentException("Unsupported channel count: $inChannels")
                        }
                    }
                }
                byteBuf.rewind()
                byteBuf
            }
            else -> throw IllegalArgumentException("Unsupported input type: $inType")
        }

        // Prepare output handling based on first output tensor (FloatArray preferred)
        val outputTensor = interpreter.getOutputTensor(0)
        val outType = outputTensor.dataType()
        val outShape = outputTensor.shape() // e.g., [1, numClasses]
        val outTotal = outShape.fold(1) { acc, v -> acc * v }

        // Simple path: 1xN FLOAT32 => FloatArray(N) with interpreter.run(input, outputArray)
        if (outType == DataType.FLOAT32 && outShape.isNotEmpty()) {
            val rank = outShape.size
            val numClasses = outShape.last()
            if (numClasses > 0) {
                if (rank == 2 && outShape[0] == 1) {
                    // Model outputs [1, numClasses] → use 2D array and then return row 0
                    val needAlloc = outputArray2D == null || outputArray2D!!.size != 1 || outputArray2D!![0].size != numClasses
                    if (needAlloc) {
                        outputArray2D = Array(1) { FloatArray(numClasses) }
                    }
                    val out2D = outputArray2D!!
                    TraceCompat.beginSection("TFLite:InferenceOnly")
                    val t0 = System.nanoTime()
                    interpreter.run(inputBuffer, out2D)
                    val dtMs = (System.nanoTime() - t0) / 1_000_000.0
                    TraceCompat.endSection()
                    Log.d("SketchClassifier", "InferenceOnly: ${dtMs}ms")
                    return out2D[0]
                } else if (rank == 1) {
                    if (outputArray == null || outputArray!!.size != numClasses) {
                        outputArray = FloatArray(numClasses)
                    }
                    val out = outputArray!!
                    TraceCompat.beginSection("TFLite:InferenceOnly")
                    val t0 = System.nanoTime()
                    interpreter.run(inputBuffer, out)
                    val dtMs = (System.nanoTime() - t0) / 1_000_000.0
                    TraceCompat.endSection()
                    Log.d("SketchClassifier", "InferenceOnly: ${dtMs}ms")
                    return out
                }
            }
        }

        // Fallback: generic ByteBuffer output processing
        var outElems = 1
        for (dim in outShape) outElems *= dim
        val outBytesPerElem = when (outType) {
            DataType.FLOAT32 -> 4
            DataType.UINT8 -> 1
            else -> throw IllegalArgumentException("Unsupported output type: $outType")
        }
        val outputBuffer = ByteBuffer.allocateDirect(outElems * outBytesPerElem).order(ByteOrder.nativeOrder())
        try {
            TraceCompat.beginSection("TFLite:InferenceOnly")
            val t0 = System.nanoTime()
            interpreter.run(inputBuffer, outputBuffer)
            val dtMs = (System.nanoTime() - t0) / 1_000_000.0
            TraceCompat.endSection()
            Log.d("SketchClassifier", "InferenceOnly: ${dtMs}ms")
        } catch (e: Exception) {
            Log.e("SketchClassifier", "Interpreter.run failed with outShape=${outShape.joinToString()}", e)
            throw e
        }
        outputBuffer.rewind()
        val result = FloatArray(outElems)
        when (outType) {
            DataType.FLOAT32 -> {
                for (i in 0 until outElems) result[i] = outputBuffer.float
            }
            DataType.UINT8 -> {
                for (i in 0 until outElems) result[i] = (outputBuffer.get().toInt() and 0xFF) / 255.0f
            }
            else -> Unit
        }
        return result
        } finally {
            TraceCompat.endSection()
        }
    }

    private companion object {}

    fun defaultCpuThreads(maxCap: Int = 8): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return maxOf(1, minOf(maxCap, cores))
    }

    @Synchronized
    fun reinitializeForCpuThreads(threads: Int) {
        try { nnApiDelegate?.javaClass?.getMethod("close")?.invoke(nnApiDelegate) } catch (_: Exception) {}
        try { gpuDelegate?.javaClass?.getMethod("close")?.invoke(gpuDelegate) } catch (_: Exception) {}
        nnApiDelegate = null
        gpuDelegate = null
        try { interpreter.close() } catch (_: Exception) {}
        interpreter = run {
            // Build CPU-only interpreter with user threads
            val opts = Interpreter.Options()
            val t = maxOf(1, threads)
            opts.setNumThreads(t)
            try { opts.setUseXNNPACK(true) } catch (_: Throwable) {}
            Log.i("SketchClassifier", "Reinit interpreter with CPU threads=$t")
            val interp = Interpreter(resolveModelBuffer(config.modelFileName), opts)
            try { warmUp(interp) } catch (_: Throwable) {}
            interp
        }
    }

    private fun warmUp(interp: Interpreter) {
        val inTensor = interp.getInputTensor(0)
        val inShape = inTensor.shape()
        val inType = inTensor.dataType()
        val batch = inShape[0]
        val h = inShape[1]
        val w = inShape[2]
        val c = inShape[3]
        val inElems = batch * h * w * c
        val input: Any = when (inType) {
            DataType.FLOAT32 -> ByteBuffer.allocateDirect(inElems * 4).order(ByteOrder.nativeOrder())
            DataType.UINT8, DataType.INT8 -> ByteBuffer.allocateDirect(inElems).order(ByteOrder.nativeOrder())
            else -> ByteBuffer.allocateDirect(inElems * 4).order(ByteOrder.nativeOrder())
        }
        val outTensor = interp.getOutputTensor(0)
        val outShape = outTensor.shape()
        val n = outShape.last()
        val outAny: Any = if (outShape.size == 2 && outShape[0] == 1) Array(1) { FloatArray(n) } else FloatArray(n)
        repeat(3) {
            try {
                interp.run(input, outAny)
            } catch (_: Throwable) {}
        }
        Log.i("SketchClassifier", "Warm-up complete")
    }

    private fun isProbablyEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.lowercase().contains("vbox")
                || Build.FINGERPRINT.lowercase().contains("test-keys")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86"))
    }
}


