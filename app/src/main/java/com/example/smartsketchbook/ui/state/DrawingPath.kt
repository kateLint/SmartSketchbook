package com.example.smartsketchbook.ui.state

import androidx.compose.ui.graphics.Path

/**
 * Represents a stroke as a Compose Path to avoid rebuilding per frame.
 */
data class DrawingPath(
    val path: Path = Path()
)


