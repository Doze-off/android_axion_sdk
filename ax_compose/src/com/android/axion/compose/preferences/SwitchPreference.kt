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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.android.axion.compose.preferences

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class PreferencePosition {
    Single,
    Top,
    Middle,
    Bottom
}

private val preferenceCornerRadius = 20.dp
private val bottomTopCornerRadius = 4.dp

val LocalPreferencePosition = compositionLocalOf { PreferencePosition.Single }

fun preferenceShape(position: PreferencePosition): Shape {
    return when (position) {
        PreferencePosition.Single -> RoundedCornerShape(preferenceCornerRadius)
        PreferencePosition.Top -> RoundedCornerShape(
            topStart = preferenceCornerRadius,
            topEnd = preferenceCornerRadius,
            bottomStart = bottomTopCornerRadius,
            bottomEnd = bottomTopCornerRadius
        )
        PreferencePosition.Middle -> RoundedCornerShape(4.dp)
        PreferencePosition.Bottom -> RoundedCornerShape(
            topStart = bottomTopCornerRadius,
            topEnd = bottomTopCornerRadius,
            bottomStart = preferenceCornerRadius,
            bottomEnd = preferenceCornerRadius
        )
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = preferenceShape(position)
    val resolvedIconTint = iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled
            ) { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .then(
                            if (iconBackgroundColor != null) Modifier.background(iconBackgroundColor)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = resolvedIconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.padding(end = 16.dp)
            ) {
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
        Switch(
            checked = checked,
            onCheckedChange = null,
            interactionSource = interactionSource,
            enabled = enabled,
            thumbContent = {
                Crossfade(
                    targetState = checked,
                    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec()
                ) { isChecked ->
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                }
            },
            colors = SwitchDefaults.colors(
                uncheckedTrackColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SecureSettingSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var isChecked by remember {
        mutableStateOf(
            try {
                Settings.Secure.getInt(contentResolver, settingKey, if (defaultValue) 1 else 0) == 1
            } catch (e: Exception) {
                defaultValue
            }
        )
    }
    
    DisposableEffect(settingKey) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isChecked = try {
                    Settings.Secure.getInt(contentResolver, settingKey, if (defaultValue) 1 else 0) == 1
                } catch (e: Exception) {
                    defaultValue
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
    
    SwitchPreference(
        title = title,
        summary = summary,
        checked = isChecked,
        onCheckedChange = { newValue ->
            isChecked = newValue
            Settings.Secure.putInt(contentResolver, settingKey, if (newValue) 1 else 0)
        },
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        position = position
    )
}

@Composable
fun SystemSettingSwitch(
    settingKey: String,
    title: String,
    summary: String? = null,
    defaultValue: Boolean = false,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    iconTint: Color? = null,
    iconBackgroundColor: Color? = null,
    position: PreferencePosition = LocalPreferencePosition.current
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    
    var isChecked by remember {
        mutableStateOf(
            try {
                Settings.System.getInt(contentResolver, settingKey, if (defaultValue) 1 else 0) == 1
            } catch (e: Exception) {
                defaultValue
            }
        )
    }
    
    DisposableEffect(settingKey) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                isChecked = try {
                    Settings.System.getInt(contentResolver, settingKey, if (defaultValue) 1 else 0) == 1
                } catch (e: Exception) {
                    defaultValue
                }
            }
        }
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(settingKey),
            false,
            observer
        )
        onDispose { contentResolver.unregisterContentObserver(observer) }
    }
    
    SwitchPreference(
        title = title,
        summary = summary,
        checked = isChecked,
        onCheckedChange = { newValue ->
            isChecked = newValue
            Settings.System.putInt(contentResolver, settingKey, if (newValue) 1 else 0)
        },
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        iconTint = iconTint,
        iconBackgroundColor = iconBackgroundColor,
        position = position
    )
}
