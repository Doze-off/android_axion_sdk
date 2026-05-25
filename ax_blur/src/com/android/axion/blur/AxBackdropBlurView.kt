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
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.android.axion.blur.domain.interactor.AxBackdropBlurInteractor
import com.android.axion.blur.ui.view.AxViewBackdropBlur
import kotlin.math.roundToInt

open class AxBackdropBlurSourceLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val contentNode = RenderNode("AxBackdropBlurSource")
    private var hasRecordedContent = false
    private val recordedListeners = mutableSetOf<Runnable>()

    val backdropRenderNode: RenderNode?
        get() = contentNode.takeIf { hasRecordedContent && it.hasDisplayList() }

    fun addOnBackdropRecordedListener(listener: Runnable) {
        recordedListeners += listener
    }

    fun removeOnBackdropRecordedListener(listener: Runnable) {
        recordedListeners -= listener
    }

    override fun draw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated && width > 0 && height > 0) {
            contentNode.setPosition(0, 0, width, height)
            val recordingCanvas = contentNode.beginRecording(width, height)
            super.draw(recordingCanvas)
            contentNode.endRecording()
            hasRecordedContent = true
            canvas.drawRenderNode(contentNode)
            recordedListeners.forEach { it.run() }
        } else {
            hasRecordedContent = false
            super.draw(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        contentNode.discardDisplayList()
        hasRecordedContent = false
        super.onDetachedFromWindow()
    }
}

class AxBackdropBlurSourceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AxBackdropBlurSourceLayout(context, attrs, defStyleAttr)

