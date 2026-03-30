/*
 * Copyright (C) 2025-2026 AxionOS Project
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
package com.android.axion.compose.sheet

import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SheetSpring: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private val DismissSpring: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)
private const val SCRIM_ALPHA = 0.32f

private enum class SheetAnchor { EXPANDED, DISMISSED }

@Composable
fun BottomSheetDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    heightFraction: Float = 0f,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val draggableState = remember {
        AnchoredDraggableState(initialValue = SheetAnchor.EXPANDED)
    }
    val enterOffset = remember { Animatable(1f) }

    val animatedDismiss: () -> Unit = remember(draggableState) {
        {
            scope.launch {
                draggableState.animateTo(SheetAnchor.DISMISSED, DismissSpring)
            }
        }
    }

    Dialog(
        onDismissRequest = animatedDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val navBarColor = containerColor.toArgb()
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        dialogWindow?.apply {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            setDimAmount(SCRIM_ALPHA)
            setWindowAnimations(0)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            navigationBarColor = navBarColor
            statusBarColor = Color.Transparent.toArgb()
        }

        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = draggableState,
            positionalThreshold = { with(density) { 56.dp.toPx() } },
            animationSpec = DismissSpring,
        )

        val nestedScrollConnection = remember(draggableState, flingBehavior) {
            SheetNestedScrollConnection(draggableState, flingBehavior)
        }

        LaunchedEffect(Unit) {
            enterOffset.animateTo(0f, SheetSpring)
        }

        LaunchedEffect(draggableState) {
            snapshotFlow { draggableState.currentValue }
                .filter { it == SheetAnchor.DISMISSED }
                .collect { onDismiss() }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxHeightMod = if (heightFraction > 0f) {
                Modifier.heightIn(max = maxHeight * heightFraction)
            } else {
                Modifier
            }

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .then(maxHeightMod)
                    .onSizeChanged { size ->
                        draggableState.updateAnchors(
                            DraggableAnchors {
                                SheetAnchor.EXPANDED at 0f
                                SheetAnchor.DISMISSED at size.height.toFloat()
                            }
                        )
                        if (draggableState.currentValue == SheetAnchor.EXPANDED) {
                            scope.launch { draggableState.snapTo(SheetAnchor.EXPANDED) }
                        }
                    }
                .offset {
                    val dragOffset = runCatching { draggableState.requireOffset() }
                        .getOrDefault(0f)
                    val dismissed = draggableState.anchors
                        .positionOf(SheetAnchor.DISMISSED)
                    val enterPx = if (!dismissed.isNaN()) {
                        (enterOffset.value * dismissed).roundToInt()
                    } else 0
                    IntOffset(0, (dragOffset.roundToInt() + enterPx).coerceAtLeast(0))
                }
                .anchoredDraggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    flingBehavior = flingBehavior,
                )
                .nestedScroll(nestedScrollConnection)
                .navigationBarsPadding(),
            color = containerColor,
            shape = SheetShape,
        ) {
            content()
        }
        }
    }
}

private class SheetNestedScrollConnection(
    private val draggableState: AnchoredDraggableState<SheetAnchor>,
    private val flingBehavior: FlingBehavior,
) : NestedScrollConnection {
    private var sheetConsumedDelta = 0f
    private var childConsumedAny = false
    private var acceptFling = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val offset = when {
            isSheetMoving() -> Offset(0f, draggableState.dispatchRawDelta(available.y))
            else -> Offset.Zero
        }
        sheetConsumedDelta = offset.y
        return offset
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        childConsumedAny = sheetConsumedDelta != consumed.y
        return when {
            !childConsumedAny && source == NestedScrollSource.UserInput ->
                Offset(0f, draggableState.dispatchRawDelta(available.y))
            else -> Offset.Zero
        }
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        acceptFling = sheetConsumedDelta != 0f
        return when {
            acceptFling -> {
                performFling(available.y)
                Velocity(0f, available.y)
            }
            else -> Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        reset()
        if (acceptFling) {
            acceptFling = false
            performFling(available.y)
            return Velocity(0f, available.y)
        }
        return Velocity.Zero
    }

    private fun reset() {
        childConsumedAny = false
        sheetConsumedDelta = 0f
    }

    private suspend fun performFling(velocity: Float) {
        draggableState.anchoredDrag {
            val scope = object : ScrollScope {
                override fun scrollBy(pixels: Float): Float {
                    dragTo(draggableState.offset + pixels)
                    return pixels
                }
            }
            with(flingBehavior) { scope.performFling(velocity) }
        }
    }

    private fun isSheetMoving() = draggableState.requireOffset() != 0f
}
