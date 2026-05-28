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

package com.android.axion.blur.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.ArraySet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.android.axion.blur.AxBlurSupport
import com.android.axion.blur.domain.interactor.AxBackdropBlurInteractor
import com.android.axion.blur.model.AxBackdropBlurSettingsSpec
import com.android.axion.blur.model.AxBackdropBlurSettingsSubscription
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import kotlin.math.max
import kotlin.math.roundToInt

class AxViewBackdropBlur @JvmOverloads constructor(
    private val view: View,
    private val observeSettings: Boolean = true,
) {
    private val path = Path()
    private val rect = RectF()
    private val childRect = RectF()
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetRect = Rect()
    private val targetRectF = RectF()
    private val transformMatrix = Matrix()
    private val scaledCornerRadii = FloatArray(8)
    private val sourceBlurNode = RenderNode("AxViewBackdropBlur")
    private val defaultKey = Any()
    private val drawables = LinkedHashMap<Any, BackgroundBlurDrawable>()
    private val drawableAlphaStates = LinkedHashMap<Any, DrawableAlphaState>()
    private val trackedStates = LinkedHashMap<View, ViewFrameState>()
    private val excludedSourceViews = ArrayList<View>()
    private val excludedSourceBranches = ArrayList<View>()
    private val sourceContentViews = ArraySet<View>()
    private var sourceDrawStopBranch: View? = null
    private var settingsInteractor = AxBackdropBlurInteractor(view.context)
    private var settingsSubscription: AxBackdropBlurSettingsSubscription? = null
    private var observingPreDraw = false
    private var attached = false
    private var enabled = false
    private var useSettingsBlurRadius = true
    private var blurRadiusPx = 0f
    private var alpha = 255
    private var overlayColor = Color.TRANSPARENT
    private var sourceView: View? = null
    private var preferSourceBlur = false
    private var requireSourceBlur = false
    private var forceSourceBlurUpdate = false
    private var sourceBlurUpdateSuppressed = false
    private var crossWindowBlurEnabled = true
    private var sourceBlurRecorded = false
    private var sourceBlurDirty = true
    private var recordedSourceState = SourceRecord()
    private var sourceBlurEffect: RenderEffect? = null
    private var sourceBlurEffectRadius = -1f
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (enabled && hasTrackedStateChanged()) {
            view.postInvalidateOnAnimation()
        }
        true
    }

    init {
        applyBlurSettings()
    }

    fun isEnabled(): Boolean {
        return enabled
    }

    fun isCrossWindowBlurActive(): Boolean {
        return crossWindowBlurEnabled &&
            shouldTrackFrames() &&
            AxBlurSupport.supportsCrossWindowBlur()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) {
            clear()
        } else {
            updatePreDrawObserver()
        }
        updateSettingsObserver()
        view.invalidate()
    }

    fun setAlpha(alpha: Int) {
        val coerced = alpha.coerceIn(0, 255)
        if (this.alpha == coerced) return
        val previous = this.alpha
        this.alpha = coerced
        drawableAlphaStates.entries.forEach { entry ->
            if (entry.value.alpha == previous) {
                entry.setValue(entry.value.copy(alpha = coerced))
            }
        }
        updateDrawableAlphas()
        view.invalidate()
    }

    fun setSurfaceAlpha(alpha: Int) {
        setAlpha(alpha)
    }

    fun setOverlayColor(color: Int) {
        if (overlayColor == color) return
        overlayColor = color
        view.invalidate()
    }

    fun setBlurRadiusPx(radius: Float) {
        useSettingsBlurRadius = false
        updateBlurRadiusPx(radius)
        updateSettingsObserver()
    }

    fun setSourceView(source: View?) {
        if (sourceView === source) return
        val previous = sourceView
        sourceView = source
        if (previous != null && previous !== view) {
            trackedStates.remove(previous)
        }
        if (source != null) {
            trackView(source)
        }
        discardSourceBlur()
        updatePreDrawObserver()
        view.invalidate()
    }

    fun setExcludedSourceViews(vararg excludedViews: View?) {
        var filteredCount = 0
        excludedViews.forEach { if (it != null) filteredCount++ }
        var changed = excludedSourceViews.size != filteredCount
        if (!changed) {
            var index = 0
            excludedViews.forEach { excludedView ->
                if (excludedView != null && excludedSourceViews[index++] !== excludedView) {
                    changed = true
                    return@forEach
                }
            }
        }
        if (!changed) return
        excludedSourceViews.forEach { trackedStates.remove(it) }
        excludedSourceViews.clear()
        excludedViews.forEach { excludedView ->
            if (excludedView != null && !excludedSourceViews.contains(excludedView)) {
                excludedSourceViews.add(excludedView)
                trackView(excludedView)
            }
        }
        discardSourceBlur()
        updatePreDrawObserver()
        view.invalidate()
    }

    fun setPreferSourceBlur(prefer: Boolean) {
        if (preferSourceBlur == prefer) return
        preferSourceBlur = prefer
        view.invalidate()
    }

    fun setRequireSourceBlur(require: Boolean) {
        if (requireSourceBlur == require) return
        requireSourceBlur = require
        view.invalidate()
    }

    fun setForceSourceBlurUpdate(force: Boolean) {
        if (forceSourceBlurUpdate == force) return
        forceSourceBlurUpdate = force
        sourceBlurDirty = true
        view.invalidate()
    }

    fun setSourceBlurUpdateSuppressed(suppressed: Boolean) {
        if (sourceBlurUpdateSuppressed == suppressed) return
        val wasSuppressed = sourceBlurUpdateSuppressed
        sourceBlurUpdateSuppressed = suppressed
        if (wasSuppressed && !suppressed) {
            sourceBlurDirty = true
        }
        view.invalidate()
    }

    fun setCrossWindowBlurEnabled(enabled: Boolean) {
        if (crossWindowBlurEnabled == enabled) return
        crossWindowBlurEnabled = enabled
        if (!enabled) {
            clearCrossWindowBlur()
        }
        view.invalidate()
    }

    fun useSystemBlurRadius() {
        useSettings(AxBackdropBlurSettingsSpec.system())
    }

    fun useSettings(settingsSpec: AxBackdropBlurSettingsSpec) {
        useSettingsBlurRadius = true
        settingsInteractor = AxBackdropBlurInteractor(view.context, settingsSpec)
        applyBlurSettings()
        resetSettingsObserver()
    }

    fun onAttachedToWindow() {
        attached = true
        applyBlurSettings()
        updateSettingsObserver()
        updatePreDrawObserver()
    }

    fun onDetachedFromWindow() {
        attached = false
        stopSettingsObserver()
        sourceView = null
        excludedSourceViews.clear()
        clear()
    }

    fun verifyDrawable(who: Drawable): Boolean {
        return drawables.values.any { it === who }
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        target: View,
        alpha: Int = this.alpha,
    ): Boolean {
        trackView(target)
        if (target.width <= 0 || target.height <= 0) {
            clearKey(target)
            return false
        }
        val bounds = targetBounds(target)
        val scale = targetCornerScale(target, bounds)
        val background = target.background
        val targetAlphaSource = if (target === view) null else target
        if (background is GradientDrawable) {
            val radii = background.cornerRadii
            if (radii != null && radii.size >= 8) {
                val drawRadii = scaledCornerRadii(radii, scale)
                return drawInternal(
                    canvas,
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom,
                    drawRadii,
                    drawRadii.maxCornerRadius(),
                    alpha,
                    target,
                    targetAlphaSource = targetAlphaSource,
                )
            }
            return drawInternal(
                canvas,
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                null,
                background.cornerRadius * scale,
                alpha,
                target,
                targetAlphaSource = targetAlphaSource,
            )
        }
        return drawInternal(
            canvas,
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            null,
            0f,
            alpha,
            target,
            targetAlphaSource = targetAlphaSource,
        )
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: Rect?,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        if (bounds == null) return false
        return draw(canvas, bounds.left, bounds.top, bounds.right, bounds.bottom, cornerRadii, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        bounds: RectF?,
        clipPath: Path?,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        if (bounds == null) return false
        bounds.roundOut(targetRect)
        return drawInternal(
            canvas,
            targetRect.left,
            targetRect.top,
            targetRect.right,
            targetRect.bottom,
            null,
            cornerRadius,
            alpha,
            defaultKey,
            clipPath,
        )
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        val cornerRadius = if (cornerRadii.size >= 8) {
            cornerRadii.maxCornerRadius()
        } else {
            0f
        }
        return drawInternal(canvas, left, top, right, bottom, cornerRadii, cornerRadius, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        return drawInternal(canvas, left, top, right, bottom, null, cornerRadius, alpha)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadius: Float,
        alpha: Int = this.alpha,
    ): Boolean {
        return drawInternal(canvas, left, top, right, bottom, null, cornerRadius, alpha, key)
    }

    @JvmOverloads
    fun draw(
        canvas: Canvas,
        key: Any,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray,
        alpha: Int = this.alpha,
    ): Boolean {
        val cornerRadius = if (cornerRadii.size >= 8) {
            cornerRadii.maxCornerRadius()
        } else {
            0f
        }
        return drawInternal(canvas, left, top, right, bottom, cornerRadii, cornerRadius, alpha, key)
    }

    private fun drawInternal(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        key: Any = defaultKey,
        clipPath: Path? = null,
        applyViewAlpha: Boolean = true,
        targetAlphaSource: View? = null,
    ): Boolean {
        trackView(view)
        if (targetAlphaSource != null) {
            trackView(targetAlphaSource)
        }
        val alphaState = DrawableAlphaState(
            alpha.coerceIn(0, 255),
            applyViewAlpha,
            targetAlphaSource,
        )
        drawableAlphaStates[key] = alphaState
        val drawAlpha = resolvedDrawableAlpha(alphaState)
        if (!canDrawGeometry(canvas, left, top, right, bottom)) {
            clearKey(key)
            return false
        }
        if (drawAlpha <= 0) {
            val drewCrossWindow = drawCrossWindowBlur(
                canvas,
                left,
                top,
                right,
                bottom,
                cornerRadii,
                cornerRadius,
                0,
                key,
                clipPath,
            )
            if (!drewCrossWindow) clearKey(key)
            return drewCrossWindow
        }
        if (preferSourceBlur) {
            val drewCrossWindow = drawCrossWindowBlur(
                canvas,
                left,
                top,
                right,
                bottom,
                cornerRadii,
                cornerRadius,
                drawAlpha,
                key,
                clipPath,
            )
            val drewSource = drawSourceBlur(
                canvas,
                left,
                top,
                right,
                bottom,
                cornerRadii,
                cornerRadius,
                drawAlpha,
                clipPath,
                drawOverlay = !drewCrossWindow,
            )
            if (!drewCrossWindow) clearKey(key)
            if (!drewSource && requireSourceBlur) {
                if (drewCrossWindow) clearKey(key)
                return false
            }
            if (drewSource) return true
            if (drewCrossWindow) {
                return true
            }
            return false
        }
        if (drawCrossWindowBlur(
                canvas,
                left,
                top,
                right,
                bottom,
                cornerRadii,
                cornerRadius,
                drawAlpha,
                key,
                clipPath,
            )
        ) {
            return true
        }
        clearKey(key)
        return drawSourceBlur(
            canvas,
            left,
            top,
            right,
            bottom,
            cornerRadii,
            cornerRadius,
            drawAlpha,
            clipPath,
        )
    }

    private fun drawCrossWindowBlur(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        key: Any,
        clipPath: Path?,
        color: Int = overlayColor,
    ): Boolean {
        if (!crossWindowBlurEnabled) return false
        if (!AxBlurSupport.supportsCrossWindowBlur()) return false
        val blurDrawable = blurDrawableFor(key) ?: return false
        blurDrawable.setVisible(true, false)
        blurDrawable.setBlurRadius(blurRadiusPx.roundToInt())
        blurDrawable.alpha = alpha
        blurDrawable.setColor(color)
        applyCornerRadius(blurDrawable, cornerRadii, cornerRadius)
        blurDrawable.setBounds(left, top, right, bottom)
        val save = canvas.save()
        clip(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        blurDrawable.draw(canvas)
        canvas.restoreToCount(save)
        return true
    }

    private fun drawSourceBlur(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        alpha: Int,
        clipPath: Path?,
        drawOverlay: Boolean = true,
    ): Boolean {
        val source = sourceView ?: return false
        if (
            source === view ||
            view.width <= 0 ||
            view.height <= 0 ||
            source.width <= 0 ||
            source.height <= 0
        ) {
            return false
        }
        if (shouldRecordSource(source) && !recordSource(source)) return false
        sourceBlurNode.setRenderEffect(resolveSourceBlurEffect())
        val save = if (alpha < 255) {
            canvas.saveLayerAlpha(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                alpha,
            )
        } else {
            canvas.save()
        }
        clip(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        canvas.drawRenderNode(sourceBlurNode)
        if (drawOverlay) {
            drawOverlay(canvas, left, top, right, bottom, cornerRadii, cornerRadius, clipPath)
        }
        canvas.restoreToCount(save)
        return true
    }

    private fun shouldRecordSource(source: View): Boolean {
        if (sourceBlurUpdateSuppressed && sourceBlurRecorded) return false
        if (forceSourceBlurUpdate) return true
        return sourceBlurDirty ||
            !sourceBlurRecorded ||
            recordedSourceState != sourceRecordFor(source)
    }

    private fun recordSource(source: View): Boolean {
        val nextRecord = sourceRecordFor(source)
        trackView(source)
        clearSourceContentViews()
        val outset = blurRadiusPx.roundToInt().coerceAtLeast(0)
        val nodeWidth = view.width + outset * 2
        val nodeHeight = view.height + outset * 2
        sourceBlurNode.setPosition(-outset, -outset, view.width + outset, view.height + outset)
        val recordingCanvas = sourceBlurNode.beginRecording(nodeWidth, nodeHeight)
        val save = recordingCanvas.save()
        recordingCanvas.translate(outset.toFloat(), outset.toFloat())
        updateTransformToView(source)
        recordingCanvas.concat(transformMatrix)
        val recorded = drawSource(recordingCanvas, source)
        recordingCanvas.restoreToCount(save)
        sourceBlurNode.endRecording()
        recordedSourceState = nextRecord
        sourceBlurDirty = !recorded
        sourceBlurRecorded = recorded
        if (!recorded) {
            sourceBlurNode.discardDisplayList()
        }
        return recorded
    }

    private fun drawSource(canvas: Canvas, source: View): Boolean {
        if (source is ViewGroup) {
            collectExcludedSourceBranches(source)
            if (excludedSourceBranches.isNotEmpty() || sourceDrawStopBranch != null) {
                drawViewGroupWithoutTargets(canvas, source, excludedSourceBranches)
                return true
            }
        }
        source.draw(canvas)
        return true
    }

    private fun collectExcludedSourceBranches(group: ViewGroup) {
        excludedSourceBranches.clear()
        sourceDrawStopBranch = findSourceBranch(group, view)
        if (sourceDrawStopBranch == null) {
            addExcludedSourceBranch(group, view)
        }
        excludedSourceViews.forEach { addExcludedSourceBranch(group, it) }
    }

    private fun addExcludedSourceBranch(group: ViewGroup, target: View) {
        val branch = findSourceBranch(group, target) ?: return
        if (!excludedSourceBranches.contains(branch)) {
            excludedSourceBranches.add(branch)
        }
    }

    private fun findSourceBranch(group: ViewGroup, target: View): View? {
        var current = target
        while (current.parent is View) {
            val parent = current.parent as View
            if (parent === group) {
                return current
            }
            current = parent
        }
        return null
    }

    private fun drawViewGroupWithoutTargets(
        canvas: Canvas,
        group: ViewGroup,
        targetBranches: List<View>,
    ) {
        drawViewBackground(canvas, group)
        val save = canvas.save()
        canvas.translate(-group.scrollX.toFloat(), -group.scrollY.toFloat())
        if (group.clipToPadding) {
            canvas.clipRect(
                group.scrollX + group.paddingLeft,
                group.scrollY + group.paddingTop,
                group.scrollX + group.width - group.paddingRight,
                group.scrollY + group.height - group.paddingBottom,
            )
        }
        val stopIndex = sourceDrawStopBranch?.let { group.indexOfChild(it) } ?: group.childCount
        val childCount = if (stopIndex >= 0) stopIndex else group.childCount
        for (i in 0 until childCount) {
            val child = group.getChildAt(i)
            if (!targetBranches.contains(child)) {
                drawChild(canvas, child)
            }
        }
        canvas.restoreToCount(save)
    }

    private fun drawViewBackground(canvas: Canvas, source: View) {
        val background = source.background ?: return
        val save = canvas.save()
        canvas.translate(source.scrollX.toFloat(), source.scrollY.toFloat())
        background.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawChild(canvas: Canvas, child: View) {
        trackSourceContentView(child)
        val childAlpha = child.visualAlpha()
        if ((child.visibility != View.VISIBLE && child.animation == null) || childAlpha <= 0f) return
        if (drawChildRenderNode(canvas, child)) return
        drawChildFallback(canvas, child, childAlpha)
    }

    private fun drawChildRenderNode(canvas: Canvas, child: View): Boolean {
        if (!canvas.isHardwareAccelerated || !child.canHaveDisplayList()) return false
        val renderNode = child.updateDisplayListIfDirty()
        if (!renderNode.hasDisplayList()) return false
        canvas.drawRenderNode(renderNode)
        return true
    }

    private fun drawChildFallback(
        canvas: Canvas,
        child: View,
        childAlpha: Float,
    ) {
        val left = child.left.toFloat()
        val top = child.top.toFloat()
        val matrix = child.matrix
        val save = if (childAlpha < 1f) {
            childRect.set(0f, 0f, child.width.toFloat(), child.height.toFloat())
            if (!matrix.isIdentity) {
                matrix.mapRect(childRect)
            }
            childRect.offset(left, top)
            canvas.saveLayerAlpha(
                childRect.left,
                childRect.top,
                childRect.right,
                childRect.bottom,
                (childAlpha * 255).roundToInt().coerceIn(0, 255),
            )
        } else {
            canvas.save()
        }
        canvas.translate(left, top)
        if (!matrix.isIdentity) {
            canvas.concat(matrix)
        }
        child.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun resolveSourceBlurEffect(): RenderEffect {
        val cached = sourceBlurEffect
        if (cached != null && sourceBlurEffectRadius == blurRadiusPx) {
            return cached
        }
        return RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP,
        ).also {
            sourceBlurEffect = it
            sourceBlurEffectRadius = blurRadiusPx
        }
    }

    private fun targetBounds(target: View): Rect {
        targetRectF.set(0f, 0f, target.width.toFloat(), target.height.toFloat())
        if (target !== view) {
            updateTransformToView(target)
            transformMatrix.mapRect(targetRectF)
        }
        targetRectF.roundOut(targetRect)
        return targetRect
    }

    private fun targetCornerScale(target: View, bounds: Rect): Float {
        if (target === view || target.width <= 0 || target.height <= 0) return 1f
        val scaleX = bounds.width().toFloat() / target.width
        val scaleY = bounds.height().toFloat() / target.height
        val scale = max(scaleX, scaleY)
        return if (scale.isFinite() && scale > 0f) scale else 1f
    }

    private fun scaledCornerRadii(radii: FloatArray, scale: Float): FloatArray {
        if (scale == 1f) return radii
        for (index in 0 until 8) {
            scaledCornerRadii[index] = radii[index] * scale
        }
        return scaledCornerRadii
    }

    private fun updateTransformToView(source: View) {
        transformMatrix.reset()
        source.transformMatrixToGlobal(transformMatrix)
        view.transformMatrixToLocal(transformMatrix)
    }

    private fun canDrawGeometry(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): Boolean {
        return enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurRadiusPx.roundToInt() > 0 &&
            canvas.isHardwareAccelerated &&
            left < right &&
            top < bottom
    }

    private fun viewTreeAlpha(alpha: Int): Int {
        var drawAlpha = alpha.coerceIn(0, 255)
        var current: View? = view
        while (current != null) {
            if (current.visibility != View.VISIBLE) return 0
            drawAlpha = (drawAlpha * current.visualAlpha()).roundToInt().coerceIn(0, 255)
            current = current.parent as? View
        }
        drawAlpha = (drawAlpha * windowAlpha()).roundToInt().coerceIn(0, 255)
        return drawAlpha
    }

    private fun resolvedDrawableAlpha(state: DrawableAlphaState): Int {
        var drawAlpha = state.alpha
        val target = state.target
        if (target != null) {
            drawAlpha = (drawAlpha * targetLocalAlpha(target)).roundToInt().coerceIn(0, 255)
        }
        return if (state.applyViewAlpha) viewTreeAlpha(drawAlpha) else drawAlpha
    }

    private fun updateDrawableAlphas() {
        drawables.forEach { (key, drawable) ->
            val state = drawableAlphaStates[key] ?: return@forEach
            drawable.alpha = resolvedDrawableAlpha(state)
        }
    }

    private fun targetLocalAlpha(target: View): Float {
        var alpha = 1f
        var current: View? = target
        while (current != null) {
            if (isHostAncestor(current)) return alpha
            if (current.visibility != View.VISIBLE) return 0f
            alpha *= current.visualAlpha()
            current = current.parent as? View
        }
        return alpha
    }

    private fun isHostAncestor(target: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === target) return true
            current = current.parent as? View
        }
        return false
    }

    private fun View.visualAlpha(): Float {
        val value = alpha * transitionAlpha
        return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    private fun windowAlpha(): Float {
        val value = view.viewRootImpl?.mWindowAttributes?.alpha ?: 1f
        return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }

    private fun trackView(target: View) {
        if (!shouldTrackFrames()) return
        trackedStates.getOrPut(target) { ViewFrameState() }
            .update(target, transformMatrix, targetRectF, targetRect, false)
        updatePreDrawObserver()
    }

    private fun trackSourceContentView(target: View) {
        sourceContentViews.add(target)
        trackView(target)
    }

    private fun shouldTrackFrames(): Boolean {
        return enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurRadiusPx.roundToInt() > 0
    }

    private fun hasTrackedStateChanged(): Boolean {
        var changed = false
        val iterator = trackedStates.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val target = entry.key
            if (target !== view && !target.isAttachedToWindow) {
                if (target === sourceView || sourceContentViews.remove(target)) {
                    sourceBlurDirty = true
                }
                clearKey(target)
                iterator.remove()
                changed = true
            } else {
                val sourceContentChanged = sourceContentViews.contains(target)
                val affectsSource = target === view || target === sourceView || sourceContentChanged
                val trackSourceDirty = target === sourceView || sourceContentChanged
                val stateChanged = entry.value.update(
                    target,
                    transformMatrix,
                    targetRectF,
                    targetRect,
                    trackSourceDirty,
                )
                if (!entry.value.isVisibleInWindow()) {
                    val cleared = if (target === view) {
                        val hadDrawables = drawables.isNotEmpty()
                        clearCrossWindowBlur()
                        hadDrawables
                    } else {
                        val hadDrawable = drawables.containsKey(target)
                        clearKey(target)
                        hadDrawable
                    }
                    changed = changed || cleared
                }
                if (stateChanged && affectsSource) {
                    sourceBlurDirty = true
                }
                changed = changed || stateChanged
            }
        }
        if (trackedStates.isEmpty()) {
            removePreDrawObserver()
        }
        if (changed) {
            updateDrawableAlphas()
        }
        return changed
    }

    private fun updatePreDrawObserver() {
        if (attached && enabled && trackedStates.isNotEmpty()) {
            addPreDrawObserver()
        } else {
            removePreDrawObserver()
        }
    }

    private fun addPreDrawObserver() {
        if (!observingPreDraw) {
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            observingPreDraw = true
        }
    }

    private fun removePreDrawObserver() {
        if (observingPreDraw) {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
            }
            observingPreDraw = false
        }
    }

    private fun blurDrawableFor(key: Any): BackgroundBlurDrawable? {
        return drawables[key] ?: view.viewRootImpl
            ?.createBackgroundBlurDrawable()
            ?.also { drawables[key] = it }
    }

    private fun applyCornerRadius(
        blurDrawable: BackgroundBlurDrawable,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
    ) {
        if (cornerRadii != null && cornerRadii.size >= 8) {
            blurDrawable.setCornerRadius(
                cornerRadii.cornerRadiusAt(0),
                cornerRadii.cornerRadiusAt(2),
                cornerRadii.cornerRadiusAt(6),
                cornerRadii.cornerRadiusAt(4),
            )
            return
        }
        blurDrawable.setCornerRadius(cornerRadius.coerceAtLeast(0f))
    }

    private fun clip(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        clipPath: Path?,
    ) {
        if (clipPath != null) {
            canvas.clipPath(clipPath)
            return
        }
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.rewind()
        if (cornerRadii != null && cornerRadii.size >= 8) {
            path.addRoundRect(rect, cornerRadii, Path.Direction.CW)
            canvas.clipPath(path)
        } else if (cornerRadius > 0f) {
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(path)
        }
    }

    private fun drawOverlay(
        canvas: Canvas,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        cornerRadii: FloatArray?,
        cornerRadius: Float,
        clipPath: Path?,
        alpha: Int = 255,
    ) {
        if (Color.alpha(overlayColor) == 0) return
        overlayPaint.color = overlayColor
        overlayPaint.alpha = (Color.alpha(overlayColor) * alpha / 255f)
            .roundToInt()
            .coerceIn(0, 255)
        if (clipPath != null) {
            canvas.drawPath(clipPath, overlayPaint)
            return
        }
        rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        path.rewind()
        if (cornerRadii != null && cornerRadii.size >= 8) {
            path.addRoundRect(rect, cornerRadii, Path.Direction.CW)
            canvas.drawPath(path, overlayPaint)
        } else if (cornerRadius > 0f) {
            path.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.drawPath(path, overlayPaint)
        } else {
            canvas.drawRect(rect, overlayPaint)
        }
    }

    private fun applyBlurSettings() {
        if (useSettingsBlurRadius) {
            updateBlurRadiusPx(settingsInteractor.settings().blurRadiusPx)
        }
    }

    private fun updateBlurRadiusPx(radius: Float) {
        val coerced = if (radius.isFinite()) radius.coerceAtLeast(0f) else 0f
        if (blurRadiusPx == coerced) return
        blurRadiusPx = coerced
        sourceBlurEffect = null
        discardSourceBlur()
        if (coerced == 0f) clear()
        view.invalidate()
    }

    private fun updateSettingsObserver() {
        if (observeSettings && attached && enabled && useSettingsBlurRadius) {
            startSettingsObserver()
        } else {
            stopSettingsObserver()
        }
    }

    private fun resetSettingsObserver() {
        settingsSubscription?.stop()
        settingsSubscription = null
        updateSettingsObserver()
    }

    private fun startSettingsObserver() {
        val observer = settingsSubscription ?: settingsInteractor.createSubscription {
            applyBlurSettings()
        }.also {
            settingsSubscription = it
        }
        observer.start()
    }

    private fun stopSettingsObserver() {
        settingsSubscription?.stop()
    }

    fun clear() {
        clearCrossWindowBlur()
        trackedStates.clear()
        discardSourceBlur()
        updatePreDrawObserver()
    }

    fun clearCrossWindowBlur() {
        drawables.values.forEach {
            it.setVisible(false, false)
            it.setBlurRadius(0)
        }
        drawables.clear()
        drawableAlphaStates.clear()
        updatePreDrawObserver()
    }

    fun refreshSourceBlur() {
        discardSourceBlur()
        view.invalidate()
    }

    fun clear(target: View?) {
        if (target != null) {
            clearKey(target)
            if (target === sourceView || sourceContentViews.remove(target)) {
                discardSourceBlur()
            }
            trackedStates.remove(target)
            updatePreDrawObserver()
        }
    }

    private fun clearKey(key: Any) {
        drawableAlphaStates.remove(key)
        drawables.remove(key)?.let {
            it.setVisible(false, false)
            it.setBlurRadius(0)
        }
    }

    private fun discardSourceBlur() {
        clearSourceContentViews()
        sourceBlurNode.discardDisplayList()
        sourceBlurRecorded = false
        sourceBlurDirty = true
        recordedSourceState = SourceRecord()
    }

    private fun sourceRecordFor(source: View): SourceRecord {
        return SourceRecord(
            viewWidth = view.width,
            viewHeight = view.height,
            sourceWidth = source.width,
            sourceHeight = source.height,
        )
    }

    private fun clearSourceContentViews() {
        sourceContentViews.forEach { trackedStates.remove(it) }
        sourceContentViews.clear()
    }

    private fun FloatArray.cornerRadiusAt(index: Int): Float {
        return (this[index] + this[index + 1]) * 0.5f
    }

    private fun FloatArray.maxCornerRadius(): Float {
        var radius = 0f
        for (index in 0..6 step 2) {
            radius = max(radius, cornerRadiusAt(index))
        }
        return radius
    }

    private data class DrawableAlphaState(
        val alpha: Int,
        val applyViewAlpha: Boolean,
        val target: View?,
    )

    private data class SourceRecord(
        val viewWidth: Int = -1,
        val viewHeight: Int = -1,
        val sourceWidth: Int = -1,
        val sourceHeight: Int = -1,
    )

    private inner class ViewFrameState {
        private var transformState = ViewFrameTransformState()
        private var visibilityState = ViewFrameVisibilityState()
        private var clipState = ViewFrameClipState()

        fun isVisibleInWindow(): Boolean {
            return visibilityState.visibleInWindow
        }

        fun update(
            target: View,
            matrix: Matrix,
            rect: RectF,
            visibleRect: Rect,
            trackDirty: Boolean,
        ): Boolean {
            rect.set(0f, 0f, target.width.toFloat(), target.height.toFloat())
            matrix.reset()
            target.transformMatrixToGlobal(matrix)
            matrix.mapRect(rect)
            val targetVisibleInWindow = target.getGlobalVisibleRect(visibleRect)
            val targetScrollX = target.scrollX
            val targetScrollY = target.scrollY
            val targetChildCount = if (target is ViewGroup) target.childCount else -1
            val targetDirty = trackDirty && target.isDirty
            val targetVisibilityState = ViewFrameVisibilityState(
                visibleInWindow = targetVisibleInWindow,
                left = if (targetVisibleInWindow) visibleRect.left else Int.MIN_VALUE,
                top = if (targetVisibleInWindow) visibleRect.top else Int.MIN_VALUE,
                right = if (targetVisibleInWindow) visibleRect.right else Int.MIN_VALUE,
                bottom = if (targetVisibleInWindow) visibleRect.bottom else Int.MIN_VALUE,
            )
            val targetClipSet = target.getClipBounds(visibleRect)
            val targetClipState = ViewFrameClipState(
                isSet = targetClipSet,
                left = if (targetClipSet) visibleRect.left else Int.MIN_VALUE,
                top = if (targetClipSet) visibleRect.top else Int.MIN_VALUE,
                right = if (targetClipSet) visibleRect.right else Int.MIN_VALUE,
                bottom = if (targetClipSet) visibleRect.bottom else Int.MIN_VALUE,
            )
            var treeAlpha = 1f
            var current: View? = target
            while (current != null) {
                if (current.visibility != View.VISIBLE) {
                    treeAlpha = 0f
                    break
                }
                treeAlpha *= current.visualAlpha()
                current = current.parent as? View
            }
            treeAlpha *= windowAlpha()
            val targetTransformState = ViewFrameTransformState(
                width = target.width,
                height = target.height,
                alpha = treeAlpha,
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
                scrollX = targetScrollX,
                scrollY = targetScrollY,
                childCount = targetChildCount,
            )
            val changed = transformState != targetTransformState ||
                visibilityState != targetVisibilityState ||
                clipState != targetClipState
            transformState = targetTransformState
            visibilityState = targetVisibilityState
            clipState = targetClipState
            return changed || targetDirty
        }
    }

    private data class ViewFrameVisibilityState(
        val visibleInWindow: Boolean = false,
        val left: Int = Int.MIN_VALUE,
        val top: Int = Int.MIN_VALUE,
        val right: Int = Int.MIN_VALUE,
        val bottom: Int = Int.MIN_VALUE,
    )

    private data class ViewFrameClipState(
        val isSet: Boolean = false,
        val left: Int = Int.MIN_VALUE,
        val top: Int = Int.MIN_VALUE,
        val right: Int = Int.MIN_VALUE,
        val bottom: Int = Int.MIN_VALUE,
    )

    private data class ViewFrameTransformState(
        val width: Int = -1,
        val height: Int = -1,
        val alpha: Float = Float.NaN,
        val left: Float = Float.NaN,
        val top: Float = Float.NaN,
        val right: Float = Float.NaN,
        val bottom: Float = Float.NaN,
        val scrollX: Int = Int.MIN_VALUE,
        val scrollY: Int = Int.MIN_VALUE,
        val childCount: Int = -1,
    )
}