class AxBackdropBlurRenderer @JvmOverloads constructor(
    private val view: View,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) {
    private val blurNode = RenderNode("AxBackdropBlur")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val childRect = RectF()
    private val nativeBackdropBlur = AxViewBackdropBlur(view, observeSettings = false).apply {
        setEnabled(true)
    }
    private val transformMatrix = Matrix()
    private var blurEffect: AndroidRenderEffect? = null
    private var blurEffectRadius = -1f
    private var observedSource: AxBackdropBlurSourceLayout? = null
    private var observingPreDraw = false
    private var useSettingsBlurRadius = true
    private var useSettingsTint = true
    private var useSettingsFallback = true
    private var enabled = true
    private var preferSourceBlur = false
    private var storedBlurRadiusPx = 0f
    private var storedBackdropTintColor = AndroidColor.TRANSPARENT
    private var storedFallbackColor = AndroidColor.TRANSPARENT
    private val blurInteractor = AxBackdropBlurInteractor(view.context)
    private val settingsObserver = blurInteractor.createSubscription {
        applySystemBlurSettings()
    }
    private val sourceRecordedListener = Runnable { view.postInvalidateOnAnimation() }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (invalidateOnPreDraw) view.postInvalidateOnAnimation()
        true
    }

    var blurRadiusPx: Float
        get() = storedBlurRadiusPx
        set(value) {
            useSettingsBlurRadius = false
            updateBlurRadiusPx(value)
        }

    var cornerRadiusPx: Float = 0f
        set(value) {
            val coerced = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
            if (field != coerced) {
                field = coerced
                view.invalidate()
            }
        }

    var backdropTintColor: Int
        get() = storedBackdropTintColor
        set(value) {
            useSettingsTint = false
            updateBackdropTintColor(value)
        }

    var fallbackColor: Int
        get() = storedFallbackColor
        set(value) {
            useSettingsFallback = false
            updateFallbackColor(value)
        }

    var sourceId: Int = View.NO_ID
        set(value) {
            if (field != value) {
                field = value
                sourceView = null
                resolveSource()
                updateSourceObserver()
                view.invalidate()
            }
        }

    var sourceView: View? = null
        set(value) {
            if (field !== value) {
                removeSourceObserver()
                field = value
                updateSourceObserver()
                view.invalidate()
            }
        }

    var invalidateOnPreDraw: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updatePreDrawObserver()
            }
        }

    init {
        applyAttributes(attrs, defStyleAttr)
        applySystemBlurSettings()
    }

    fun onAttachedToWindow() {
        nativeBackdropBlur.onAttachedToWindow()
        settingsObserver.start()
        applySystemBlurSettings()
        resolveSource()
        updateSourceObserver()
        updatePreDrawObserver()
    }

    fun onDetachedFromWindow() {
        settingsObserver.stop()
        removePreDrawObserver()
        removeSourceObserver()
        nativeBackdropBlur.onDetachedFromWindow()
        blurNode.discardDisplayList()
    }

    fun draw(canvas: Canvas) {
        drawBackdrop(canvas)
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) clear()
        view.invalidate()
    }

    fun clear() {
        nativeBackdropBlur.clear()
        blurNode.discardDisplayList()
    }

    fun setPreferSourceBlur(prefer: Boolean) {
        if (preferSourceBlur == prefer) return
        preferSourceBlur = prefer
        view.invalidate()
    }

    fun setBlurRadiusDp(radius: Float) {
        blurRadiusPx = radius * view.resources.displayMetrics.density
    }

    private fun applyAttributes(attrs: AttributeSet?, defStyleAttr: Int) {
        val array = view.context.obtainStyledAttributes(
            attrs,
            R.styleable.AxBackdropBlurLayout,
            defStyleAttr,
            0,
        )
        try {
            if (array.hasValue(R.styleable.AxBackdropBlurLayout_axBackdropBlurRadius)) {
                blurRadiusPx = array.getDimension(
                    R.styleable.AxBackdropBlurLayout_axBackdropBlurRadius,
                    AxBackdropBlurInteractor.maxBlurRadiusPx(view.context),
                )
            }
            cornerRadiusPx = array.getDimension(
                R.styleable.AxBackdropBlurLayout_axBackdropBlurCornerRadius,
                0f,
            )
            if (array.hasValue(R.styleable.AxBackdropBlurLayout_axBackdropBlurTint)) {
                backdropTintColor = array.getColor(
                    R.styleable.AxBackdropBlurLayout_axBackdropBlurTint,
                    AxBlurColors.tint(view.context),
                )
            }
            if (array.hasValue(R.styleable.AxBackdropBlurLayout_axBackdropBlurFallbackColor)) {
                fallbackColor = array.getColor(
                    R.styleable.AxBackdropBlurLayout_axBackdropBlurFallbackColor,
                    AxBlurColors.fallback(view.context),
                )
            }
            sourceId = array.getResourceId(
                R.styleable.AxBackdropBlurLayout_axBackdropBlurSource,
                View.NO_ID,
            )
            invalidateOnPreDraw = array.getBoolean(
                R.styleable.AxBackdropBlurLayout_axBackdropBlurInvalidateOnPreDraw,
                false,
            )
        } finally {
            array.recycle()
        }
    }

    private fun applySystemBlurSettings() {
        val settings = blurInteractor.settings()
        val tint = AxBlurColors.tint(view.context)
        if (useSettingsBlurRadius) updateBlurRadiusPx(settings.blurRadiusPx)
        if (useSettingsTint) updateBackdropTintColor(tint)
        if (useSettingsFallback) {
            updateFallbackColor(
                if (settings.enabled) tint else AxBlurColors.fallback(view.context),
            )
        }
    }

    private fun updateBlurRadiusPx(value: Float) {
        val coerced = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
        if (storedBlurRadiusPx != coerced) {
            storedBlurRadiusPx = coerced
            blurEffect = null
            view.invalidate()
        }
    }

    private fun updateBackdropTintColor(value: Int) {
        if (storedBackdropTintColor != value) {
            storedBackdropTintColor = value
            view.invalidate()
        }
    }

    private fun updateFallbackColor(value: Int) {
        if (storedFallbackColor != value) {
            storedFallbackColor = value
            view.invalidate()
        }
    }

    private fun drawBackdrop(canvas: Canvas) {
        if (!enabled) {
            clear()
            return
        }
        if (!shouldDraw()) return

        if (preferSourceBlur) {
            val drewCrossWindow = drawCrossWindowBlur(canvas)
            val drewSource = drawSourceBlur(canvas)
            if (drewSource) return
            if (drewCrossWindow) {
                drawColor(canvas, backdropTintColor)
                return
            }
        }

        if (drawCrossWindowBlur(canvas)) {
            drawColor(canvas, backdropTintColor)
            return
        }

        if (drawSourceBlur(canvas)) return
        drawColor(canvas, fallbackColor)
    }

    private fun drawSourceBlur(canvas: Canvas): Boolean {
        if (!canDrawNativeBlur(canvas)) return false
        val source = resolveDrawSource() ?: return false
        if (!recordSource(source)) return false
        blurNode.setRenderEffect(resolveBlurEffect())
        withClip(canvas) {
            canvas.drawRenderNode(blurNode)
            drawColorUnclipped(canvas, backdropTintColor)
        }
        return true
    }

    private fun drawCrossWindowBlur(canvas: Canvas): Boolean {
        nativeBackdropBlur.setBlurRadiusPx(blurRadiusPx)
        return nativeBackdropBlur.draw(
            canvas,
            0,
            0,
            view.width,
            view.height,
            cornerRadiusPx,
        )
    }

    private fun canDrawNativeBlur(canvas: Canvas): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            blurRadiusPx.roundToInt() > 0 &&
            canvas.isHardwareAccelerated &&
            view.width > 0 &&
            view.height > 0
    }

    private fun shouldDraw(): Boolean {
        return view.visibility == View.VISIBLE &&
            view.width > 0 &&
            view.height > 0
    }

    private fun resolveDrawSource(): View? {
        resolveSource()
        return sourceView ?: view.parent as? View
    }

    private fun recordSource(source: View): Boolean {
        if (source === view || source.width <= 0 || source.height <= 0) return false

        blurNode.setPosition(0, 0, view.width, view.height)
        val recordingCanvas = blurNode.beginRecording(view.width, view.height)
        val save = recordingCanvas.save()
        transformMatrix.reset()
        source.transformMatrixToGlobal(transformMatrix)
        view.transformMatrixToLocal(transformMatrix)
        recordingCanvas.concat(transformMatrix)
        val recorded = drawSource(recordingCanvas, source)
        recordingCanvas.restoreToCount(save)
        blurNode.endRecording()
        return recorded
    }

    private fun drawSource(canvas: Canvas, source: View): Boolean {
        val sourceNode = (source as? AxBackdropBlurSourceLayout)?.backdropRenderNode
        if (sourceNode != null) {
            canvas.drawRenderNode(sourceNode)
            return true
        }
        if (source is ViewGroup) {
            val targetBranch = findTargetBranch(source, view)
            if (targetBranch != null) {
                drawViewGroupWithoutTarget(canvas, source, targetBranch)
                return true
            }
        }
        source.draw(canvas)
        return true
    }

    private fun findTargetBranch(group: ViewGroup, target: View): View? {
        var current = target
        while (current.parent is View) {
            val parent = current.parent as View
            if (parent === group) return current
            current = parent
        }
        return null
    }

    private fun drawViewGroupWithoutTarget(canvas: Canvas, group: ViewGroup, targetBranch: View) {
        drawViewBackground(canvas, group)
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child !== targetBranch) {
                drawChild(canvas, group, child)
            }
        }
    }

    private fun drawViewBackground(canvas: Canvas, source: View) {
        val background = source.background ?: return
        val save = canvas.save()
        canvas.translate(source.scrollX.toFloat(), source.scrollY.toFloat())
        background.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawChild(canvas: Canvas, parent: ViewGroup, child: View) {
        if (child.visibility != View.VISIBLE || child.alpha <= 0f) return
        val left = (child.left - parent.scrollX).toFloat()
        val top = (child.top - parent.scrollY).toFloat()
        val matrix = child.matrix
        val save = if (child.alpha < 1f) {
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
                (child.alpha * 255).roundToInt().coerceIn(0, 255),
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

    private fun resolveBlurEffect(): AndroidRenderEffect {
        val cached = blurEffect
        if (cached != null && blurEffectRadius == blurRadiusPx) {
            return cached
        }
        return AndroidRenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.CLAMP,
        ).also {
            blurEffect = it
            blurEffectRadius = blurRadiusPx
        }
    }

    private fun drawColor(canvas: Canvas, color: Int) {
        withClip(canvas) { drawColorUnclipped(canvas, color) }
    }

    private fun drawColorUnclipped(canvas: Canvas, color: Int) {
        if (AndroidColor.alpha(color) > 0) {
            paint.color = color
            canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), paint)
        }
    }

    private inline fun withClip(canvas: Canvas, block: () -> Unit) {
        val save = canvas.save()
        if (cornerRadiusPx > 0f) {
            rect.set(0f, 0f, view.width.toFloat(), view.height.toFloat())
            path.rewind()
            path.addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
            canvas.clipPath(path)
        }
        block()
        canvas.restoreToCount(save)
    }

    private fun resolveSource() {
        if (sourceView == null && sourceId != View.NO_ID) {
            sourceView = view.rootView?.findViewById(sourceId)
        }
    }

    private fun updatePreDrawObserver() {
        if (invalidateOnPreDraw && view.isAttachedToWindow) {
            addPreDrawObserver()
        } else {
            removePreDrawObserver()
        }
    }

    private fun updateSourceObserver() {
        if (!view.isAttachedToWindow) {
            removeSourceObserver()
            return
        }
        val source = sourceView as? AxBackdropBlurSourceLayout
        if (observedSource !== source) {
            removeSourceObserver()
            if (source != null) {
                source.addOnBackdropRecordedListener(sourceRecordedListener)
                observedSource = source
            }
        }
    }

    private fun removeSourceObserver() {
        observedSource?.removeOnBackdropRecordedListener(sourceRecordedListener)
        observedSource = null
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
}

