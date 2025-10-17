package com.example.smartsketchbook.ui.state

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ParcelPoint(
    val x: Float,
    val y: Float
) : Parcelable

@Parcelize
data class ParcelStroke(
    val points: List<ParcelPoint>,
    val colorInt: Int,
    val strokeWidthPx: Float
) : Parcelable


