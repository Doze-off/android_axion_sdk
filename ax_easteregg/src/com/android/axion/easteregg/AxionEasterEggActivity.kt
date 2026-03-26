package com.android.axion.easteregg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.android.axion.easteregg.ui.MainContainer

class AxionEasterEggActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        
        setContent {
            
            MaterialTheme(colorScheme = darkColorScheme(
                background = Color.Black,
                surface = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White
            )) {
                MainContainer()
            }
        }
    }
}
