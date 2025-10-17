package com.example.smartsketchbook.domain.ml

enum class ModelSource { ASSET, DOWNLOADED }

data class AvailableModel(
    val id: String,
    val name: String,
    val fileName: String,
    val labels: List<String>,
    val source: ModelSource = ModelSource.ASSET,
    val version: Int,
    val downloadUrl: String? = null
)

object AvailableModels {
    val Digits = AvailableModel(
        id = "digits",
        name = "Handwritten Digits",
        fileName = "mnist.tflite",
        labels = com.example.smartsketchbook.domain.ml.ModelLabels.MNIST_LABELS,
        source = ModelSource.ASSET,
        version = 1
    )

    val Letters = AvailableModel(
        id = "letters",
        name = "Handwritten Letters",
        fileName = "letters_placeholder.tflite",
        labels = ('A'..'Z').map { it.toString() },
        source = ModelSource.ASSET,
        version = 2
    )

    val DigitsDownloaded = AvailableModel(
        id = "digits_downloaded",
        name = "Downloaded Digits (Local)",
        fileName = "mnist.tflite", // simulate downloading same MNIST
        labels = com.example.smartsketchbook.domain.ml.ModelLabels.MNIST_LABELS,
        source = ModelSource.DOWNLOADED,
        version = 1,
        downloadUrl = "https://example.com/models/mnist.tflite"
    )

    val All: List<AvailableModel> = listOf(Digits, Letters, DigitsDownloaded)
}


