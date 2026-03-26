package com.android.axion.easteregg.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    var currentWordIndex by remember { mutableIntStateOf(0) }
    val words = listOf("faster.", "more powerful.", "more reliable.", "more axion.")
    
    LaunchedEffect(Unit) {
        delay(1500); currentWordIndex = 1
        delay(1500); currentWordIndex = 2
        delay(1500); currentWordIndex = 3
        delay(2000); onFinish()
    }
    
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Axion", 
                    style = TextStyle(fontSize = 58.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                )
                Text(
                    text = "OS", 
                    style = TextStyle(fontSize = 58.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White, drawStyle = Stroke(width = 3f))
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
                Text("Make your android ", style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.White.copy(0.6f)))
                AnimatedContent(
                    targetState = currentWordIndex, 
                    transitionSpec = { (slideInVertically { h -> h } + fadeIn(tween(600))).togetherWith(slideOutVertically { h -> -h } + fadeOut(tween(600))) }, 
                    label = "words"
                ) { index ->
                    Text(
                        text = words[index], 
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = if (index == 3) Color.White else Color.White.copy(0.6f))
                    )
                }
            }
        }
    }
}
