package com.android.axion.easteregg.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.android.axion.easteregg.ui.components.drawElectronOnOrbit
import com.android.axion.easteregg.ui.components.drawOvalOrbit
import com.android.axion.easteregg.ui.theme.BackEaseIn
import com.android.axion.easteregg.ui.theme.ExpoOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AtomCrashGame(onFinish: () -> Unit) {
    val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    var rA by remember { mutableIntStateOf(5) }; var isE by remember { mutableStateOf(false) }
    val cP = remember { Animatable(0f) }; val eP = remember { Animatable(0f) }; val fA = remember { Animatable(0f) }; val nS = remember { Animatable(0f) }; val nSc = remember { Animatable(1f) }
    val sGR by rememberInfiniteTransition(label="r").animateFloat(0f, 360f, infiniteRepeatable(tween(25000, easing = LinearEasing)), label="sys_rot")
    val cP_ by rememberInfiniteTransition(label="p").animateFloat(0.8f, 1.4f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label="pulse")
    val rot by rememberInfiniteTransition(label="o").animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label="elec_rot")
    Box(Modifier.fillMaxSize().clickable(indication=null, interactionSource=remember{MutableInteractionSource()}) { 
        if (rA > 0 && !cP.isRunning && !isE) {
            scope.launch {
                launch { haptic.performHapticFeedback(HapticFeedbackType.LongPress); nSc.animateTo(1.25f, tween(60)); nSc.animateTo(1f, spring()) }
                launch { repeat(3) { nS.animateTo(8f, tween(30)); nS.animateTo(-8f, tween(30)) }; nS.animateTo(0f, tween(30)) }
                cP.animateTo(1f, tween(250, easing = BackEaseIn)); rA--
                if (rA == 0) { isE = true; launch { fA.animateTo(0.5f, tween(40)); fA.animateTo(0f, tween(1200)) }; eP.animateTo(1f, tween(600, easing = ExpoOut)); delay(650); onFinish() }
                cP.snapTo(0f)
            }
        }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(450.dp).graphicsLayer { translationX = nS.value }) {
            val cX = size.width / 2; val cY = size.height / 2
            if (!isE || eP.value < 1f) {
                val a = 1f - eP.value
                rotate(sGR, Offset(cX, cY)) {
                    if (a > 0f) for (i in 0..2) drawOvalOrbit(cX, cY, size.minDimension / 2.8f, size.minDimension / 2.8f * 0.45f, i * 60f, Color.White.copy(0.15f * a), 1.5f.dp.toPx())
                    val nR = 26.dp.toPx() * nSc.value + (if (isE) eP.value * 380.dp.toPx() else 0f)
                    if (a > 0f) {
                        drawCircle(Color.White.copy(0.1f * a), radius = nR * 1.5f * cP_, center = Offset(cX, cY))
                        drawCircle(Color.White.copy(a), radius = nR, center = Offset(cX, cY))
                        drawCircle(Color.Black.copy(a), radius = nR * 0.88f, center = Offset(cX, cY))
                        drawCircle(Color.White.copy(0.8f * a), radius = nR * 0.35f, center = Offset(cX, cY))
                    }
                    if (!isE) {
                        val ts = listOf(0f, 60f, 120f, 0f, 60f); val as_ = listOf(0f, 72f, 144f, 216f, 288f)
                        for (i in 0 until rA) drawElectronOnOrbit(cX, cY, size.minDimension / 2.8f, size.minDimension / 2.8f * 0.45f, ts[i], rot + as_[i], 8.dp.toPx(), Color.White, if (i == rA - 1 && cP.value > 0f) cP.value else 0f)
                    }
                }
                if (isE) drawCircle(Color.White.copy(0.4f * a), radius = eP.value * size.maxDimension, center = Offset(cX, cY), style = Stroke(1.dp.toPx()))
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(fA.value)))
    }
}
