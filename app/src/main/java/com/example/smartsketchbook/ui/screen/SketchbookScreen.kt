package com.example.smartsketchbook.ui.screen

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel

@Composable
fun SketchbookRoute(
    viewModel: SketchbookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SketchbookScreen(state = uiState)
}

@Composable
fun SketchbookScreen(
    state: SketchbookUiState
) {
    Text(text = "Sketchbook Status: ${state.statusMessage}")
}