package com.example.smartsketchbook.domain.ml

data class AvailableModel(
    val id: String,
    val name: String,
    val fileName: String,
    val labels: List<String>
)

object AvailableModels {
    val Digits = AvailableModel(
        id = "digits",
        name = "Handwritten Digits",
        fileName = "mnist.tflite",
        labels = com.example.smartsketchbook.domain.ml.ModelLabels.MNIST_LABELS
    )

    val Letters = AvailableModel(
        id = "letters",
        name = "Handwritten Letters",
        fileName = "letters_placeholder.tflite",
        labels = ('A'..'Z').map { it.toString() }
    )

    val All: List<AvailableModel> = listOf(Digits, Letters)
}


