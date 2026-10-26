/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A compact, consistent slider matching the app's custom equalizer controls. */
@Composable
internal fun DolbySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val thumbScale by animateFloatAsState(
        targetValue = if (enabled && (dragged || pressed || focused)) 1.18f else 1f,
        animationSpec = tween(100),
        label = "slider interaction",
    )
    var lastTickTime by remember { mutableLongStateOf(0L) }
    val tickCount = if (steps > 0) steps + 1 else 20
    fun tickPosition(position: Float): Int =
        (((position - valueRange.start) /
            (valueRange.endInclusive - valueRange.start)) * tickCount).roundToInt()
    val active = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.18f else 0.12f)
    Slider(
        value = value,
        onValueChange = { nextValue ->
            val now = SystemClock.uptimeMillis()
            if (tickPosition(nextValue) != tickPosition(value) && now - lastTickTime >= 40L) {
                view.performDolbySliderTickHaptic()
                lastTickTime = now
            }
            onValueChange(nextValue)
        },
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        onValueChangeFinished = {
            view.performDolbySliderReleaseHaptic()
            onValueChangeFinished?.invoke()
        },
        thumb = {
            Box(
                Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .clip(CircleShape)
                    .background(active)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val fraction = ((state.value - state.valueRange.start) /
                    (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                val centerY = size.height / 2f
                val stroke = 4.dp.toPx()
                drawLine(
                    color = inactive,
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                if (fraction > 0f) {
                    val isRtl = layoutDirection == LayoutDirection.Rtl
                    drawLine(
                        color = active,
                        start = Offset(if (isRtl) size.width else 0f, centerY),
                        end = Offset(size.width * if (isRtl) 1f - fraction else fraction, centerY),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        },
    )
}
