package com.android.axion.kotlin.math

import android.content.Context

inline fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)

val Context.scaleRatio: Float
    get() {
        val displayMetrics = resources.displayMetrics
        val sw = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels) / displayMetrics.density
        return if (sw > 620f) 1f else sw / 420f
    }
