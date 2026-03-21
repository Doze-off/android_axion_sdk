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

package com.android.axion.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AxOcrEngine(context: Context) {

    private val appContext = context.applicationContext
    private var detector: TextDetector? = null
    private var recognizer: TextRecognizer? = null

    @Synchronized
    private fun ensureInitialized() {
        if (detector == null) {
            detector = TextDetector(appContext)
        }
        if (recognizer == null) {
            recognizer = TextRecognizer(appContext)
        }
    }

    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        ensureInitialized()

        val det = detector ?: return@withContext OcrResult(emptyList(), 0L)
        val rec = recognizer ?: return@withContext OcrResult(emptyList(), 0L)

        val input = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val regions = det.detect(input)
        if (regions.isEmpty()) {
            if (input !== bitmap) input.recycle()
            return@withContext OcrResult(emptyList(), System.currentTimeMillis() - startTime)
        }

        val textBlocks = regions.mapNotNull { region ->
            try {
                val result = rec.recognize(input, region)
                if (result.text.isNotBlank()) {
                    TextBlock(
                        text = result.text,
                        confidence = result.confidence,
                        boundingBox = region.boundingBox,
                        corners = region.corners
                    )
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Recognition failed for region: ${region.boundingBox}", e)
                null
            }
        }

        if (input !== bitmap) input.recycle()

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "OCR completed: ${textBlocks.size} blocks in ${elapsed}ms")

        OcrResult(textBlocks, elapsed)
    }

    fun detectOnly(bitmap: Bitmap): List<DetectedRegion> {
        ensureInitialized()
        return detector?.detect(bitmap) ?: emptyList()
    }

    @Synchronized
    fun close() {
        detector?.close()
        detector = null
        recognizer?.close()
        recognizer = null
    }

    companion object {
        private const val TAG = "AxOcr"
    }
}
