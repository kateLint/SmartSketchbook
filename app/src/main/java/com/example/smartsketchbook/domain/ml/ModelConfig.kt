package com.example.smartsketchbook.domain.ml

data class ModelConfig(
    val modelFileName: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int
)


