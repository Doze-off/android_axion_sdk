package com.android.axion.easteregg.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.android.axion.easteregg.data.StatsManager
import com.android.axion.easteregg.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*
import kotlin.random.Random

@Composable
fun SpaceGame(statsManager: StatsManager, onExit: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    
    
    val fireCommands = remember { ConcurrentLinkedQueue<Offset>() }
    
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sw = constraints.maxWidth.toFloat()
        val sh = constraints.maxHeight.toFloat()
        
        var shipPos by remember { mutableStateOf(Offset(sw / 2, sh * 0.85f)) }
        var shipLean by remember { mutableFloatStateOf(0f) }
        var bossPos by remember { mutableStateOf(Offset(sw / 2, sh * 0.18f)) }
        var bossPhase by remember { mutableFloatStateOf(0f) } 
        var bossHealth by remember { mutableFloatStateOf(2026f) }
        var bossHitFlash by remember { mutableFloatStateOf(0f) } 
        
        
        var gamePhase by remember { mutableStateOf("PLAYING") } 
        var gameResult by remember { mutableStateOf("PENDING") } 
        
        var gameTime by remember { mutableLongStateOf(0L) }
        var endSequenceTime by remember { mutableLongStateOf(0L) }
        
        
        var bullets by remember { mutableStateOf(listOf<Bullet>()) }
        var particles by remember { mutableStateOf(listOf<Particle>()) }
        var shockwaves by remember { mutableStateOf(listOf<Shockwave>()) }
        var damageTexts by remember { mutableStateOf(listOf<DamageText>()) } 
        var warpLines by remember { mutableStateOf(List(15) { WarpLine(Random.nextFloat()*sw, Random.nextFloat()*sh, Random.nextFloat()*10f+5f, Random.nextFloat()*40f+20f) }) }
        
        var secretTaps by remember { mutableIntStateOf(0) }
        var ignoreTaps by remember { mutableStateOf(true) }
        var overdriveMode by remember { mutableStateOf(false) }
        
        var currentScreenShake by remember { mutableFloatStateOf(0f) }
        var shipAlpha by remember { mutableFloatStateOf(1f) }
        var bossAlpha by remember { mutableFloatStateOf(1f) }

        val tS by rememberInfiniteTransition(label="t").animateFloat(0.8f, 1.4f, infiniteRepeatable(tween(60), RepeatMode.Reverse), label = "t")
        var bulletsFired by remember { mutableIntStateOf(0) }
        var hitsConfirmed by remember { mutableIntStateOf(0) }
        val accuracy = derivedStateOf { if (bulletsFired > 0) ((hitsConfirmed.toFloat() / bulletsFired) * 100).toInt() else 0 }
        val animatedHealth by animateFloatAsState(targetValue = bossHealth / 2026f, animationSpec = spring(stiffness = Spring.StiffnessLow), label = "health")
        
        val liveTimeStr = remember(gameTime) {
            val totalMs = gameTime
            val m = (totalMs / 60000).toInt()
            val s = (totalMs / 1000 % 60).toInt()
            val c = (totalMs % 1000 / 10).toInt()
            "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}:${c.toString().padStart(2, '0')}"
        }

        
        LaunchedEffect(Unit) {
            delay(50)
            ignoreTaps = false
            var lastFrameTime = -1L
            while(true) {
                withFrameMillis { frameTime ->
                    if (lastFrameTime == -1L) lastFrameTime = frameTime
                    val deltaMs = frameTime - lastFrameTime
                    lastFrameTime = frameTime
                    
                    val newParticles = particles.toMutableList()
                    val newShockwaves = shockwaves.toMutableList()
                    val newDamageTexts = damageTexts.toMutableList()
                    val newBullets = bullets.toMutableList()

                    if (gamePhase == "PLAYING") {
                        gameTime += deltaMs
                        if (bossHitFlash > 0f) bossHitFlash = max(0f, bossHitFlash - 0.1f)
                        
                        
                        while (fireCommands.isNotEmpty()) {
                            val pos = fireCommands.poll()
                            if (pos != null) {
                                newBullets.add(Bullet(pos + Offset(0f, Random.nextFloat() * 20f)))
                                bulletsFired++
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        
                        val speedMult = 1.2f + (1f - (bossHealth / 2026f)) * 1.8f
                        bossPhase += (deltaMs / 1000f) * speedMult
                        bossPos = Offset((sw/2) + sin(bossPhase) * (sw*0.35f), (sh*0.18f) + sin(bossPhase*2.1f) * 15f)
                        
                        
                        warpLines = warpLines.map { w ->
                            val newY = w.y + w.speed
                            if (newY > sh + w.length) {
                                w.copy(y = -w.length, x = Random.nextFloat() * sw)
                            } else {
                                w.copy(y = newY)
                            }
                        }
                        
                        if (gameTime % 2L == 0L) {
                            newParticles.add(Particle(shipPos + Offset(Random.nextFloat()*10f-5f, 40f), Offset(0f, Random.nextFloat()*5f+5f), Color.White.copy(0.6f), 1f, Random.nextFloat()*3f+1f))
                        }

                        
                        val fireDelay = when { bossHealth > 1500f -> 2000L; bossHealth > 1000f -> 1700L; bossHealth > 500f -> 1400L; else -> 1100L }
                        if (gameTime % fireDelay < 20L) {
                            newBullets.add(Bullet(bossPos + Offset(0f, 100f), true))
                            if(bossHealth <= 1500f) newBullets.add(Bullet(bossPos + Offset(-20f, 100f), true, -3f))
                            if(bossHealth <= 1000f) newBullets.add(Bullet(bossPos + Offset(30f, 100f), true, 5f))
                        }
                        
                        
                        val bulletsToKeep = mutableListOf<Bullet>()
                        newBullets.forEach { b ->
                            val updatedB = if (b.isEnemy) {
                                b.copy(position = Offset(b.position.x + b.velocityX, b.position.y + 7.5f))
                            } else {
                                b.copy(position = Offset(b.position.x, b.position.y - 48f))
                            }

                            var shouldRemove = false
                            if (updatedB.isEnemy) {
                                if ((updatedB.position - shipPos).getDistance() < 55f) { 
                                    gameResult = "LOST"
                                    gamePhase = "END_SEQUENCE"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } else {
                                if ((updatedB.position - bossPos).getDistance() < 130f) {
                                    val dmg = if (overdriveMode) 100f else 25f
                                    bossHealth = (bossHealth - dmg).coerceAtLeast(0f)
                                    hitsConfirmed++
                                    bossHitFlash = 1f 
                                    newShockwaves.add(Shockwave(60f, 0.8f, updatedB.position))
                                    newDamageTexts.add(DamageText("-${dmg.toInt()}", bossPos + Offset(Random.nextFloat() * 80 - 40, Random.nextFloat() * 40 - 60), 1f))
                                    shouldRemove = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    repeat(4) { newParticles.add(Particle(updatedB.position, Offset(Random.nextFloat()*20-10, Random.nextFloat()*20-10), Color.White, 1f)) }
                                    if (bossHealth <= 0) { 
                                        bossHealth = 0f
                                        gameResult = "WON"
                                        gamePhase = "END_SEQUENCE"
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                            
                            if (!shouldRemove && updatedB.position.y > -150f && updatedB.position.y < sh + 150f) {
                                bulletsToKeep.add(updatedB)
                            }
                        }
                        newBullets.clear()
                        newBullets.addAll(bulletsToKeep)
                        
                        if ((shipPos - bossPos).getDistance() < 160f) { 
                            gameResult = "LOST"
                            gamePhase = "END_SEQUENCE"
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }

                    } else if (gamePhase == "END_SEQUENCE") {
                        endSequenceTime += deltaMs
                        
                        if (gameResult == "WON") {
                            currentScreenShake = (Random.nextFloat() - 0.5f) * 60f
                            bossAlpha = (1f - (endSequenceTime / 1000f)).coerceAtLeast(0f)
                            if (endSequenceTime % 60 < 20) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                newShockwaves.add(Shockwave(0f, 1f, bossPos + Offset(Random.nextFloat()*180-90, Random.nextFloat()*180-90)))
                                repeat(5) { newParticles.add(Particle(bossPos, Offset(Random.nextFloat()*60-30, Random.nextFloat()*60-30), Color.White, 2f, Random.nextFloat()*15+8f)) }
                            }
                        } else {
                            currentScreenShake = (Random.nextFloat() - 0.5f) * 15f
                            shipAlpha = (1f - (endSequenceTime / 1000f)).coerceAtLeast(0f)
                            if (endSequenceTime % 120 < 20) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                newShockwaves.add(Shockwave(0f, 1f, shipPos + Offset(Random.nextFloat()*40-20, Random.nextFloat()*40-20)))
                                repeat(2) { newParticles.add(Particle(shipPos, Offset(Random.nextFloat()*20-10, Random.nextFloat()*20-10), Color.White, 1.2f, Random.nextFloat()*5+2f)) }
                            }
                        }

                        if (endSequenceTime > 2200) {
                            currentScreenShake = 0f
                            
                            val totalMs = gameTime
                            val m = (totalMs / 60000).toInt()
                            val s = (totalMs / 1000 % 60).toInt()
                            val c = (totalMs % 1000 / 10).toInt()
                            val fTs = "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}:${c.toString().padStart(2, '0')}"
                            
                            statsManager.saveStat(GameStat(System.currentTimeMillis(), gameResult, fTs, accuracy.value))
                            gamePhase = "LEADERBOARD"
                        }
                    }

                    
                    particles = newParticles.mapNotNull { p ->
                        val nextLife = p.life - 0.04f
                        if (nextLife <= 0) null else p.copy(pos = p.pos + p.vel, life = nextLife)
                    }

                    shockwaves = newShockwaves.mapNotNull { s ->
                        val nextAlpha = s.alpha - 0.04f
                        if (nextAlpha <= 0) null else s.copy(radius = s.radius + 10f, alpha = nextAlpha)
                    }

                    damageTexts = newDamageTexts.mapNotNull { dt ->
                        val nextLife = dt.life - 0.02f
                        if (nextLife <= 0) null else dt.copy(pos = Offset(dt.pos.x, dt.pos.y - 1.5f), life = nextLife)
                    }
                    
                    bullets = newBullets
                }
            }
        }

        Crossfade(targetState = (gamePhase == "LEADERBOARD"), animationSpec = tween(1000), label = "main_transition") { showLeaderboard ->
            if (showLeaderboard) {
                val hexD = remember { List(20) { Random.nextInt(0x1000, 0xFFFF).toString(16).uppercase() } }
                val mO by rememberInfiniteTransition(label="m").animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label="matrix")
                var aS by remember { mutableIntStateOf(0) }
                val hT = if (gameResult == "WON") "PURITY RESTORED" else "INTEGRITY BREACH"
                var cC by remember { mutableIntStateOf(0) }
                LaunchedEffect(gameResult) { aS = 1; while (cC < hT.length) { delay(60); cC++ }; delay(300); aS = 2; delay(800); aS = 3; delay(600); aS = 4 }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(0.98f))) {
                    Canvas(Modifier.fillMaxSize()) { hexD.forEachIndexed { i, h -> val x = (i * (size.width / 20)); val y = ((mO * size.height) + (i * 100)) % size.height; drawText(textMeasurer, h, Offset(x, y), TextStyle(color = Color.White.copy(0.05f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)) } }
                    Column(Modifier.align(Alignment.Center).padding(24.dp).border(1.dp, Color.White.copy(0.1f)).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(hT.take(cC), color = Color.White, style = TextStyle(fontSize = 28.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Light, letterSpacing = 8.sp, textAlign = TextAlign.Center))
                        AnimatedVisibility(aS >= 2, enter = fadeIn(tween(1000)) + expandVertically(expandFrom = Alignment.Top)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { 
                            Spacer(Modifier.height(8.dp)); Text(if(gameResult == "WON") "EXTERNAL INFLUENCE NEUTRALIZED" else "SYSTEM PURITY COMPROMISED", color = Color.White.copy(0.4f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 2.sp))
                            Spacer(Modifier.height(48.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                                val ad = accuracy.value.toString().length
                                val sp = if (ad == 3) 16.dp else if (ad == 2) 24.dp else 32.dp
                                Box(Modifier.size(12.dp).border(1.dp, Color.White.copy(0.4f))); Spacer(Modifier.width(sp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("FINAL ACCURACY", color = Color.White.copy(0.5f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 4.sp)); Text("${accuracy.value}%", color = Color.White, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.ExtraLight)) }
                                Spacer(Modifier.width(sp)); Box(Modifier.size(12.dp).border(1.dp, Color.White.copy(0.4f)))
                            }
                        } }
                        Spacer(Modifier.height(48.dp)); AnimatedVisibility(aS >= 3, enter = fadeIn(tween(800)) + slideInVertically { it / 2 }) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("MISSION_LOG // SESSION_HISTORY", color = Color.White.copy(0.2f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.sp))
                            Spacer(Modifier.height(16.dp)); Box(Modifier.height(180.dp).width(320.dp)) { LazyColumn {
                                items(statsManager.history.take(10)) { s ->
                                    val dStr = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(s.timestamp))
                                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(dStr, color = Color.White.copy(0.3f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp), modifier = Modifier.width(90.dp))
                                        Text(s.time, color = Color.White.copy(0.6f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, textAlign = TextAlign.Center), modifier = Modifier.width(60.dp))
                                        Text(if (s.result == "WON") "PURGED" else "FAILED", color = if (s.result == "WON") Color.White else Color.Gray, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(60.dp))
                                        Text("${s.accuracy}%", color = Color.White.copy(0.4f), style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, textAlign = TextAlign.End), modifier = Modifier.width(50.dp))
                                    }
                                }
                            } }
                        } }
                        Spacer(Modifier.height(48.dp)); AnimatedVisibility(visible = aS >= 4, enter = fadeIn(tween(1000))) { 
                            Text(text = "[ RE-ENGAGE_SYSTEM ]", modifier = Modifier.clickable { onExit() }, color = Color.White, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = FontFamily.Monospace)) 
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { 
                    awaitPointerEventScope { 
                        while (true) { 
                            val e = awaitPointerEvent()
                            e.changes.forEach { c -> 
                                if (gamePhase == "PLAYING") { 
                                    if (c.pressed && !c.previousPressed) { 
                                        if (!ignoreTaps) {
                                            fireCommands.add(shipPos - Offset(0f, 60f))
                                        }
                                    }
                                    if (c.pressed) { 
                                        val m = c.positionChange()
                                        shipLean = (m.x * 0.8f).coerceIn(-25f, 25f)
                                        shipPos = Offset((shipPos.x + m.x).coerceIn(60f, sw - 60f), (shipPos.y + m.y).coerceIn(200f, sh - 120f))
                                        c.consume() 
                                    } else { 
                                        shipLean = 0f 
                                    } 
                                } 
                            } 
                        } 
                    } 
                }) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = currentScreenShake }) {
                        warpLines.forEach { w -> drawLine(color = Color.White.copy(0.12f), start = Offset(w.x, w.y), end = Offset(w.x, w.y + w.length), strokeWidth = 2f, cap = StrokeCap.Round) }
                        shockwaves.forEach { s -> drawCircle(color = Color.White.copy(alpha = s.alpha.coerceIn(0f, 1f)), radius = s.radius, center = s.pos, style = Stroke(2f)) }
                        
                        if (bossAlpha > 0.01f) {
                            val pulse = if (gamePhase == "PLAYING") sin(gameTime / 180f) * 0.1f else 0f; val bR = 120f
                            val bA = if (gamePhase == "PLAYING") (if(bossHitFlash > 0.1f) 0.8f else 1f) else bossAlpha.coerceIn(0f, 1f)
                            drawCircle(Color.White.copy((0.12f * bA).coerceIn(0f, 1f)), 260f * (1f + pulse), bossPos)
                            drawCircle(Color.White.copy(bA), bR, bossPos, style = Stroke(3.dp.toPx()))
                            rotate(gameTime / 8f, pivot = bossPos) { 
                                val aR = 135f
                                drawArc(Color.White.copy((0.4f * bA).coerceIn(0f, 1f)), 0f, 60f, false, bossPos - Offset(aR, aR), Size(aR * 2, aR * 2), style = Stroke(2.dp.toPx()))
                                drawArc(Color.White.copy((0.4f * bA).coerceIn(0f, 1f)), 180f, 60f, false, bossPos - Offset(aR, aR), Size(aR * 2, aR * 2), style = Stroke(2.dp.toPx())) 
                            }
                            val gS = 55f; val gR = Rect(bossPos.x - gS, bossPos.y - gS, bossPos.x + gS, bossPos.y + gS)
                            drawArc(Color.White.copy(bA), 35f, 290f, false, gR.topLeft, gR.size, style = Stroke(7.dp.toPx()))
                            drawLine(Color.White.copy(bA), Offset(bossPos.x + 15f, bossPos.y), Offset(bossPos.x + gS, bossPos.y), strokeWidth = 7.dp.toPx())
                        }
                        
                        if (shipAlpha > 0.01f) {
                            rotate(shipLean, pivot = shipPos) { translate(shipPos.x - 48f, shipPos.y - 48f) {
                                val w = 96f; val h = 96f
                                val fP = Path().apply { moveTo(w * 0.45f, h * 0.8f); lineTo(w * 0.5f, h * (0.8f + 0.25f * tS)); lineTo(w * 0.55f, h * 0.8f); close() }
                                drawPath(fP, Color.White.copy((0.6f * shipAlpha).coerceIn(0f, 1f))); drawCircle(Color.White.copy((0.3f * shipAlpha).coerceIn(0f, 1f)), radius = 7.dp.toPx() * tS, center = Offset(w * 0.5f, h * 0.82f))
                                val bP = Path().apply { moveTo(w * 0.5f, h * 0.1f); lineTo(w * 0.58f, h * 0.35f); lineTo(w * 0.85f, h * 0.65f); lineTo(w * 0.92f, h * 0.85f); lineTo(w * 0.72f, h * 0.85f); lineTo(w * 0.62f, h * 0.72f); lineTo(w * 0.5f, h * 0.8f); lineTo(w * 0.38f, h * 0.72f); lineTo(w * 0.28f, h * 0.85f); lineTo(w * 0.08f, h * 0.85f); lineTo(w * 0.15f, h * 0.65f); lineTo(w * 0.42f, h * 0.35f); close() }
                                drawPath(bP, Color.White.copy(shipAlpha.coerceIn(0f, 1f)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                                drawLine(Color.White.copy((0.5f * shipAlpha).coerceIn(0f, 1f)), Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.5f), strokeWidth = 1.dp.toPx())
                            } }
                        }
                        
                        damageTexts.forEach { dt -> drawText(textMeasurer, dt.text, dt.pos, TextStyle(color = Color.White.copy(dt.life.coerceIn(0f, 1f)), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)) }
                        bullets.forEach { b -> if (b.isEnemy) { drawCircle(Color.White.copy(0.3f), 12f, b.position); drawCircle(Color.White, 8f, b.position) } else { drawLine(Color.White, b.position, b.position - Offset(0f, 25f), strokeWidth = 4f, cap = StrokeCap.Round) } }
                        particles.forEach { p -> drawCircle(p.color.copy(p.life.coerceIn(0f, 1f)), radius = p.size * p.life, center = p.pos) }
                    }
                    
                    val mStart = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) }
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
                        Column(Modifier.align(Alignment.TopStart)) {
                            Text(text = if (overdriveMode) "OVERDRIVE PROTOCOL" else "AXION COMBAT", modifier = Modifier.clickable(indication=null, interactionSource=remember{MutableInteractionSource()}){ secretTaps++; if(secretTaps==5){ overdriveMode=!overdriveMode; haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }, color = Color.White, style = TextStyle(letterSpacing = 2.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 14.sp))
                            Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ORB", color = Color.White.copy(0.5f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(end = 8.dp))
                                Box(Modifier.width(140.dp).height(6.dp).background(Color.White.copy(0.1f))) { Box(Modifier.fillMaxHeight().fillMaxWidth(animatedHealth).background(Color.White)) }
                                Spacer(Modifier.width(10.dp)); Text("${bossHealth.toInt()}/2026", color = Color.White.copy(0.5f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                Spacer(Modifier.width(16.dp)); Text(text = "ACC: ${accuracy.value}%", style = TextStyle(fontSize = 10.sp, color = Color.White.copy(0.8f), fontFamily = FontFamily.Monospace))
                            }
                            Spacer(Modifier.height(10.dp)); Text("$mStart > MISSION START", color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text(text = "T+ $liveTimeStr", color = Color.White, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), modifier = Modifier.align(Alignment.TopEnd))
                        if (gamePhase == "PLAYING") Text("TOUCH TO MOVE • TAP OTHER FINGER TO FIRE", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), color = Color.White.copy(0.3f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp)
                    }
                }
            }
        }
    }
}
