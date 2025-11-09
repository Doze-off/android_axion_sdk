package com.android.axion.compose.math

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Number.sdp: Dp
    @Composable get() = (this.toFloat() * LocalContext.current.scaleRatio).dp
