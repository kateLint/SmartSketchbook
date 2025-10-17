package com.example.smartsketchbook.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel

@Composable
fun ConfidenceBarDisplay(scores: FloatArray, topIndex: Int) {
    val viewModel: SketchbookViewModel = hiltViewModel()
    val currentModel = viewModel.currentModel.collectAsState().value
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val totalWidth = 1f
        for (i in scores.indices) {
            val dynamicLabel = currentModel.labels.getOrNull(i)
            val label = dynamicLabel ?: com.example.smartsketchbook.domain.ml.ModelLabels.MNIST_LABELS.getOrNull(i) ?: i.toString()
            val v = scores[i].coerceIn(0f, 1f)
            val barColor = if (i == topIndex) Color(0xFF2E7D32) else Color(0xFF90A4AE)
            val pct = String.format("%.2f%%", v * 100f)
            Text(text = "$label  $pct")
            Box(
                modifier = Modifier
                    .fillMaxWidth(v)
                    .height(8.dp)
                    .background(barColor)
            )
        }
    }
}