open class AxBackdropBlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val renderer = AxBackdropBlurRenderer(this, attrs, defStyleAttr)

    var blurRadiusPx: Float
        get() = renderer.blurRadiusPx
        set(value) {
            renderer.blurRadiusPx = value
        }

    var cornerRadiusPx: Float
        get() = renderer.cornerRadiusPx
        set(value) {
            renderer.cornerRadiusPx = value
        }

    var backdropTintColor: Int
        get() = renderer.backdropTintColor
        set(value) {
            renderer.backdropTintColor = value
        }

    var fallbackColor: Int
        get() = renderer.fallbackColor
        set(value) {
            renderer.fallbackColor = value
        }

    var sourceId: Int
        get() = renderer.sourceId
        set(value) {
            renderer.sourceId = value
        }

    var sourceView: View?
        get() = renderer.sourceView
        set(value) {
            renderer.sourceView = value
        }

    var invalidateOnPreDraw: Boolean
        get() = renderer.invalidateOnPreDraw
        set(value) {
            renderer.invalidateOnPreDraw = value
        }

    init {
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        renderer.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas)
    }

    fun setBlurRadiusDp(radius: Float) {
        renderer.setBlurRadiusDp(radius)
    }

    fun setPreferSourceBlur(prefer: Boolean) {
        renderer.setPreferSourceBlur(prefer)
    }
}

class AxBackdropBlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AxBackdropBlurLayout(context, attrs, defStyleAttr)
