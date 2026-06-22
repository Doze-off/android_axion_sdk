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
package com.android.axion.blur.model

data class AxBackdropBlurSettingsSpec internal constructor(
    internal val enabledKey: String?,
    internal val radiusKey: String,
    internal val defaultEnabled: Boolean,
    internal val defaultRadiusPx: Float?,
    internal val maxRadiusPx: Float?,
    internal val radiusInPercent: Boolean,
) {
    companion object {
        private const val KEY_SYSTEM_BLUR_RADIUS_PCT = "system_blur_radius_pct"
        private const val KEY_LAUNCHER_BLUR_ENABLED = "pulse_launcher_blur_enabled"
        private const val KEY_LAUNCHER_BLUR_RADIUS_PCT = "pulse_launcher_blur_radius_pct"
        private const val DEFAULT_LAUNCHER_BLUR_RADIUS_PX = 34f
        private const val MAX_LAUNCHER_BLUR_RADIUS_PX = 175f
        private val SYSTEM = AxBackdropBlurSettingsSpec(
            enabledKey = null,
            radiusKey = KEY_SYSTEM_BLUR_RADIUS_PCT,
            defaultEnabled = true,
            defaultRadiusPx = null,
            maxRadiusPx = null,
            radiusInPercent = true,
        )

        @JvmStatic
        fun system(): AxBackdropBlurSettingsSpec {
            return SYSTEM
        }

        @JvmStatic
        @JvmOverloads
        fun launcher(
            defaultRadiusPx: Float = DEFAULT_LAUNCHER_BLUR_RADIUS_PX,
            maxRadiusPx: Float = MAX_LAUNCHER_BLUR_RADIUS_PX,
        ): AxBackdropBlurSettingsSpec {
            return AxBackdropBlurSettingsSpec(
                enabledKey = KEY_LAUNCHER_BLUR_ENABLED,
                radiusKey = KEY_LAUNCHER_BLUR_RADIUS_PCT,
                defaultEnabled = false,
                defaultRadiusPx = defaultRadiusPx,
                maxRadiusPx = maxRadiusPx,
                radiusInPercent = true,
            )
        }

        @JvmStatic
        fun secure(
            enabledKey: String,
            radiusKey: String,
            defaultEnabled: Boolean,
            defaultRadiusPx: Float,
            maxRadiusPx: Float,
        ): AxBackdropBlurSettingsSpec {
            return AxBackdropBlurSettingsSpec(
                enabledKey = enabledKey,
                radiusKey = radiusKey,
                defaultEnabled = defaultEnabled,
                defaultRadiusPx = defaultRadiusPx,
                maxRadiusPx = maxRadiusPx,
                radiusInPercent = false,
            )
        }
    }
}
