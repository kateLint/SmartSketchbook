package com.example.smartsketchbook.di

import com.example.smartsketchbook.domain.ml.ModelConfig
import com.example.smartsketchbook.domain.ml.AvailableModels
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
        // Default to Digits model; ViewModel will request swap at runtime
        val m = AvailableModels.Digits
        return ModelConfig(
            modelFileName = m.fileName,
            inputWidth = 28,
            inputHeight = 28,
            inputChannels = 1
        )
    }
}


