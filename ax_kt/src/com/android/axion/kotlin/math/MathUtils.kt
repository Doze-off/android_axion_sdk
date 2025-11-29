package com.android.axion.kotlin.math

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Number.sldp: Dp
    @Composable get() = (this.toFloat() * LocalContext.current.scaleRatioLocked).dp

val Number.sdp: Dp
    @Composable get() = (this.toFloat() * LocalContext.current.scaleRatio).dp

fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

val Context.scaleRatioLocked: Float
    get() {
        val displayMetrics = resources.displayMetrics
        val sw = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
        val ratio = sw / 420f
        return ratio
    }

val Context.scaleRatio: Float
    get() {
        val displayMetrics = resources.displayMetrics
        val sw = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
        val ratio = if (sw > 620f) 1f else sw / 420f
        return ratio
    }
