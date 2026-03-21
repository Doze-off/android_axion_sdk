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
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.android.axion.ocr.internal.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TextRecognizer(context: Context) {

    private val interpreter: Interpreter = ModelLoader.createInterpreter(
        context, MODEL_FILE, useGpu = false, numThreads = NUM_THREADS
    ).also { interp ->
        val inputTensor = interp.getInputTensor(0)
        val outputTensor = interp.getOutputTensor(0)
        Log.d(TAG, "Rec input: ${inputTensor.shape().contentToString()}, dtype: ${inputTensor.dataType()}")
        Log.d(TAG, "Rec output: ${outputTensor.shape().contentToString()}, dtype: ${outputTensor.dataType()}")
    }

    private val outputShape = interpreter.getOutputTensor(0).shape()
    private val numTimesteps = outputShape[1]
    private val numClasses = outputShape[2]

    fun recognize(bitmap: Bitmap, region: DetectedRegion): RecognitionResult {
        val cropped = cropAndTransform(bitmap, region)
        val resized = Bitmap.createScaledBitmap(cropped, INPUT_WIDTH, INPUT_HEIGHT, true)

        val inputBuffer = preprocessBitmap(resized)

        val logits = Array(1) { Array(numTimesteps) { FloatArray(numClasses) } }

        try {
            interpreter.run(inputBuffer, logits)
        } catch (e: Exception) {
            Log.e(TAG, "Text recognition inference failed", e)
            return RecognitionResult("", 0f)
        } finally {
            cropped.recycle()
            if (resized !== cropped) resized.recycle()
        }

        return ctcGreedyDecode(logits[0])
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_HEIGHT * INPUT_WIDTH * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        bitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            buffer.putFloat((r / 255f - 0.5f) / 0.5f)
            buffer.putFloat((g / 255f - 0.5f) / 0.5f)
            buffer.putFloat((b / 255f - 0.5f) / 0.5f)
        }
        buffer.rewind()
        return buffer
    }

    private fun cropAndTransform(bitmap: Bitmap, region: DetectedRegion): Bitmap {
        val corners = region.corners
        if (corners.size < 4) {
            val left = region.boundingBox.left.toInt().coerceIn(0, bitmap.width - 1)
            val top = region.boundingBox.top.toInt().coerceIn(0, bitmap.height - 1)
            val w = region.boundingBox.width().toInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.width - left)
            val h = region.boundingBox.height().toInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.height - top)
            return Bitmap.createBitmap(bitmap, left, top, w, h)
        }

        val width = region.boundingBox.width().toInt().coerceAtLeast(1)
        val height = region.boundingBox.height().toInt().coerceAtLeast(1)

        val src = floatArrayOf(
            corners[0].x, corners[0].y,
            corners[1].x, corners[1].y,
            corners[2].x, corners[2].y,
            corners[3].x, corners[3].y
        )
        val dst = floatArrayOf(
            0f, 0f,
            width.toFloat(), 0f,
            width.toFloat(), height.toFloat(),
            0f, height.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, matrix, null)
        return result
    }

    private fun ctcGreedyDecode(logits: Array<FloatArray>): RecognitionResult {
        val sb = StringBuilder()
        var lastIndex = -1
        var totalConfidence = 0f
        var charCount = 0
        var pendingSpace = false

        for (timestep in logits) {
            var maxIdx = 0
            var maxVal = timestep[0]
            for (j in 1 until timestep.size) {
                if (timestep[j] > maxVal) {
                    maxVal = timestep[j]
                    maxIdx = j
                }
            }

            if (maxIdx == BLANK_INDEX) {
                lastIndex = BLANK_INDEX
                continue
            }

            if (maxIdx == SPACE_INDEX) {
                if (charCount > 0) pendingSpace = true
                lastIndex = SPACE_INDEX
                continue
            }

            if (maxIdx == lastIndex) continue

            val charIndex = maxIdx - 1
            if (charIndex in CHARSET.indices) {
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(CHARSET[charIndex])
                totalConfidence += softmax(timestep, maxIdx)
                charCount++
            }
            lastIndex = maxIdx
        }

        val avgConfidence = if (charCount > 0) totalConfidence / charCount else 0f
        return RecognitionResult(sb.toString().trim(), avgConfidence)
    }

    private fun softmax(logits: FloatArray, index: Int): Float {
        var maxLogit = logits[0]
        for (v in logits) if (v > maxLogit) maxLogit = v

        var sumExp = 0f
        for (v in logits) sumExp += Math.exp((v - maxLogit).toDouble()).toFloat()

        return Math.exp((logits[index] - maxLogit).toDouble()).toFloat() / sumExp
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "AxOcr"
        private const val MODEL_FILE = "text_recognizer.tflite"
        private const val INPUT_WIDTH = 320
        private const val INPUT_HEIGHT = 48
        private const val NUM_THREADS = 4
        private const val BLANK_INDEX = 0
        private const val SPACE_INDEX = 96

        private const val CHARSET =
            "0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~!\"#\$%&'()*+,-./ "
    }
}

data class RecognitionResult(
    val text: String,
    val confidence: Float
)
