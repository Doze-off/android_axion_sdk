/*
 * Copyright (C) 2025-2026 AxionOS
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

package com.android.axion.blur

import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSpec
import com.android.axion.blur.ui.view.AxViewBackdropBlur
import com.android.axion.kotlin.settings.SettingsFlow
import com.android.axion.kotlin.settings.SettingsType
import kotlin.math.roundToInt

val LocalAxBlurEnabled = staticCompositionLocalOf { true }

object AxBlurSurfaceDefaults {
    @Composable
    fun surfaceColor(): Color = colorResource(R.color.ax_blur_surface_bright_expressive)

    @Composable
    fun surfaceColor(alpha: Float): Color =
        surfaceColor().copy(alpha = alpha.coerceIn(0f, 1f))

    @Composable
    fun tintColor(surfaceColor: Color? = null): Color {
        val tint = colorResource(R.color.ax_blur_surface_bright_expressive_tint)
        return surfaceColor?.copy(alpha = tint.alpha) ?: tint
    }

    @Composable
    fun tintColor(alpha: Float, surfaceColor: Color? = null): Color {
        val tint = tintColor(surfaceColor)
        return tint.copy(alpha = tint.alpha * alpha.coerceIn(0f, 1f))
    }
}

@Composable
fun AxBlurLifecycle(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val parentEnabled = LocalAxBlurEnabled.current
    CompositionLocalProvider(
        LocalAxBlurEnabled provides (parentEnabled && enabled),
        content = content,
    )
}

@Composable
fun rememberAxBlurEnabled(
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
): Boolean {
    val resolver = LocalContext.current.contentResolver
    val globalSettings = remember(resolver) { SettingsFlow(resolver, SettingsType.GLOBAL) }
    val globalDefaultDisabled = !AxBlurProperties.defaultGlobalBlurEnabled
    val globalDisabled by remember(globalSettings, globalDefaultDisabled) {
        globalSettings.observeBoolean(Settings.Global.DISABLE_WINDOW_BLURS, globalDefaultDisabled)
    }.collectAsState(
        initial = globalSettings.getInt(
            Settings.Global.DISABLE_WINDOW_BLURS,
            if (globalDefaultDisabled) 1 else 0,
        ) != 0,
    )
    val enabledKey = settingsSpec.enabledKey
    val settingEnabled = if (enabledKey == null) {
        true
    } else {
        val secureSettings = remember(resolver) { SettingsFlow(resolver, SettingsType.SECURE) }
        val value by remember(secureSettings, enabledKey, settingsSpec.defaultEnabled) {
            secureSettings.observeBoolean(enabledKey, settingsSpec.defaultEnabled)
        }.collectAsState(
            initial = secureSettings.getInt(
                enabledKey,
                if (settingsSpec.defaultEnabled) 1 else 0,
            ) != 0,
        )
        value
    }
    return !globalDisabled && settingEnabled
}

@Composable
fun AxBlurSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    cornerRadius: Dp,
    enabled: Boolean = true,
    surfaceColor: Color? = null,
    tintColor: Color? = null,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val resolvedSurfaceColor = surfaceColor ?: AxBlurSurfaceDefaults.surfaceColor()
    val resolvedTintColor = tintColor ?: AxBlurSurfaceDefaults.tintColor(surfaceColor)
    Box(
        modifier = modifier
            .clip(shape)
            .axBlurBackground(
                enabled = enabled,
                fallbackColor = resolvedSurfaceColor,
                tintColor = resolvedTintColor,
                cornerRadius = cornerRadius,
                settingsSpec = settingsSpec,
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun Modifier.axBlurBackground(
    enabled: Boolean,
    fallbackColor: Color,
    tintColor: Color,
    cornerRadius: Dp = Dp.Unspecified,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
): Modifier {
    val density = LocalDensity.current
    val view = LocalView.current
    val blurEnabled = LocalAxBlurEnabled.current && enabled
    val blur = remember(view, settingsSpec) {
        AxViewBackdropBlur(view).apply {
            useSettings(settingsSpec)
            setEnabled(blurEnabled)
        }
    }
    val tintArgb = tintColor.toArgb()
    val requestedCornerRadiusPx = if (cornerRadius.isSpecified) {
        with(density) { cornerRadius.toPx() }
    } else {
        Float.NaN
    }

    DisposableEffect(blur) {
        blur.onAttachedToWindow()
        onDispose {
            blur.onDetachedFromWindow()
        }
    }

    SideEffect {
        blur.setEnabled(blurEnabled)
    }

    return drawWithContent {
        val width = size.width.roundToInt()
        val height = size.height.roundToInt()
        val cornerRadiusPx = if (requestedCornerRadiusPx.isFinite()) {
            requestedCornerRadiusPx
        } else {
            size.minDimension * 0.5f
        }
        val drewBlur = if (blurEnabled && width > 0 && height > 0) {
            var result = false
            blur.setOverlayColor(tintArgb)
            drawIntoCanvas { canvas ->
                result = blur.draw(
                    canvas.nativeCanvas,
                    0,
                    0,
                    width,
                    height,
                    cornerRadiusPx,
                )
            }
            result
        } else {
            false
        }
        if (!drewBlur) {
            drawRoundRect(
                color = fallbackColor,
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }
        drawContent()
    }
}
