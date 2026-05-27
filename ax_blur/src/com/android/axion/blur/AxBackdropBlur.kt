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
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import com.android.axion.blur.domain.interactor.AxBackdropBlurInteractor
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsModel
import com.android.axion.blur.shared.model.AxBackdropBlurSettingsSubscription
import com.android.axion.blur.ui.view.AxViewBackdropBlur
import kotlin.math.roundToInt

@Composable
fun rememberAxBackdropBlurState(
    positionStrategy: AxBackdropBlurPositionStrategy = AxBackdropBlurPositionStrategy.Auto,
): AxBackdropBlurState = remember { AxBackdropBlurState() }.apply {
    this.positionStrategy = positionStrategy
}

enum class AxBackdropBlurPositionStrategy {
    Local,
    Screen,
    Auto,
}

@Stable
class AxBackdropBlurState {
    var positionStrategy by mutableStateOf(AxBackdropBlurPositionStrategy.Auto)
    internal var resolvedPositionStrategy by mutableStateOf(AxBackdropBlurPositionStrategy.Local)
    internal val areas = mutableStateListOf<AxBackdropBlurArea>()

    internal fun addArea(area: AxBackdropBlurArea) {
        areas += area
    }

    internal fun removeArea(area: AxBackdropBlurArea) {
        areas -= area
    }
}

@Immutable
data class AxBackdropBlurStyle(
    val blurRadius: Dp = Dp.Unspecified,
    val tint: Color = Color.Unspecified,
    val fallbackColor: Color = Color.Unspecified,
    val cornerRadius: Dp = Dp.Unspecified,
)

object AxBackdropBlurDefaults {
    val BlurRadius = AX_BACKDROP_BLUR_RADIUS_DP.dp
    val Style = AxBackdropBlurStyle()

    @Composable
    fun style(
        blurRadius: Dp = Dp.Unspecified,
        cornerRadius: Dp = Dp.Unspecified,
    ): AxBackdropBlurStyle {
        LocalConfiguration.current
        val context = LocalContext.current
        val density = LocalDensity.current
        val settings = rememberAxBackdropBlurSettingsModel(context)
        val tint = Color(AxBlurColors.tint(context))
        return AxBackdropBlurStyle(
            blurRadius = if (blurRadius.isSpecified) blurRadius else with(density) {
                settings.blurRadiusPx.toDp()
            },
            cornerRadius = cornerRadius,
            tint = tint,
            fallbackColor = Color(AxBlurColors.fallback(context)),
        )
    }

    @Composable
    fun surfaceStyle(
        blurRadius: Dp = Dp.Unspecified,
        cornerRadius: Dp = Dp.Unspecified,
        level: Int = 0,
    ): AxBackdropBlurStyle {
        LocalConfiguration.current
        val context = LocalContext.current
        val density = LocalDensity.current
        val settings = rememberAxBackdropBlurSettingsModel(context)
        return AxBackdropBlurStyle(
            blurRadius = if (blurRadius.isSpecified) blurRadius else with(density) {
                settings.blurRadiusPx.toDp()
            },
            cornerRadius = cornerRadius,
            tint = Color(AxBlurColors.surfaceEffect(context, level)),
            fallbackColor = Color(AxBlurColors.fallback(context)),
        )
    }
}

@Composable
fun AxBackdropBlurSource(
    state: AxBackdropBlurState,
    modifier: Modifier = Modifier,
    zIndex: Float = 0f,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.axBackdropBlurSource(state, zIndex),
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints,
        content = content,
    )
}

@Composable
fun AxBackdropBlur(
    state: AxBackdropBlurState,
    modifier: Modifier = Modifier,
    style: AxBackdropBlurStyle = AxBackdropBlurDefaults.Style,
    zIndex: Float = 1f,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier.axBackdropBlur(state, style, zIndex),
        contentAlignment = contentAlignment,
        propagateMinConstraints = propagateMinConstraints,
        content = content,
    )
}

@Stable
fun Modifier.axBackdropBlurSource(
    state: AxBackdropBlurState,
    zIndex: Float = 0f,
): Modifier = this then AxBackdropBlurSourceElement(state, zIndex)

