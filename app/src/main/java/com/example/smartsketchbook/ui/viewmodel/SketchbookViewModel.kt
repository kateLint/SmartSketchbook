package com.example.smartsketchbook.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.smartsketchbook.ui.state.SketchbookUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * ViewModel for the sketchbook screen, responsible for UI-related data and state.
 *
 * The @HiltViewModel annotation is used by Hilt to generate the necessary factory
 * and allow the injection of dependencies (if any) via the @Inject constructor.
 */
@HiltViewModel
class SketchbookViewModel @Inject constructor() : ViewModel() {

    // Holds all UI-related state for the Sketchbook screen.
    private val _uiState: MutableStateFlow<SketchbookUiState> = MutableStateFlow(SketchbookUiState())

    // Read-only state exposed to the UI.
    val uiState: StateFlow<SketchbookUiState> = _uiState

    // Example function to update the status (for demonstration, though not strictly required by the prompt)
    fun updateStatus(newStatus: String) {
        _uiState.value = _uiState.value.copy(statusMessage = newStatus)
    }
}