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

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSpec
import com.android.axion.blur.ui.view.AxViewBackdropBlur
import kotlin.math.min

class AxBlurBackgroundRenderer @JvmOverloads constructor(
    private val view: View,
    settingsSpec: AxBackdropBlurSettingsSpec = AxBackdropBlurSettingsSpec.system(),
    enabled: Boolean = true,
) {
    private val blur = AxViewBackdropBlur(view).apply {
        useSettings(settingsSpec)
        setEnabled(enabled)
    }
    private var surfaceAlpha = 255

    fun onAttachedToWindow() {
        blur.onAttachedToWindow()
    }

    fun onDetachedFromWindow() {
        blur.onDetachedFromWindow()
    }

    fun onVisibilityAggregated(isVisible: Boolean) {
        if (isVisible) view.invalidate()
    }

    fun setEnabled(enabled: Boolean) {
        blur.setEnabled(enabled)
    }

    fun isCrossWindowBlurActive(): Boolean {
        return blur.isCrossWindowBlurActive()
    }

    fun setSettings(settingsSpec: AxBackdropBlurSettingsSpec) {
        blur.useSettings(settingsSpec)
    }

    fun setSourceView(source: View?) {
        blur.setSourceView(source)
    }

    fun setExcludedSourceViews(vararg excludedViews: View?) {
        blur.setExcludedSourceViews(*excludedViews)
    }

    fun setPreferSourceBlur(prefer: Boolean) {
        blur.setPreferSourceBlur(prefer)
    }

    fun setCrossWindowBlurEnabled(enabled: Boolean) {
        blur.setCrossWindowBlurEnabled(enabled)
    }

    fun setSurfaceAlpha(alpha: Int) {
        surfaceAlpha = alpha.coerceIn(0, 255)
        blur.setSurfaceAlpha(surfaceAlpha)
    }

    fun setOverlayColor(color: Int) {
        blur.setOverlayColor(color)
    }

    fun verifyDrawable(who: Drawable): Boolean {
        return blur.verifyDrawable(who)
    }

    fun clear() {
        blur.clear()
    }

    fun clearCrossWindowBlur() {
        blur.clearCrossWindowBlur()
    }

    fun clear(target: View?) {
        blur.clear(target)
    }

    fun createBackgroundDrawable(background: GradientDrawable, overlayColor: Int): GradientDrawable {
        return BlurGradientDrawable(this, background, overlayColor)
    }

    @JvmOverloads
    fun drawBackground(
        canvas: Canvas,
        background: Drawable?,
        fallbackColor: Int = Color.TRANSPARENT,
    ): Boolean {
        val current = background?.current
        return drawResolvedBackground(
            canvas,
            background,
            if (current != null) backgroundColor(current, fallbackColor) else fallbackColor,
        )
    }

    fun drawBackgroundWithOverlayColor(
        canvas: Canvas,
        background: Drawable?,
        overlayColor: Int,
    ): Boolean {
        return drawResolvedBackground(canvas, background, overlayColor)
    }

    private fun drawResolvedBackground(
        canvas: Canvas,
        background: Drawable?,
        overlayColor: Int,
    ): Boolean {
        if (background == null || view.width <= 0 || view.height <= 0) {
            return false
        }
        val current = background.current
        blur.setOverlayColor(overlayColor)
        if (current is GradientDrawable) {
            val cornerRadii = current.cornerRadii
            if (cornerRadii != null && cornerRadii.size >= 8) {
                return blur.draw(canvas, 0, 0, view.width, view.height, cornerRadii)
            }
            return blur.draw(canvas, 0, 0, view.width, view.height, cornerRadius(current))
        }
        return blur.draw(canvas, 0, 0, view.width, view.height, 0f)
    }

    @JvmOverloads
    fun draw(canvas: Canvas, target: View, overlayColor: Int, alpha: Int = surfaceAlpha): Boolean {
        blur.setOverlayColor(overlayColor)
        return blur.draw(canvas, target, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        overlayColor: Int,
        alpha: Int = surfaceAlpha,
    ): Boolean {
        blur.setOverlayColor(overlayColor)
        return blur.draw(canvas, left, top, right, bottom, cornerRadius, alpha)
    }

    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        overlayColor: Int,
        alpha: Int,
    ): Boolean {
        blur.setOverlayColor(overlayColor)
        return blur.draw(canvas, key, left, top, right, bottom, cornerRadius, alpha)
    }

    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray,
        overlayColor: Int,
        alpha: Int,
    ): Boolean {
        blur.setOverlayColor(overlayColor)
        return blur.draw(canvas, key, left, top, right, bottom, cornerRadii, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: RectF?,
        clipPath: Path?,
        cornerRadius: Float,
        overlayColor: Int,
        alpha: Int = surfaceAlpha,
    ): Boolean {
        blur.setOverlayColor(overlayColor)
        return blur.draw(canvas, bounds, clipPath, cornerRadius, alpha)
    }

    private fun cornerRadius(background: GradientDrawable): Float {
        if (background.shape == GradientDrawable.OVAL) {
            return min(view.width, view.height) * 0.5f
        }
        return background.cornerRadius
    }

    private fun backgroundColor(background: Drawable, fallbackColor: Int): Int {
        val tint = view.backgroundTintList
        if (tint != null) {
            return tint.getColorForState(view.drawableState, tint.defaultColor)
        }
        if (background is GradientDrawable) {
            val color = background.color
            if (color != null) {
                return color.getColorForState(view.drawableState, color.defaultColor)
            }
        }
        if (background is ColorDrawable) {
            return background.color
        }
        return fallbackColor
    }

    companion object {
        @JvmStatic
        fun launcher(view: View, defaultRadiusPx: Float): AxBlurBackgroundRenderer {
            return AxBlurBackgroundRenderer(
                view,
                AxBackdropBlurSettingsSpec.launcher(defaultRadiusPx),
            )
        }
    }

    private class BlurGradientDrawable(
        private val renderer: AxBlurBackgroundRenderer,
        source: GradientDrawable,
        private val overlayColor: Int,
    ) : GradientDrawable() {
        private val key = Any()

        init {
            setShape(source.shape)
            val color = source.color
            if (color != null) {
                setColor(color)
            }
            val radii = source.cornerRadii
            if (radii != null) {
                setCornerRadii(radii)
            } else {
                setCornerRadius(source.cornerRadius)
            }
            setAlpha(source.alpha)
        }

        override fun draw(canvas: Canvas) {
            val drawBounds = getBounds()
            val drawAlpha = getAlpha()
            if (drawBounds.isEmpty) return
            val radii = cornerRadii
            val drewBlur = if (radii != null && radii.size >= 8) {
                renderer.draw(
                    canvas,
                    key,
                    drawBounds.left,
                    drawBounds.top,
                    drawBounds.right,
                    drawBounds.bottom,
                    radii,
                    overlayColor,
                    drawAlpha,
                )
            } else {
                renderer.draw(
                    canvas,
                    key,
                    drawBounds.left,
                    drawBounds.top,
                    drawBounds.right,
                    drawBounds.bottom,
                    cornerRadius,
                    overlayColor,
                    drawAlpha,
                )
            }
            if (!drewBlur && drawAlpha > 0 && !renderer.isCrossWindowBlurActive()) {
                super.draw(canvas)
            }
        }
    }
}
