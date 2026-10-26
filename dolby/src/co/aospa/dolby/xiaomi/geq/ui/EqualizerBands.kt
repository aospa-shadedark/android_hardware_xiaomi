/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.data.EqualizerCurve
import co.aospa.dolby.xiaomi.DolbySlider
import co.aospa.dolby.xiaomi.performDolbySelectionHaptic
import java.text.DecimalFormat
import kotlin.math.roundToInt

@Composable
fun EqualizerBands(viewModel: EqualizerViewModel) {
    val hapticView = LocalView.current
    val preset by viewModel.preset.collectAsState()
    val proMode by viewModel.proMode.collectAsState()
    val bandGains = remember(preset, proMode) {
        val nativeGains = EqualizerCurve.toNative(preset.bandGains)
        if (proMode) nativeGains else EqualizerCurve.toStandard(nativeGains)
    }
    if (bandGains.isEmpty()) return
    // Legacy preset IDs remain unchanged; labels describe the native points actually edited.
    val displayedFrequencies = remember(proMode) {
        if (proMode) EqualizerCurve.nativeFrequencies
        else EqualizerCurve.nativeFrequencies.filterIndexed { index, _ -> index % 2 == 0 }
    }

    var selectedNative by rememberSaveable { mutableIntStateOf(8) }
    val selected = (if (proMode) selectedNative else selectedNative / 2).coerceIn(bandGains.indices)
    fun selectBand(index: Int) { selectedNative = if (proMode) index else index * 2 }
    val selectedBand = bandGains[selected]
    var sliderPosition by remember(preset, selected, proMode) {
        mutableFloatStateOf(selectedBand.gain / 100f)
    }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    val responseDescription = stringResource(R.string.dolby_eq_curve_description)
    val gainDescription = stringResource(R.string.dolby_eq_band_gain, formatFrequency(displayedFrequencies[selected]))
    val selectedBandDescription = stringResource(R.string.dolby_eq_selected_band, formatFrequency(displayedFrequencies[selected]))
    val bandActions = bandGains.mapIndexed { index, band ->
        CustomAccessibilityAction(stringResource(R.string.dolby_eq_select_band, formatFrequency(displayedFrequencies[index]))) {
            selectBand(index)
            sliderPosition = band.gain / 100f
            true
        }
    }

    val responseGains = remember(bandGains, selected, sliderPosition) {
        bandGains.mapIndexed { index, band ->
            if (index == selected) sliderPosition else band.gain / 100f
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .68f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.dolby_preset),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        if (proMode) stringResource(R.string.dolby_eq_pro_scroll) else responseDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatFrequency(displayedFrequencies[selected]),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        modifier = Modifier.widthIn(min = 76.dp * LocalDensity.current.fontScale),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            formatGain(sliderPosition),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val density = LocalDensity.current
                val graphViewportWidth = maxWidth
                val selectedOffset = with(density) {
                    (selected.toFloat() * 32.dp + 14.dp - graphViewportWidth / 2)
                        .roundToPx().coerceAtLeast(0)
                }
                // Position a new mode before its first frame; animate only later band selections.
                val graphScroll = remember(proMode) { ScrollState(if (proMode) selectedOffset else 0) }
                LaunchedEffect(graphScroll, selected, graphScroll.maxValue) {
                    graphScroll.animateScrollTo(if (proMode) selectedOffset else 0)
                }
                Box(Modifier.fillMaxWidth().horizontalScroll(graphScroll, enabled = proMode)) {
                    Canvas(
                        modifier = Modifier
                            .width(if (proMode) graphViewportWidth.coerceAtLeast(636.dp) else graphViewportWidth)
                            .height(166.dp)
                            .semantics {
                                contentDescription = responseDescription
                                stateDescription = "$selectedBandDescription, ${formatGain(sliderPosition)}"
                                customActions = bandActions
                            }
                            .pointerInput(bandGains, proMode, selected) {
                                detectTapGestures { tap ->
                                    val edge = 14.dp.toPx()
                                    val plotWidth = (size.width - edge * 2).coerceAtLeast(1f)
                                    val position = ((tap.x - edge) / plotWidth).coerceIn(0f, 1f)
                                    val index = (position * (bandGains.size - 1)).roundToInt()
                                    if (index != selected) hapticView.performDolbySelectionHaptic()
                                    selectBand(index)
                                    sliderPosition = bandGains[index].gain / 100f
                                }
                            },
                    ) {
                        val edge = 14.dp.toPx()
                        val top = 14.dp.toPx()
                        val bottom = size.height - 14.dp.toPx()
                        val plotWidth = size.width - edge * 2
                        val plotHeight = bottom - top
                        val centerY = top + plotHeight / 2f
                        val halfRange = plotHeight / 2f
                        val xStep = plotWidth / (bandGains.size - 1).coerceAtLeast(1)
                        val selectedX = edge + selected * xStep
                        val highlightLeft = (selectedX - xStep * .45f).coerceAtLeast(edge)
                        val highlightRight = (selectedX + xStep * .45f).coerceAtMost(size.width - edge)
                        drawRect(
                            color = primary.copy(alpha = .045f),
                            topLeft = Offset(highlightLeft, top),
                            size = Size(highlightRight - highlightLeft, plotHeight),
                        )
                        val grid = outline.copy(alpha = .38f)
                        drawRect(
                            color = outline.copy(alpha = .65f),
                            topLeft = Offset(edge, top),
                            size = Size(plotWidth, plotHeight),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                        drawLine(
                            color = outline.copy(alpha = .85f),
                            start = Offset(edge, centerY),
                            end = Offset(size.width - edge, centerY),
                            strokeWidth = 1.25.dp.toPx(),
                        )
                        (1 until bandGains.lastIndex).forEach { index ->
                            val x = edge + index * xStep
                            drawLine(grid, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                        }

                        val points = responseGains.mapIndexed { index, gain ->
                            Offset(edge + index * xStep, centerY - gain.coerceIn(-1f, 1f) * halfRange)
                        }
                        val curve = Path().apply {
                            points.forEachIndexed { index, point ->
                                if (index == 0) {
                                    moveTo(point.x, point.y)
                                } else {
                                    val previous = points[index - 1]
                                    val controlOffset = (point.x - previous.x) * .42f
                                    cubicTo(
                                        previous.x + controlOffset, previous.y,
                                        point.x - controlOffset, point.y,
                                        point.x, point.y,
                                    )
                                }
                            }
                        }
                        val fill = Path().apply {
                            addPath(curve)
                            lineTo(points.last().x, centerY)
                            lineTo(points.first().x, centerY)
                            close()
                        }
                        drawPath(
                            fill,
                            brush = Brush.verticalGradient(
                                0f to primary.copy(alpha = .16f),
                                .5f to primary.copy(alpha = .025f),
                                1f to primary.copy(alpha = .14f),
                                startY = top,
                                endY = bottom,
                            ),
                        )
                        drawPath(
                            curve,
                            color = primary,
                            style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        points.forEachIndexed { index, point ->
                            if (index == selected) {
                                drawCircle(primary.copy(alpha = .12f), 13.dp.toPx(), point)
                                drawCircle(surface, 6.dp.toPx(), point)
                                drawCircle(primary, 6.dp.toPx(), point, style = Stroke(width = 2.2.dp.toPx()))
                            } else {
                                drawCircle(surface, 3.5.dp.toPx(), point)
                                drawCircle(
                                    primary,
                                    3.5.dp.toPx(),
                                    point,
                                    style = Stroke(width = 1.8.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
            key(proMode, selected) {
                DolbySlider(
                    value = sliderPosition,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = {
                        viewModel.setGain(selected, (sliderPosition * 100f).toInt(), inProMode = proMode)
                    },
                    valueRange = -1f..1f,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = gainDescription
                        stateDescription = formatGain(sliderPosition)
                    },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dolby_eq_min_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dolby_eq_neutral_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dolby_eq_max_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                val density = LocalDensity.current
                val fontScale = density.fontScale.coerceAtLeast(1f)
                val gap = 8.dp
                val groupGap = 12.dp
                val verticalPadding = 8.dp
                val valueStyle = MaterialTheme.typography.labelLarge
                val unitStyle = MaterialTheme.typography.labelSmall
                val tileHeight = (with(density) { valueStyle.lineHeight.toDp() + unitStyle.lineHeight.toDp() } +
                    verticalPadding * 2 + 2.dp).coerceAtLeast(56.dp)
                val viewportHeight = tileHeight * 3 + gap * 2
                // Keep tile widths identical when switching modes; Pro uses this gutter for its scrollbar.
                val availableWidth = maxWidth - 8.dp
                val columns = ((availableWidth + gap) / (64.dp * fontScale + gap))
                    .toInt().coerceIn(1, 4)
                val selectedRowTop = with(density) {
                    val rowsPerGroup = (10 + columns - 1) / columns
                    ((selected / 10).toFloat() *
                        (rowsPerGroup.toFloat() * (tileHeight + gap) - gap + groupGap) +
                        (selected % 10 / columns).toFloat() * (tileHeight + gap)).roundToPx()
                }
                val initialTileOffset = with(density) {
                    (selectedRowTop + tileHeight.roundToPx() - viewportHeight.roundToPx()).coerceAtLeast(0)
                }
                val tileScroll = remember(proMode, columns, tileHeight) {
                    ScrollState(if (proMode) initialTileOffset else 0)
                }
                val scrollColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .38f)
                LaunchedEffect(tileScroll, selected, tileScroll.maxValue) {
                    if (!proMode) return@LaunchedEffect
                    val rowsPerGroup = (10 + columns - 1) / columns
                    val top = with(density) {
                        ((selected / 10).toFloat() *
                            (rowsPerGroup.toFloat() * (tileHeight + gap) - gap + groupGap) +
                            (selected % 10 / columns).toFloat() * (tileHeight + gap)).roundToPx()
                    }
                    val height = with(density) { tileHeight.roundToPx() }
                    val viewport = with(density) { viewportHeight.roundToPx() }
                    val target = when {
                        top < tileScroll.value -> top
                        top + height > tileScroll.value + viewport -> top + height - viewport
                        else -> tileScroll.value
                    }
                    tileScroll.animateScrollTo(target.coerceAtLeast(0))
                }
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(max = if (proMode) viewportHeight else Dp.Infinity)
                        .drawWithContent {
                            drawContent()
                            if (proMode && tileScroll.maxValue > 0) {
                                val contentHeight = size.height + tileScroll.maxValue
                                val thumbHeight = (size.height * size.height / contentHeight)
                                    .coerceIn(24.dp.toPx(), size.height)
                                val top = tileScroll.value.toFloat() / tileScroll.maxValue * (size.height - thumbHeight)
                                val x = size.width - 1.5.dp.toPx()
                                drawLine(scrollColor, Offset(x, top), Offset(x, top + thumbHeight),
                                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        },
                ) {
                    // Pro keeps the graph visible while its compact band area scrolls.
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .then(if (proMode) Modifier.verticalScroll(tileScroll) else Modifier)
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(groupGap),
                    ) {
                        bandGains.withIndex().toList().chunked(10).forEach { group ->
                            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                                group.chunked(columns).forEach { rowBands ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(gap),
                                    ) {
                                        rowBands.forEach { (index, band) ->
                                            val isSelected = index == selected
                                            Surface(
                                                modifier = Modifier.weight(1f).heightIn(min = tileHeight)
                                                    .selectable(selected = isSelected, role = Role.RadioButton) {
                                                        if (index != selected) hapticView.performDolbySelectionHaptic()
                                                        selectBand(index)
                                                        sliderPosition = band.gain / 100f
                                                    },
                                                shape = MaterialTheme.shapes.large,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                border = BorderStroke(1.dp, if (isSelected) primary.copy(alpha = .45f)
                                                    else outline.copy(alpha = .55f)),
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = verticalPadding),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                ) {
                                                    Text(
                                                        text = frequencyValue(displayedFrequencies[index]),
                                                        style = valueStyle.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(frequencyUnit(displayedFrequencies[index]), style = unitStyle,
                                                        textAlign = TextAlign.Center, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun frequencyValue(frequency: Int): String =
    if (frequency >= 1000) DecimalFormat("0.###").format(frequency / 1000.0) else frequency.toString()

private fun frequencyUnit(frequency: Int): String = if (frequency >= 1000) "kHz" else "Hz"

private fun formatFrequency(frequency: Int): String =
    "${frequencyValue(frequency)} ${frequencyUnit(frequency)}"

private fun formatGain(gain: Float): String {
    val db = gain * 10f
    return (if (db > 0) "+" else "") + "%.1f dB".format(db)
}
