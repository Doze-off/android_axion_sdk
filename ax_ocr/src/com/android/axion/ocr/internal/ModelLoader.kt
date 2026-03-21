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

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

object ModelLoader {

    fun createInterpreter(
        context: Context,
        modelFileName: String,
        useGpu: Boolean = true,
        numThreads: Int = 4
    ): Interpreter {
        val modelBuffer = FileUtil.loadMappedFile(context, modelFileName)
        val options = Interpreter.Options()

        if (useGpu) {
            val gpuDelegate = GpuDelegateHelper.createGpuDelegate()
            if (gpuDelegate != null) {
                options.addDelegate(gpuDelegate)
            } else {
                options.setNumThreads(numThreads)
            }
        } else {
            options.setNumThreads(numThreads)
        }

        return Interpreter(modelBuffer, options)
    }
}
