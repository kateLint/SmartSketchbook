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
    @ApplicationContext private val context: Context
) {

    private val interpreter: Interpreter by lazy {
        val options = Interpreter.Options().apply {
            // Tune as needed
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            // XNNPACK/NNAPI may be enabled by default depending on TF Lite version
        }
        Interpreter(loadModelFile(context, MODEL_ASSET_PATH), options)
    }

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
        val label = "Predicted: $maxIndex"
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
        val inType = inputTensor.dataType()
        val inHeight = if (inShape.size >= 2) inShape[1] else bitmap.height
        val inWidth = if (inShape.size >= 3) inShape[2] else bitmap.width
        val inChannels = if (inShape.size >= 4) inShape[3] else 1

        // Prepare input bitmap at expected size
        val inputBitmap = if (bitmap.width != inWidth || bitmap.height != inHeight) {
            Bitmap.createScaledBitmap(bitmap, inWidth, inHeight, true)
        } else bitmap

        // Allocate input buffer; prefer FloatBuffer for FLOAT32 models
        val numInputElems = inWidth * inHeight * inChannels
        var inputFloatView: FloatBuffer? = null
        val inputBuffer: ByteBuffer = when (inType) {
            DataType.FLOAT32 -> {
                val byteBuf = ByteBuffer.allocateDirect(numInputElems * 4).order(ByteOrder.nativeOrder())
                val floatView: FloatBuffer = byteBuf.asFloatBuffer()
                val normalized = BitmapPreprocessor.bitmapToFloatArray(inputBitmap, channels = inChannels)
                floatView.put(normalized)
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
            DataType.UINT8 -> {
                val byteBuf = ByteBuffer.allocateDirect(numInputElems).order(ByteOrder.nativeOrder())
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
                            byteBuf.put(gray.toInt().toByte())
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

        // Simple path: 1xN FLOAT32 => FloatArray(N) with interpreter.run(input, outputArray)
        if (outType == DataType.FLOAT32 && outShape.isNotEmpty()) {
            val numClasses = outShape.last()
            if (numClasses > 0) {
                val outputArray = FloatArray(numClasses)
                interpreter.run(inputBuffer, outputArray)
                return outputArray
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
        interpreter.run(inputBuffer, outputBuffer)
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

    private companion object {
        const val MODEL_ASSET_PATH: String = "model.tflite"
    }
}


