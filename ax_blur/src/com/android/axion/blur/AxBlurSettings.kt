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

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.axion.blur.domain.interactor.AxBackdropBlurInteractor
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSpec
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSubscription

class AxBlurSettings @JvmOverloads constructor(
    context: Context,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val interactor = AxBackdropBlurInteractor(context, settingsSpec, handler)
    private var subscription: AxBackdropBlurSettingsSubscription? = null

    val enabled: Boolean
        get() = interactor.settings().enabled

    val blurRadiusPx: Float
        get() = interactor.settings().blurRadiusPx

    fun start(onSettingsChanged: Runnable) {
        stop()
        subscription = interactor.createSubscription {
            onSettingsChanged.run()
        }.also {
            it.start()
        }
    }

    fun stop() {
        subscription?.stop()
        subscription = null
    }

    companion object {
        @JvmStatic
        fun launcher(context: Context): AxBlurSettings {
            return AxBlurSettings(context, AxBackdropBlurSettingsSpec.launcher())
        }

        @JvmStatic
        fun launcher(context: Context, defaultRadiusPx: Float): AxBlurSettings {
            return AxBlurSettings(context, AxBackdropBlurSettingsSpec.launcher(defaultRadiusPx))
        }
    }
}
