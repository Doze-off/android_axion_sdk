package com.android.axion.easteregg.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.axion.easteregg.data.StatsManager
import com.android.axion.easteregg.model.Star
import com.android.axion.easteregg.ui.screens.AtomCrashGame
import com.android.axion.easteregg.ui.screens.CombatPrepScreen
import com.android.axion.easteregg.ui.screens.SpaceGame
import com.android.axion.easteregg.ui.screens.SplashScreen
import kotlin.random.Random

@Composable
fun MainContainer() {
    var gameState by remember { mutableStateOf("SPLASH") }
    val context = LocalContext.current
    val statsManager = remember { StatsManager(context) }
    
    val layer1 = remember { List(25) { Star(Random.nextFloat(), Random.nextFloat(), 0.8f, 0.05f, 0.15f) } }
    val layer2 = remember { List(15) { Star(Random.nextFloat(), Random.nextFloat(), 1.2f, 0.12f, 0.35f) } }
    val layer3 = remember { List(6) { Star(Random.nextFloat(), Random.nextFloat(), 2.0f, 0.25f, 0.6f) } }
    
    val infiniteTransition = rememberInfiniteTransition(label="s")
    val starOffset by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = 1f, 
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "star_scroll"
    )
    
    Box(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
        Canvas(Modifier.fillMaxSize()) {
            val drawLayer = { stars: List<Star>, speedMult: Float ->
                stars.forEach { star ->
                    val currentY = (star.y + starOffset * star.speed * speedMult) % 1f
                    drawCircle(Color.White.copy(alpha = star.alpha), radius = star.size, center = Offset(star.x * size.width, currentY * size.height))
                }
            }
            drawLayer(layer1, 10f); drawLayer(layer2, 15f); drawLayer(layer3, 25f)
        }
        Crossfade(targetState = gameState, animationSpec = tween(1000), label = "state") { state ->
            when(state) {
                "SPLASH" -> SplashScreen(onFinish = { gameState = "ANIMATION" })
                "ANIMATION" -> AtomCrashGame(onFinish = { gameState = "PREP" })
                "PREP" -> CombatPrepScreen(onStart = { gameState = "GAME" })
                "GAME" -> SpaceGame(statsManager = statsManager, onExit = { gameState = "PREP" })
            }
        }
    }
}
