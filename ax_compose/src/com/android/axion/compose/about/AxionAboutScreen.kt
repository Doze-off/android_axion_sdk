/*
 * Copyright (C) 2025 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.axion.compose.about

import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AxionAboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    onEditDeviceName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var deviceInfo by remember { mutableStateOf(DeviceInfoProvider.getDeviceInfo(context)) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    
    val wallpaperBitmap = remember {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val drawable = wallpaperManager.drawable
            if (drawable is BitmapDrawable) drawable.bitmap.asImageBitmap() else null
        } catch (e: Exception) { null }
    }
    
    val cardColor = MaterialTheme.colorScheme.surface
    
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("About device", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            ImmersiveHeroBanner(
                wallpaperBitmap = wallpaperBitmap,
                version = deviceInfo.axionVersion,
                buildType = deviceInfo.axionBuildType,
                maintainer = deviceInfo.maintainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            DeviceInfoCard(
                deviceName = deviceInfo.deviceName,
                storageUsed = deviceInfo.storageUsed,
                storageTotal = deviceInfo.storageTotal,
                onEditDeviceName = { showEditDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SpecsList(
                maintainer = deviceInfo.maintainer,
                processor = deviceInfo.processor,
                ram = deviceInfo.totalRam,
                rearCamera = deviceInfo.rearCamera,
                frontCamera = deviceInfo.frontCamera,
                battery = deviceInfo.batteryCapacity,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            NavigationCard(
                icon = Icons.Outlined.Info,
                title = "More device info",
                subtitle = "IMEI, network, status",
                onClick = onNavigateToDeviceInfo,
                cardColor = cardColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    if (showEditDialog) {
        var text by remember { mutableStateOf(deviceInfo.deviceName) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit device name") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Device name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        DeviceInfoProvider.setDeviceName(context, text.trim())
                        deviceInfo = deviceInfo.copy(deviceName = text.trim())
                        onEditDeviceName(text.trim())
                    }
                    showEditDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ImmersiveHeroBanner(
    wallpaperBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    version: String,
    buildType: String,
    maintainer: String
) {
    val accentColor = colorResource(android.R.color.system_accent1_100)
    
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatOffset"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(280.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = 800f
                    )
                )
        ) {
            if (wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "AXIONOS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = accentColor
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "v$version",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .offset(y = (-floatOffset).dp)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            accentColor.copy(alpha = 0.2f),
                                            Color.Black
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedDeviceIllustration(
                                deviceName = "AxionOS",
                                version = version,
                                atomColor = accentColor,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.3f),
                                style = Stroke(width = 1.dp.toPx()),
                                cornerRadius = CornerRadius(16.dp.toPx())
                            )
                            
                            val punchHoleRadius = 4.dp.toPx()
                            drawCircle(
                                color = Color.Black,
                                radius = punchHoleRadius,
                                center = Offset(size.width / 2, 12.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    deviceName: String,
    storageUsed: String,
    storageTotal: String,
    onEditDeviceName: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usedGb = storageUsed.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 0f
    val totalGb = storageTotal.replace(Regex("[^0-9.]"), "").toFloatOrNull() ?: 1f
    val storagePercent = (usedGb / totalGb).coerceIn(0f, 1f)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surface
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEditDeviceName),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                PhoneOutlineIcon(
                    color = primaryColor,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    "Device name",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
                
                Spacer(Modifier.height(2.dp))
                
                Text(
                    deviceName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                WaveStorageIndicator(
                    fillPercent = storagePercent,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(Modifier.height(12.dp))
                
                Text(
                    "Storage",
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
                
                Spacer(Modifier.height(2.dp))
                
                Text(
                    "$storageUsed / $storageTotal",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PhoneOutlineIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val cornerRadius = 4.dp.toPx()
        
        val phoneWidth = size.width * 0.5f
        val phoneHeight = size.height * 0.85f
        val phoneLeft = 0f
        val phoneTop = (size.height - phoneHeight) / 2
        
        val phonePath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = phoneLeft,
                    top = phoneTop,
                    right = phoneLeft + phoneWidth,
                    bottom = phoneTop + phoneHeight,
                    cornerRadius = CornerRadius(cornerRadius)
                )
            )
        }
        
        drawPath(
            path = phonePath,
            color = color.copy(alpha = 0.1f)
        )
        
        drawPath(
            path = phonePath,
            color = color,
            style = Stroke(width = strokeWidth)
        )
        
        val lineWidth = phoneWidth * 0.4f
        val lineHeight = 2.dp.toPx()
        val lineLeft = phoneLeft + (phoneWidth - lineWidth) / 2
        val lineTop = phoneTop + phoneHeight - 6.dp.toPx()
        
        drawRoundRect(
            color = color,
            topLeft = Offset(lineLeft, lineTop),
            size = Size(lineWidth, lineHeight),
            cornerRadius = CornerRadius(lineHeight / 2)
        )
    }
}

@Composable
private fun WaveStorageIndicator(
    fillPercent: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )
    
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val circleRadius = (size.minDimension / 2) - strokeWidth
        val center = Offset(size.width / 2, size.height / 2)
        
        drawCircle(
            color = color.copy(alpha = 0.1f),
            radius = circleRadius,
            center = center
        )
        
        drawCircle(
            color = color,
            radius = circleRadius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        
        val fillHeight = size.height * (1f - fillPercent)
        val waveAmplitude = 3.dp.toPx()
        val waveFrequency = 2f
        
        val wavePath = Path().apply {
            moveTo(0f, fillHeight)
            for (x in 0..size.width.toInt()) {
                val radians = Math.toRadians((x * waveFrequency + waveOffset).toDouble())
                val y = fillHeight + (waveAmplitude * sin(radians)).toFloat()
                lineTo(x.toFloat(), y)
            }
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        
        val circlePath = Path().apply {
            addOval(Rect(center, circleRadius))
        }
        
        clipPath(circlePath) {
            drawPath(wavePath, color.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun SpecsList(
    maintainer: String,
    processor: String,
    ram: String,
    rearCamera: String,
    frontCamera: String,
    battery: String,
    modifier: Modifier = Modifier
) {
    val cardColor = MaterialTheme.colorScheme.surface
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SpecItem(label = "Maintainer", value = maintainer)
            SpecItem(label = "Processor", value = processor)
            SpecItem(label = "RAM", value = ram)
            SpecItem(label = "Camera", value = "Front $frontCamera / Rear $rearCamera")
            SpecItem(label = "Battery", value = battery)
        }
    }
}

@Composable
private fun SpecItem(
    label: String,
    value: String
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = onSurface
        )
    }
}

@Composable
private fun NavigationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun AxionAboutScreenPreview() {
    AxionAboutScreen(onNavigateBack = {}, onNavigateToDeviceInfo = {}, onEditDeviceName = {})
}
