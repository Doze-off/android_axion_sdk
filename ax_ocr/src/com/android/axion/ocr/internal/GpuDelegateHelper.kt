/*
 * Copyright 2025-2026 AxionOS
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

package com.android.axion.ocr.internal

import android.util.Log
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

object GpuDelegateHelper {

    private const val TAG = "AxOcr"

    fun createGpuDelegate(): Delegate? {
        return try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                GpuDelegate(compatList.bestOptionsForThisDevice)
            } else {
                GpuDelegate()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
            null
        }
    }
}
