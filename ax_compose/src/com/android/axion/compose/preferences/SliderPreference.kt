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

import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SliderPreference(
    title: String,
    summary: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    displayValue: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    onReset: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = preferenceShape(position)
    val haptic = LocalHapticFeedback.current
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 60.dp)
                )
                
                if (onReset != null && enabled) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReset()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

@Composable
fun SecureSettingSlider(
    settingKey: String,
    title: String,
    summary: String,
    min: Int = 0,
    max: Int,
    interval: Int = 1,
    unit: String = "",
    defaultValue: Int = min,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    position: PreferencePosition = LocalPreferencePosition.current,
    formatValue: ((Int) -> String)? = null
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var currentValue by remember {
        mutableFloatStateOf(
            try {
                Settings.Secure.getInt(contentResolver, settingKey, defaultValue).toFloat()
            } catch (e: Exception) {
                defaultValue.toFloat()
            }
        )
    }
    
    DisposableEffect(settingKey) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                currentValue = try {
                    Settings.Secure.getInt(contentResolver, settingKey, defaultValue).toFloat()
                } catch (e: Exception) {
                    defaultValue.toFloat()
                }
            }
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(settingKey),
            false,
            observer
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }
    
    val displayValue = formatValue?.invoke(currentValue.roundToInt()) ?: run {
        val intValue = currentValue.roundToInt()
        when {
            unit.equals("MHz", ignoreCase = true) -> "${intValue / 1000} MHz"
            unit.equals("Level", ignoreCase = true) -> "Level $intValue"
            unit.isNotEmpty() -> "$intValue $unit"
            else -> intValue.toString()
        }
    }
    
    SliderPreference(
        title = title,
        summary = summary,
        value = currentValue,
        onValueChange = { newValue ->
            val steppedValue = ((newValue - min) / interval).roundToInt() * interval + min
            currentValue = steppedValue.coerceIn(min, max).toFloat()
        },
        onValueChangeFinished = {
            Settings.Secure.putInt(contentResolver, settingKey, currentValue.roundToInt())
        },
        onReset = {
            Settings.Secure.putInt(contentResolver, settingKey, defaultValue)
            currentValue = defaultValue.toFloat()
        },
        valueRange = min.toFloat()..max.toFloat(),
        steps = 0, 
        displayValue = displayValue,
        modifier = modifier,
        enabled = enabled,
        position = position
    )
}
