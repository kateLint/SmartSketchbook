package com.example.smartsketchbook.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.smartsketchbook.ui.viewmodel.SketchbookViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: SketchbookViewModel = hiltViewModel()) {
    val cpuThreads by viewModel.cpuThreadCount.collectAsState()
    val delegateStatus by viewModel.hardwareDelegateStatus.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings and Optimization") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painter = painterResource(android.R.drawable.ic_media_previous), contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier.padding(inner).padding(16.dp)) {
            Text(text = "Performance Status")
            Text(text = delegateStatus)
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "CPU Threads: $cpuThreads")
            Slider(
                value = (cpuThreads - 1) / 7f,
                onValueChange = {
                    val threads = 1 + (it * 7f).toInt().coerceIn(0, 7)
                    viewModel.setCpuThreads(threads)
                },
                colors = SliderDefaults.colors()
            )
            when (cpuThreads) {
                1 -> Text("Note: Using 1 thread is the most power efficient, but the slowest.")
                in 4..8 -> Text("Warning: High thread counts can increase battery drain.")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Drawing Preferences")
            // Placeholder for color selection UI (move your existing color buttons here)
            // e.g., show sample swatches and call viewModel.setDrawingColor(color)
        }
    }
}


