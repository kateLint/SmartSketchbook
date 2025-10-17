package com.example.smartsketchbook.domain.ml

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val scores: FloatArray,
    val top3Indices: List<Int>
)


