package com.example.smartsketchbook.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the provided [content] Composable offscreen at the given size and returns a [Bitmap].
 *
 * This uses an offscreen ComposeView and View.drawToBitmap, which is a modern, stable way
 * to capture Compose content into an Android Bitmap. Ensure calls run on the main thread.
 */
suspend fun captureComposableToBitmap(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): Bitmap = withContext(Dispatchers.Main) {
    val root = FrameLayout(context)
    val composeView = ComposeView(context).apply {
        setContent(content)
    }
    root.addView(
        composeView,
        FrameLayout.LayoutParams(widthPx, heightPx)
    )

    val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    root.measure(widthSpec, heightSpec)
    root.layout(0, 0, widthPx, heightPx)

    composeView.drawToBitmap(Bitmap.Config.ARGB_8888)
}