@Stable
fun Modifier.axBackdropBlur(
    state: AxBackdropBlurState,
    style: AxBackdropBlurStyle = AxBackdropBlurDefaults.Style,
    zIndex: Float = 1f,
): Modifier = this then AxBackdropBlurElement(state, style, zIndex)

@Stable
fun Modifier.axBlurSurface(
    state: AxBackdropBlurState,
    style: AxBackdropBlurStyle,
    shape: Shape,
    zIndex: Float = 1f,
): Modifier = clip(shape).axBackdropBlur(state, style, zIndex)

internal class AxBackdropBlurArea {
    var position by mutableStateOf(Offset.Unspecified)
    var size by mutableStateOf(Size.Unspecified)
    var zIndex by mutableStateOf(0f)
    var windowId: Any? by mutableStateOf(null)
    var contentLayer: GraphicsLayer? by mutableStateOf(null)
    var drawingContent = false
    val preDrawListeners = mutableStateSetOf<() -> Unit>()

    val bounds: Rect?
        get() = if (position.isSpecified && size.isSpecified) Rect(position, size) else null

    fun reset() {
        position = Offset.Unspecified
        size = Size.Unspecified
        windowId = null
        drawingContent = false
        notifyChanged()
    }

    fun notifyChanged() {
        preDrawListeners.forEach { it() }
    }
}

private fun resolvePositionStrategy(
    configured: AxBackdropBlurPositionStrategy,
    areas: List<AxBackdropBlurArea>,
    windowId: Any?,
): AxBackdropBlurPositionStrategy = when (configured) {
    AxBackdropBlurPositionStrategy.Auto -> {
        if (areas.any { it.windowId != null && it.windowId != windowId }) {
            AxBackdropBlurPositionStrategy.Screen
        } else {
            AxBackdropBlurPositionStrategy.Local
        }
    }
    else -> configured
}

private fun LayoutCoordinates.boundsForAxBlur(
    strategy: AxBackdropBlurPositionStrategy,
): Rect? = when (strategy) {
    AxBackdropBlurPositionStrategy.Local,
    AxBackdropBlurPositionStrategy.Auto -> boundsInRoot()
    AxBackdropBlurPositionStrategy.Screen -> try {
        val rootPosition = findRootCoordinates().positionOnScreen()
        if (rootPosition.isSpecified) boundsInRoot().translate(rootPosition) else null
    } catch (_: Exception) {
        null
    }
}

private data class AxBackdropBlurSourceElement(
    val state: AxBackdropBlurState,
    val zIndex: Float,
) : ModifierNodeElement<AxBackdropBlurSourceNode>() {
    override fun create() = AxBackdropBlurSourceNode(state, zIndex)

    override fun update(node: AxBackdropBlurSourceNode) {
        node.update(state, zIndex)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "axBackdropBlurSource"
        properties["zIndex"] = zIndex
    }
}

