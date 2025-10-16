package com.example.smartsketchbook.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartsketchbook.ui.components.DrawingCanvas
import com.example.smartsketchbook.ui.state.SketchbookUiState
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel

@Composable
fun SketchbookRoute(
    modifier: Modifier = Modifier,
    viewModel: SketchbookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SketchbookScreen(state = uiState, modifier = modifier)
}

@Composable
fun SketchbookScreen(
    state: SketchbookUiState,
    modifier: Modifier = Modifier
) {
    val viewModel: SketchbookViewModel = hiltViewModel()
    Column(modifier = modifier.padding(16.dp)) {
        Row {
            Button(onClick = { viewModel.clearCanvas() }) { Text("Clear") }
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "Sketchbook Status: ${state.statusMessage}")
        }
        DrawingCanvas(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(420.dp),
            viewModel = viewModel,
            onDrawFinished = { bitmap: Bitmap ->
                viewModel.updateStatus(
                    "Captured ${bitmap.width}x${bitmap.height}"
                )
            }
        )
    }
}