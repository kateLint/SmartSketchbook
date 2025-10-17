package com.example.smartsketchbook.ui.state

import androidx.compose.ui.geometry.Offset

/**
 * Represents a stroke as an ordered list of points.
 */
data class DrawingPath(
    val points: List<Offset> = emptyList()
)


