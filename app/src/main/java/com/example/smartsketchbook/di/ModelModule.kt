package com.example.smartsketchbook.di

import com.example.smartsketchbook.domain.ml.ModelConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ModelModule {

    @Provides
    @Singleton
    fun provideModelConfig(): ModelConfig {
        // MNIST-style grayscale config; update as needed when swapping models
        return ModelConfig(
            modelFileName = "mnist.tflite",
            inputWidth = 28,
            inputHeight = 28,
            inputChannels = 1
        )
    }
}


