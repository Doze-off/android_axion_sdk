package com.android.axion.easteregg.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class Star(val x: Float, val y: Float, val size: Float, val speed: Float, val alpha: Float)
data class Bullet(val position: Offset, val isEnemy: Boolean = false, val velocityX: Float = 0f)
data class Particle(val pos: Offset, val vel: Offset, val color: Color, val life: Float, val size: Float = 4f)
data class Shockwave(val radius: Float, val alpha: Float, val pos: Offset)
data class DamageText(val text: String, val pos: Offset, val life: Float) 
data class WarpLine(val x: Float, val y: Float, val speed: Float, val length: Float) 
data class GameStat(val timestamp: Long, val result: String, val time: String, val accuracy: Int)
