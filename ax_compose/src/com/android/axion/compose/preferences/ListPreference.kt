/*
 * Copyright (C) 2025 AxionOS Project
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

package com.android.axion.compose.preferences

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SecureListPreference(
    key: String,
    title: String,
    summary: String,
    options: List<Pair<String, String>>,
    defaultValue: String,
    position: PreferencePosition = LocalPreferencePosition.current,
    dependencyKey: String? = null
) {
    val (value, setValue) = rememberSecureSettingStringState(key, defaultValue)
    val enabled = if (dependencyKey != null) {
        rememberSecureSettingBoolean(dependencyKey, true)
    } else {
        true
    }
    
    var showDialog by remember { mutableStateOf(false) }
    val shape = preferenceShape(position)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(enabled = enabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        }
    }
    
    if (showDialog && enabled) {
        ListPreferenceDialog(
            title = title,
            options = options,
            selectedKey = value,
            onOptionSelected = { newValue ->
                setValue(newValue)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun ListPreference(
    title: String,
    summary: String? = null,
    options: List<Pair<String, String>>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    var showDialog by remember { mutableStateOf(false) }
    val shape = preferenceShape(position)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(enabled = enabled) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface 
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        }
    }

    if (showDialog && enabled) {
        ListPreferenceDialog(
            title = title,
            options = options,
            selectedKey = value,
            onOptionSelected = { newValue ->
                onValueChange(newValue)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ListPreferenceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onOptionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    val canScrollUp by remember { derivedStateOf { scrollState.value > 0 } }
    val canScrollDown by remember { derivedStateOf { scrollState.value < scrollState.maxValue } }
    val isScrollable by remember { derivedStateOf { scrollState.maxValue > 0 } }

    val topFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollUp) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "topFade"
    )
    val bottomFadeAlpha by animateFloatAsState(
        targetValue = if (canScrollDown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "bottomFade"
    )

    val fadeColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .clip(ExpressiveShapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isScrollable) {
                ScrollIndicatorHint(
                    iconUp = true,
                    visible = canScrollUp,
                    fadeColor = fadeColor
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .drawWithContent {
                            drawContent()
                            val fadeHeight = 24.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(fadeColor, Color.Transparent),
                                    startY = 0f,
                                    endY = fadeHeight
                                ),
                                alpha = topFadeAlpha
                            )
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, fadeColor),
                                    startY = size.height - fadeHeight,
                                    endY = size.height
                                ),
                                alpha = bottomFadeAlpha
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (key, label) ->
                        val isSelected = selectedKey == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer
                                )
                                .clickable { onOptionSelected(key) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onPrimary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (isScrollable) {
                    val scrollbarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                            .width(4.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = trackColor,
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )

                                val viewportHeight = size.height
                                val totalContentHeight = scrollState.maxValue + viewportHeight
                                val thumbHeight = (viewportHeight / totalContentHeight) * viewportHeight
                                val minThumbHeight = 24.dp.toPx()
                                val actualThumbHeight = thumbHeight.coerceAtLeast(minThumbHeight)

                                val scrollRange = viewportHeight - actualThumbHeight
                                val scrollProgress = if (scrollState.maxValue > 0) {
                                    scrollState.value.toFloat() / scrollState.maxValue.toFloat()
                                } else 0f
                                val thumbOffset = scrollProgress * scrollRange

                                drawRoundRect(
                                    color = scrollbarColor,
                                    topLeft = Offset(0f, thumbOffset),
                                    size = Size(size.width, actualThumbHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                )
                            }
                    )
                }
            }

            if (isScrollable) {
                ScrollIndicatorHint(
                    iconUp = false,
                    visible = canScrollDown,
                    fadeColor = fadeColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollIndicatorHint(
    iconUp: Boolean,
    visible: Boolean,
    fadeColor: Color
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0.3f,
        animationSpec = tween(durationMillis = 200),
        label = "indicatorAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (visible) 3f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounceOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .graphicsLayer {
                this.alpha = alpha
                translationY = if (iconUp) -bounceOffset else bounceOffset
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 24.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (visible) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
        )
    }
}