private class AxBackdropBlurSourceNode(
    private var state: AxBackdropBlurState,
    zIndex: Float,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode {

    private val area = AxBackdropBlurArea().apply { this.zIndex = zIndex }
    private var lastCoordinates: LayoutCoordinates? = null
    private var hostView: View? = null
    private var observingPreDraw = false
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (isAttached) {
            lastCoordinates?.let { updateCoordinates(it, state.resolvedPositionStrategy) }
        }
        true
    }

    override val shouldAutoInvalidate = false

    fun update(state: AxBackdropBlurState, zIndex: Float) {
        if (this.state !== state) {
            val attachedToState = area in this.state.areas
            if (attachedToState) this.state.removeArea(area)
            this.state = state
            if (attachedToState) this.state.addArea(area)
            if (isAttached) onObservedReadsChanged()
        }
        if (area.zIndex != zIndex) {
            area.zIndex = zIndex
            area.notifyChanged()
            invalidateDraw()
        }
    }

    override fun onAttach() {
        state.addArea(area)
        updatePreDrawObserver()
        onObservedReadsChanged()
    }

    override fun onDetach() {
        removePreDrawObserver()
        area.reset()
        releaseLayer()
        state.removeArea(area)
    }

    override fun onObservedReadsChanged() {
        observeReads {
            val strategy = state.resolvedPositionStrategy
            lastCoordinates?.let { updateCoordinates(it, strategy) }
        }
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        val newSize = size.toSize()
        if (area.size != newSize) {
            area.size = newSize
            area.notifyChanged()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun ContentDrawScope.draw() {
        try {
            area.drawingContent = true
            if (!isAttached) return
            lastCoordinates?.let { updateCoordinates(it, state.resolvedPositionStrategy) }
            val layer = if (size.width > 0f && size.height > 0f) {
                area.contentLayer?.takeUnless { it.isReleased }
                    ?: currentValueOf(LocalGraphicsContext).createGraphicsLayer().also {
                        area.contentLayer = it
                    }
            } else {
                null
            }

            if (layer != null) {
                val contentDrawScope = this
                layer.record(size.toIntSize()) { contentDrawScope.drawContent() }
                drawLayer(layer)
            } else {
                drawContent()
            }
        } finally {
            area.drawingContent = false
            area.notifyChanged()
        }
    }

    override fun onReset() {
        area.reset()
        removePreDrawObserver()
    }

    private fun updateCoordinates(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        updateCoordinates(coordinates, state.resolvedPositionStrategy)
    }

    private fun updateCoordinates(
        coordinates: LayoutCoordinates,
        strategy: AxBackdropBlurPositionStrategy,
    ) {
        val newBounds = coordinates.boundsForAxBlur(strategy)
        val newPosition = newBounds?.topLeft ?: Offset.Unspecified
        val newSize = newBounds?.size ?: Size.Unspecified
        val newWindowId = currentWindowId()
        var changed = false
        if (area.position != newPosition) {
            area.position = newPosition
            changed = true
        }
        if (area.size != newSize) {
            area.size = newSize
            changed = true
        }
        if (area.windowId != newWindowId) {
            area.windowId = newWindowId
            changed = true
        }
        if (changed) {
            area.notifyChanged()
        }
    }

    private fun releaseLayer() {
        area.contentLayer?.let { currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(it) }
        area.contentLayer = null
        area.notifyChanged()
    }

    private fun currentWindowId(): Any? {
        return hostView?.windowId ?: currentValueOf(LocalView).windowId
    }

    private fun updatePreDrawObserver() {
        val view = if (isAttached) currentValueOf(LocalView) else null
        if (hostView !== view) {
            removePreDrawObserver()
            hostView = view
        }
        if (view != null && !observingPreDraw) {
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            observingPreDraw = true
        }
    }

    private fun removePreDrawObserver() {
        if (!observingPreDraw) return
        hostView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDrawListener)
        observingPreDraw = false
    }
}

private data class AxBackdropBlurElement(
    val state: AxBackdropBlurState,
    val style: AxBackdropBlurStyle,
    val zIndex: Float,
) : ModifierNodeElement<AxBackdropBlurNode>() {
    override fun create() = AxBackdropBlurNode(state, style, zIndex)

    override fun update(node: AxBackdropBlurNode) {
        node.update(state, style, zIndex)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "axBackdropBlur"
        properties["style"] = style
        properties["zIndex"] = zIndex
    }
}

private class AxBackdropBlurNode(
    private var state: AxBackdropBlurState,
    private var style: AxBackdropBlurStyle,
    private var zIndex: Float,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    DrawModifierNode,
    GlobalPositionAwareModifierNode,
    LayoutAwareModifierNode,
    ObserverModifierNode {

    private var position = Offset.Unspecified
    private var nodeSize = Size.Unspecified
    private var rootBounds = Rect.Zero
    private var windowId: Any? = null
    private var lastCoordinates: LayoutCoordinates? = null
    private var areas = emptyList<AxBackdropBlurArea>()
    private var layer: GraphicsLayer? = null
    private var renderEffect: RenderEffect? = null
    private var renderEffectRadius = -1f
    private var nativeBackdropBlur: AxViewBackdropBlur? = null
    private var nativeBackdropBlurView: View? = null
    private var settingsValues: AxBackdropBlurSettingsModel? = null
    private var settingsObserver: AxBackdropBlurSettingsSubscription? = null
    private var needsSourceInvalidation = false
    private var hostView: View? = null
    private var observingPreDraw = false
    private val sourceInvalidation: () -> Unit = {
        if (!needsSourceInvalidation) {
            needsSourceInvalidation = true
            invalidateDraw()
        }
    }
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        if (isAttached) {
            lastCoordinates?.let { updateCoordinates(it, state.resolvedPositionStrategy, true) }
            invalidateDraw()
        }
        true
    }

    override val shouldAutoInvalidate = false

    fun update(state: AxBackdropBlurState, style: AxBackdropBlurStyle, zIndex: Float) {
        if (this.state !== state) {
            clearAreaListeners()
            this.state = state
        }
        if (this.style != style || this.zIndex != zIndex) {
            this.style = style
            this.zIndex = zIndex
            invalidateDraw()
        }
        updateAreas()
    }

    override fun onAttach() {
        val context = currentValueOf(LocalContext)
        val interactor = AxBackdropBlurInteractor(context)
        updateSettings(interactor)
        settingsObserver = interactor.createSubscription {
            updateSettings(interactor)
            invalidateDraw()
        }.also { it.start() }
        updatePreDrawObserver()
        updateAreas()
    }

    override fun onDetach() {
        removePreDrawObserver()
        settingsObserver?.stop()
        settingsObserver = null
        settingsValues = null
        nativeBackdropBlur?.onDetachedFromWindow()
        nativeBackdropBlur = null
        nativeBackdropBlurView = null
        clearAreaListeners()
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
    }

    override fun onObservedReadsChanged() {
        updateAreas()
        updatePreDrawObserver()
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun onRemeasured(size: IntSize) {
        val newSize = size.toSize()
        if (nodeSize != newSize) {
            nodeSize = newSize
            invalidateDraw()
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        updateCoordinates(coordinates)
    }

    override fun ContentDrawScope.draw() {
        try {
            lastCoordinates?.let { updateCoordinates(it, state.resolvedPositionStrategy, false) }
            val context = currentValueOf(LocalContext)
            val settings = settingsValues ?: AxBackdropBlurInteractor(context).settings().also {
                settingsValues = it
            }
            val requestedBlurRadius = if (style.blurRadius.isSpecified) {
                style.blurRadius.toPx()
            } else {
                settings.blurRadiusPx
            }
            val blurRadius = if (settings.enabled) requestedBlurRadius else 0f
            val effect = blurEffect(blurRadius)
            val tint = resolveColor(style.tint, AxBlurColors.tint(context))
            val fallbackColor = resolveColor(
                style.fallbackColor,
                AxBlurColors.fallback(context),
            )
            val cornerRadius = if (style.cornerRadius.isSpecified) {
                style.cornerRadius.toPx()
            } else {
                0f
            }
            val sourceFirst = state.resolvedPositionStrategy == AxBackdropBlurPositionStrategy.Local
            if (!sourceFirst && drawCrossWindowBackdrop(blurRadius, tint, cornerRadius)) {
                drawContent()
                return
            }
            val geometry = layerGeometry(blurRadius)
            if (canDrawBackdrop(effect, geometry)) {
                val targetLayer = layer?.takeUnless { it.isReleased }
                    ?: requireGraphicsContext().createGraphicsLayer().also { layer = it }
                targetLayer.record(geometry.size.toIntSize()) {
                    translate(-geometry.bounds.left, -geometry.bounds.top) {
                        areas.forEach { area ->
                            val areaPosition = Snapshot.withoutReadObservation {
                                area.position.takeOrElse { Offset.Zero }
                            }
                            val contentLayer = Snapshot.withoutReadObservation {
                                area.contentLayer?.takeUnless { it.isReleased }
                                    ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
                            }
                            val areaSize = Snapshot.withoutReadObservation { area.size }
                            if (!area.drawingContent && contentLayer != null) {
                                translate(areaPosition.x, areaPosition.y) {
                                    if (areaSize.isSpecified) {
                                        val scaleX = areaSize.width / contentLayer.size.width
                                        val scaleY = areaSize.height / contentLayer.size.height
                                        if (
                                            scaleX.isFinite() &&
                                            scaleY.isFinite() &&
                                            scaleX > 0f &&
                                            scaleY > 0f &&
                                            (scaleX != 1f || scaleY != 1f)
                                        ) {
                                            scale(scaleX, scaleY, Offset.Zero) {
                                                drawLayer(contentLayer)
                                            }
                                        } else {
                                            drawLayer(contentLayer)
                                        }
                                    } else {
                                        drawLayer(contentLayer)
                                    }
                                }
                            }
                        }
                    }
                }
                targetLayer.renderEffect = effect
                clipRect {
                    translate(-geometry.offset.x, -geometry.offset.y) {
                        drawLayer(targetLayer)
                    }
                }
                drawOverlay(tint)
            } else {
                if (sourceFirst && drawCrossWindowBackdrop(blurRadius, tint, cornerRadius)) {
                    drawContent()
                    return
                }
                drawOverlay(fallbackColor)
            }
            drawContent()
        } finally {
            needsSourceInvalidation = false
        }
    }

    private fun ContentDrawScope.drawCrossWindowBackdrop(
        blurRadius: Float,
        tint: Color,
        cornerRadius: Float,
    ): Boolean {
        val blur = nativeBackdropBlurFor(currentValueOf(LocalView))
        blur.setBlurRadiusPx(blurRadius)
        val boundsLeft = 0
        val boundsTop = 0
        val boundsRight = size.width.roundToInt()
        val boundsBottom = size.height.roundToInt()
        var drawn = false
        drawIntoCanvas {
            drawn = blur.draw(
                it.nativeCanvas,
                boundsLeft,
                boundsTop,
                boundsRight,
                boundsBottom,
                if (cornerRadius.isFinite()) cornerRadius.coerceAtLeast(0f) else 0f,
            )
        }
        if (!drawn) return false
        drawOverlay(tint)
        return true
    }

    private fun nativeBackdropBlurFor(view: View): AxViewBackdropBlur {
        val current = nativeBackdropBlur
        if (current != null && nativeBackdropBlurView === view) return current
        current?.onDetachedFromWindow()
        nativeBackdropBlurView = view
        return AxViewBackdropBlur(view, observeSettings = false).apply {
            setEnabled(true)
            onAttachedToWindow()
            nativeBackdropBlur = this
        }
    }

    private fun updateAreas() {
        if (!isAttached) return
        observeReads {
            state.positionStrategy
            state.resolvedPositionStrategy
            val nextAreas = state.areas
                .filter { it.zIndex < zIndex }
                .sortedBy { it.zIndex }
            if (areas != nextAreas) {
                clearAreaListeners()
                areas = nextAreas
                areas.forEach { it.preDrawListeners += sourceInvalidation }
                updatePreDrawObserver()
                invalidateDraw()
            }
            val resolved = resolvePositionStrategy(
                configured = state.positionStrategy,
                areas = areas,
                windowId = windowId,
            )
            if (state.resolvedPositionStrategy != resolved) {
                state.resolvedPositionStrategy = resolved
                lastCoordinates?.let { updateCoordinates(it, resolved, false) }
                invalidateDraw()
            }
        }
    }

    private fun updateCoordinates(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        updateCoordinates(coordinates, state.resolvedPositionStrategy, true)
    }

    private fun updateCoordinates(
        coordinates: LayoutCoordinates,
        strategy: AxBackdropBlurPositionStrategy,
        refreshAreas: Boolean,
    ) {
        val newBounds = coordinates.boundsForAxBlur(strategy)
        val newPosition = newBounds?.topLeft ?: Offset.Unspecified
        val newSize = newBounds?.size ?: Size.Unspecified
        val rootCoordinates = coordinates.findRootCoordinates()
        val newRootBounds = rootCoordinates.boundsForAxBlur(strategy) ?: Rect.Zero
        val newWindowId = currentWindowId()
        if (
            position != newPosition ||
            nodeSize != newSize ||
            rootBounds != newRootBounds ||
            windowId != newWindowId
        ) {
            position = newPosition
            nodeSize = newSize
            rootBounds = newRootBounds
            windowId = newWindowId
            if (refreshAreas) updateAreas()
            invalidateDraw()
        }
    }

    private fun layerGeometry(blurRadius: Float): AxBackdropBlurLayerGeometry {
        if (
            !position.isSpecified ||
            !nodeSize.isSpecified ||
            nodeSize.width <= 0f ||
            nodeSize.height <= 0f
        ) {
            return AxBackdropBlurLayerGeometry(Rect.Zero, Offset.Zero)
        }
        val nodeBounds = Rect(position, nodeSize)
        val expandedBounds = if (blurRadius >= 1f) {
            nodeBounds.inflate(blurRadius)
        } else {
            nodeBounds
        }
        val bounds = expandedBounds.intersectOrEmpty(
            if (rootBounds.isEmpty) expandedBounds else rootBounds,
        )
        return AxBackdropBlurLayerGeometry(
            bounds = bounds,
            offset = position - bounds.topLeft,
        )
    }

    private fun blurEffect(radius: Float): RenderEffect? {
        if (!radius.isFinite() || radius <= 0f) return null
        if (renderEffect == null || renderEffectRadius != radius) {
            renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            renderEffectRadius = radius
        }
        return renderEffect
    }

    private fun canDrawBackdrop(
        effect: RenderEffect?,
        geometry: AxBackdropBlurLayerGeometry,
    ): Boolean {
        return effect?.isSupported() == true &&
            position.isSpecified &&
            nodeSize.isSpecified &&
            nodeSize.width > 0f &&
            nodeSize.height > 0f &&
            geometry.size.width > 0f &&
            geometry.size.height > 0f &&
            hasDrawableArea()
    }

    private fun hasDrawableArea(): Boolean {
        return areas.any { area ->
            Snapshot.withoutReadObservation {
                area.position.isSpecified &&
                    !area.drawingContent &&
                    area.contentLayer?.takeUnless { it.isReleased }
                        ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 } != null
            }
        }
    }

    private fun Rect.intersectOrEmpty(other: Rect): Rect {
        val intersection = intersect(other)
        return if (intersection.isEmpty) Rect.Zero else intersection
    }

    private data class AxBackdropBlurLayerGeometry(
        val bounds: Rect,
        val offset: Offset,
    ) {
        val size: Size
            get() = bounds.size
    }

    private fun ContentDrawScope.drawOverlay(color: Color) {
        if (color.alpha > 0f) {
            drawRect(color)
        }
    }

    private fun resolveColor(color: Color, defaultColor: Int): Color {
        return if (color.isSpecified) color else Color(defaultColor)
    }

    private fun updateSettings(interactor: AxBackdropBlurInteractor) {
        settingsValues = interactor.settings()
    }

    private fun clearAreaListeners() {
        areas.forEach { it.preDrawListeners -= sourceInvalidation }
        areas = emptyList()
    }

    private fun currentWindowId(): Any? {
        return hostView?.windowId ?: currentValueOf(LocalView).windowId
    }

    private fun updatePreDrawObserver() {
        val view = if (isAttached) currentValueOf(LocalView) else null
        if (hostView !== view) {
            removePreDrawObserver()
            hostView = view
        }
        if (view != null && !observingPreDraw) {
            view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            observingPreDraw = true
        }
    }

    private fun removePreDrawObserver() {
        if (!observingPreDraw) return
        hostView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnPreDrawListener(preDrawListener)
        observingPreDraw = false
    }
}

@Composable
private fun rememberAxBackdropBlurSettingsModel(context: Context): AxBackdropBlurSettingsModel {
    val interactor = remember(context) { AxBackdropBlurInteractor(context) }
    var settings by remember(interactor) { mutableStateOf(interactor.settings()) }
    DisposableEffect(interactor) {
        val observer = interactor.createSubscription {
            settings = interactor.settings()
        }
        observer.start()
        onDispose { observer.stop() }
    }
    return settings
}
