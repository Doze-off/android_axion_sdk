package com.android.axion.easteregg.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CombatPrepScreen(onStart: () -> Unit) {
    var t1 by remember { mutableIntStateOf(0) }; var t2 by remember { mutableIntStateOf(0) }
    val txt1 = "SYNCING FLIGHT COURSE..."; val txt2 = "AXION COURSE PREPARED"
    val bias = remember { Animatable(0f) }; val sY = remember { Animatable(500f) }; val sA = remember { Animatable(0f) }
    val tScale by rememberInfiniteTransition(label="t").animateFloat(0.8f, 1.4f, infiniteRepeatable(tween(60), RepeatMode.Reverse), label = "t")

    LaunchedEffect(Unit) {
        while (t1 < txt1.length) { delay(80); t1++ }; delay(800); bias.animateTo(-0.4f, tween(1000, easing = FastOutSlowInEasing)); delay(600)
        while (t2 < txt2.length) { delay(60); t2++ }; delay(800); launch { sA.animateTo(1f, tween(1000)) }; sY.animateTo(0f, tween(1500, easing = LinearOutSlowInEasing))
        while(true) { sY.animateTo(-15f, tween(2000, easing = FastOutSlowInEasing)); sY.animateTo(0f, tween(2000, easing = FastOutSlowInEasing)) }
    }

    Box(Modifier.fillMaxSize().clickable(indication=null, interactionSource=remember{MutableInteractionSource()}){ if(t2>=txt2.length) onStart() }) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).align(BiasAlignment(0f, bias.value)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text = txt1.take(t1), style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Light, letterSpacing = 3.sp, color = Color.White, fontFamily = FontFamily.Monospace), textAlign = TextAlign.Center)
            if (bias.value < -0.1f) {
                Spacer(Modifier.height(24.dp)); Text(text = txt2.take(t2), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color.White, fontFamily = FontFamily.Monospace), textAlign = TextAlign.Center)
                if (t2 >= txt2.length) { Spacer(Modifier.height(16.dp)); Text(text = "[ SYSTEM_NOTE: DO NOT TAP 5 TIMES OVER \"AXION COMBAT\" ]", style = TextStyle(fontSize = 10.sp, color = Color.White.copy(0.25f), fontFamily = FontFamily.Monospace, letterSpacing = 1.sp), textAlign = TextAlign.Center) }
            }
        }
        Box(Modifier.align(Alignment.Center).offset(y = 220.dp + sY.value.dp).graphicsLayer { alpha = sA.value }.size(120.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val fP = Path().apply { moveTo(w * 0.45f, h * 0.8f); lineTo(w * 0.5f, h * (0.8f + 0.25f * tScale)); lineTo(w * 0.55f, h * 0.8f); close() }
                drawPath(fP, Color.White.copy(0.6f)); drawCircle(Color.White.copy(0.3f), radius = 12.dp.toPx() * tScale, center = Offset(w * 0.5f, h * 0.82f))
                val bP = Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.58f, h * 0.35f); lineTo(w * 0.85f, h * 0.65f); lineTo(w * 0.92f, h * 0.85f); lineTo(w * 0.72f, h * 0.85f); lineTo(w * 0.62f, h * 0.72f); lineTo(w * 0.5f, h * 0.8f); lineTo(w * 0.38f, h * 0.72f); lineTo(w * 0.28f, h * 0.85f); lineTo(w * 0.08f, h * 0.85f); lineTo(w * 0.15f, h * 0.65f); lineTo(w * 0.42f, h * 0.35f); close() }
                drawPath(bP, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(Color.White.copy(0.5f), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.5f), strokeWidth = 1.dp.toPx())
            }
        }
        if (t2 >= txt2.length) Text(text = "TAP TO INITIATE", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp), style = TextStyle(fontSize = 12.sp, color = Color.White.copy(0.4f), letterSpacing = 6.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace))
    }
}
