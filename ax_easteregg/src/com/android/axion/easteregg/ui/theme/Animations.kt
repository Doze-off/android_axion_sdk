package com.android.axion.easteregg.ui.theme

import androidx.compose.animation.core.Easing
import kotlin.math.pow

val ExpoOut = Easing { x -> if (x == 1f) 1f else 1f - 2.0.pow(-10.0 * x.toDouble()).toFloat() }
val BackEaseIn = Easing { x -> val s = 1.70158f; x * x * ((s + 1) * x - s) }
