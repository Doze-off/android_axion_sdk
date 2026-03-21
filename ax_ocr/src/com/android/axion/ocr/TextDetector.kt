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
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.android.axion.ocr.internal.ModelLoader
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class TextDetector(context: Context) {

    private val interpreter: Interpreter = ModelLoader.createInterpreter(
        context, MODEL_FILE, useGpu = true, numThreads = NUM_THREADS
    ).also { interp ->
        val inputTensor = interp.getInputTensor(0)
        val outputTensor = interp.getOutputTensor(0)
        Log.d(TAG, "Det input: ${inputTensor.shape().contentToString()}")
        Log.d(TAG, "Det output: ${outputTensor.shape().contentToString()}")
    }

    fun detect(bitmap: Bitmap): List<DetectedRegion> {
        val inputBuffer = preprocessBitmap(bitmap)

        val output = Array(1) { Array(1) { Array(INPUT_SIZE) { FloatArray(INPUT_SIZE) } } }

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            Log.e(TAG, "Text detection inference failed", e)
            return emptyList()
        }

        val probMap = output[0][0]

        val binaryMap = Array(INPUT_SIZE) { y ->
            BooleanArray(INPUT_SIZE) { x ->
                probMap[y][x] > BINARY_THRESHOLD
            }
        }

        val regions = findConnectedComponents(binaryMap, probMap)

        val scaleX = bitmap.width.toFloat() / INPUT_SIZE
        val scaleY = bitmap.height.toFloat() / INPUT_SIZE

        return regions.mapNotNull { region ->
            val rect = RectF(
                max(0f, region.rect.left * scaleX),
                max(0f, region.rect.top * scaleY),
                min(bitmap.width.toFloat(), region.rect.right * scaleX),
                min(bitmap.height.toFloat(), region.rect.bottom * scaleY)
            )

            val w = rect.width()
            val h = rect.height()
            if (w < MIN_BOX_SIZE || h < MIN_BOX_SIZE) return@mapNotNull null

            val aspect = w / h
            if (aspect < MIN_ASPECT_RATIO && 1f / aspect < MIN_ASPECT_RATIO) return@mapNotNull null

            val area = w * h
            val bitmapArea = bitmap.width.toFloat() * bitmap.height
            if (area / bitmapArea > MAX_AREA_RATIO) return@mapNotNull null
            if (h > bitmap.height * MAX_HEIGHT_RATIO) return@mapNotNull null

            val corners = listOf(
                PointF(rect.left, rect.top),
                PointF(rect.right, rect.top),
                PointF(rect.right, rect.bottom),
                PointF(rect.left, rect.bottom)
            )
            DetectedRegion(rect, region.score, corners, 0f)
        }.sortedWith(compareBy({ it.boundingBox.top }, { it.boundingBox.left }))
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        if (resized !== bitmap) resized.recycle()

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            buffer.putFloat((r / 255f - MEAN_R) / STD_R)
            buffer.putFloat((g / 255f - MEAN_G) / STD_G)
            buffer.putFloat((b / 255f - MEAN_B) / STD_B)
        }
        buffer.rewind()
        return buffer
    }

    private data class RawRegion(val rect: RectF, val score: Float)

    private fun findConnectedComponents(
        binaryMap: Array<BooleanArray>,
        probMap: Array<FloatArray>
    ): List<RawRegion> {
        val visited = Array(INPUT_SIZE) { BooleanArray(INPUT_SIZE) }
        val regions = mutableListOf<RawRegion>()

        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                if (!binaryMap[y][x] || visited[y][x]) continue

                var minX = x
                var minY = y
                var maxX = x
                var maxY = y
                var scoreSum = 0f
                var count = 0

                val stack = ArrayDeque<Int>()
                stack.addLast(y * INPUT_SIZE + x)
                visited[y][x] = true

                while (stack.isNotEmpty()) {
                    val pos = stack.removeLast()
                    val py = pos / INPUT_SIZE
                    val px = pos % INPUT_SIZE

                    minX = min(minX, px)
                    minY = min(minY, py)
                    maxX = max(maxX, px)
                    maxY = max(maxY, py)
                    scoreSum += probMap[py][px]
                    count++

                    for (d in DIRECTIONS) {
                        val ny = py + d[0]
                        val nx = px + d[1]
                        if (ny in 0 until INPUT_SIZE && nx in 0 until INPUT_SIZE
                            && binaryMap[ny][nx] && !visited[ny][nx]) {
                            visited[ny][nx] = true
                            stack.addLast(ny * INPUT_SIZE + nx)
                        }
                    }
                }

                if (count < MIN_PIXEL_COUNT) continue

                val avgScore = scoreSum / count
                if (avgScore < CONFIDENCE_THRESHOLD) continue

                val expandX = (maxX - minX) * BOX_EXPAND
                val expandY = (maxY - minY) * BOX_EXPAND
                val rect = RectF(
                    max(0f, minX - expandX),
                    max(0f, minY - expandY),
                    min(INPUT_SIZE.toFloat(), maxX + expandX + 1),
                    min(INPUT_SIZE.toFloat(), maxY + expandY + 1)
                )
                regions.add(RawRegion(rect, avgScore))
            }
        }

        return regions
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        private const val TAG = "AxOcr"
        private const val MODEL_FILE = "text_detector.tflite"
        private const val INPUT_SIZE = 640
        private const val NUM_THREADS = 4
        private const val BINARY_THRESHOLD = 0.3f
        private const val CONFIDENCE_THRESHOLD = 0.6f
        private const val MIN_BOX_SIZE = 10f
        private const val MIN_PIXEL_COUNT = 30
        private const val BOX_EXPAND = 0.1f
        private const val MIN_ASPECT_RATIO = 1.5f
        private const val MAX_AREA_RATIO = 0.15f
        private const val MAX_HEIGHT_RATIO = 0.12f

        private const val MEAN_R = 0.485f
        private const val MEAN_G = 0.456f
        private const val MEAN_B = 0.406f
        private const val STD_R = 0.229f
        private const val STD_G = 0.224f
        private const val STD_B = 0.225f

        private val DIRECTIONS = arrayOf(
            intArrayOf(-1, 0), intArrayOf(1, 0),
            intArrayOf(0, -1), intArrayOf(0, 1)
        )
    }
}

data class DetectedRegion(
    val boundingBox: RectF,
    val confidence: Float,
    val corners: List<PointF>,
    val angle: Float
)
