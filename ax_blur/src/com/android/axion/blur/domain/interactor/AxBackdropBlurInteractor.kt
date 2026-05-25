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
package com.android.axion.blur.domain.interactor

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.axion.blur.data.repository.AxBackdropBlurSettingsRepository
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsModel
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSpec
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSubscription

internal class AxBackdropBlurInteractor(
    context: Context,
    private val spec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val appContext = context.applicationContext ?: context
    private val repository = AxBackdropBlurSettingsRepository(appContext, handler)

    fun settings(): AxBackdropBlurSettingsModel {
        val maxRadius = spec.maxRadiusPx ?: maxBlurRadiusPx(appContext)
        val defaultRadius = spec.defaultRadiusPx ?: maxRadius
        val enabled = repository.globalBlurEnabled() && settingEnabled()
        if (!enabled) {
            return AxBackdropBlurSettingsModel(
                enabled = false,
                blurRadiusPx = 0f,
            )
        }
        val radius = repository.secureFloat(spec.radiusKey, defaultRadius)
        return AxBackdropBlurSettingsModel(
            enabled = true,
            blurRadiusPx = radius.sanitize(maxRadius),
        )
    }

    fun createSubscription(
        onSettingsChanged: () -> Unit,
    ): AxBackdropBlurSettingsSubscription {
        return repository.createSubscription(spec, onSettingsChanged)
    }

    private fun settingEnabled(): Boolean {
        val enabledKey = spec.enabledKey ?: return true
        return repository.secureInt(enabledKey, if (spec.defaultEnabled) 1 else 0) != 0
    }

    private fun Float.sanitize(maxRadius: Float): Float {
        val ceiling = if (maxRadius.isFinite() && maxRadius > 0f) maxRadius else 0f
        return if (isFinite()) coerceIn(0f, ceiling) else ceiling
    }

    companion object {
        fun maxBlurRadiusPx(context: Context): Float {
            return AxBackdropBlurSettingsRepository.maxBlurRadiusPx(context)
        }
    }
}
