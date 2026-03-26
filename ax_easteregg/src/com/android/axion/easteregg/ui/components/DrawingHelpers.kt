package com.android.axion.easteregg.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

fun DrawScope.drawOvalOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, color: Color, stroke: Float) {
    rotate(degrees = tilt, pivot = Offset(cX, cY)) { 
        drawOval(color, Offset(cX - rX, cY - rY), Size(rX * 2, rY * 2), style = Stroke(stroke)) 
    }
}

fun DrawScope.drawElectronOnOrbit(cX: Float, cY: Float, rX: Float, rY: Float, tilt: Float, angle: Float, radius: Float, color: Color, crashProgress: Float) {
    val rad = Math.toRadians(angle.toDouble()); val tiltRad = Math.toRadians(tilt.toDouble())
    val x = rX * cos(rad).toFloat(); val y = rY * sin(rad).toFloat()
    val rX_ = x * cos(tiltRad).toFloat() - y * sin(tiltRad).toFloat(); val rY_ = x * sin(tiltRad).toFloat() + y * cos(tiltRad).toFloat()
    val eX = cX + (rX_ * (1f - crashProgress)); val eY = cY + (rY_ * (1f - crashProgress))
    drawCircle(Color.White.copy(0.2f), radius * 2.5f, Offset(eX, eY)); drawCircle(color, radius, Offset(eX, eY))
}
