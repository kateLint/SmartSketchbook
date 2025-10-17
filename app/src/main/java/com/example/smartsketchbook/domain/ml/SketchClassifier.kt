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

/**
 * Loads a TensorFlow Lite model from assets and provides a minimal inference API.
 */
@Singleton
class SketchClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: ModelConfig
) {

    private val interpreter: Interpreter by lazy {
        val options = Interpreter.Options().apply {
            // Tune as needed
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            // XNNPACK/NNAPI may be enabled by default depending on TF Lite version
        }
        try {
            val interp = Interpreter(loadModelFile(context, config.modelFileName), options)
            // Log discovered input data type for Day 17
            val inputDt = interp.getInputTensor(0).dataType()
            Log.i("SketchClassifier", "Input DataType: $inputDt, model=${config.modelFileName}")
            interp
        } catch (e: Exception) {
            Log.e("SketchClassifier", "Failed to load TFLite model: ${config.modelFileName}", e)
            throw e
        }
    }

    // Pre-allocated reusable buffers and bitmaps
    private val targetSize: Int = config.inputWidth // assume square or use both dims
    private val reusableInputBitmap: Bitmap = Bitmap.createBitmap(config.inputWidth, config.inputHeight, Bitmap.Config.ARGB_8888)
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
        interpreter.close()
    }

    fun processOutput(outputArray: FloatArray): ClassificationResult {
        if (outputArray.isEmpty()) return ClassificationResult(label = "N/A", confidence = 0f)
        val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: 0
        val maxValue = outputArray[maxIndex]
        val labelStr = ModelLabels.MNIST_LABELS.getOrNull(maxIndex) ?: maxIndex.toString()
        val label = "Predicted: $labelStr"
        return ClassificationResult(label = label, confidence = maxValue)
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        FileInputStream(assetFileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel: FileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    /**
     * Converts [bitmap] into the interpreter's expected input tensor format and runs inference.
     * Returns the first output tensor as a flattened FloatArray (normalized if quantized).
     */
    fun classify(bitmap: Bitmap): FloatArray {
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
            BitmapPreprocessor.preprocessInto(bitmap, reusableInputBitmap, targetSize)
            reusableInputBitmap
        } else if (bitmap.width != inWidth || bitmap.height != inHeight) {
            Bitmap.createScaledBitmap(bitmap, inWidth, inHeight, true)
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
                    interpreter.run(inputBuffer, out2D)
                    return out2D[0]
                } else if (rank == 1) {
                    if (outputArray == null || outputArray!!.size != numClasses) {
                        outputArray = FloatArray(numClasses)
                    }
                    val out = outputArray!!
                    interpreter.run(inputBuffer, out)
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
            interpreter.run(inputBuffer, outputBuffer)
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
    }

    private companion object {}
}


