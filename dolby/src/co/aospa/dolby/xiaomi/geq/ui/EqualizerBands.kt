/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.DolbySlider
import kotlin.math.roundToInt

@Composable
fun EqualizerBands(viewModel: EqualizerViewModel) {
    val preset by viewModel.preset.collectAsState()
    val bandGains = preset.bandGains
    if (bandGains.isEmpty()) return

    var selected by rememberSaveable { mutableIntStateOf(4) }
    selected = selected.coerceIn(bandGains.indices)
    val selectedBand = bandGains[selected]
    var sliderPosition by remember(preset, selected) {
        mutableFloatStateOf(selectedBand.gain / 100f)
    }
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val responseDescription = stringResource(R.string.dolby_eq_curve_description)
    val gainDescription = stringResource(R.string.dolby_eq_band_gain, formatFrequency(selectedBand.band))
    val selectedBandDescription = stringResource(R.string.dolby_eq_selected_band, formatFrequency(selectedBand.band))
    val bandActions = bandGains.mapIndexed { index, band ->
        CustomAccessibilityAction(stringResource(R.string.dolby_eq_select_band, formatFrequency(band.band))) {
            selected = index
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
                        responseDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatFrequency(selectedBand.band),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            formatGain(sliderPosition),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .semantics {
                        contentDescription = responseDescription
                        stateDescription = selectedBandDescription
                        customActions = bandActions
                    }
                    .pointerInput(bandGains) {
                        detectTapGestures { tap ->
                            val edge = 12.dp.toPx()
                            val plotWidth = (size.width - edge * 2).coerceAtLeast(1f)
                            val position = ((tap.x - edge) / plotWidth).coerceIn(0f, 1f)
                            val index = (position * (bandGains.size - 1)).roundToInt()
                            selected = index
                            sliderPosition = bandGains[index].gain / 100f
                        }
                    },
            ) {
                val edge = 12.dp.toPx()
                val top = 10.dp.toPx()
                val bottom = size.height - 10.dp.toPx()
                val plotWidth = size.width - edge * 2
                val plotHeight = bottom - top
                val centerY = top + plotHeight / 2f
                val halfRange = plotHeight * .46f
                val xStep = plotWidth / (bandGains.size - 1).coerceAtLeast(1)
                val grid = outline.copy(alpha = .65f)

                listOf(-1f, 0f, 1f).forEach { tick ->
                    val y = centerY - tick * halfRange
                    drawLine(
                        color = if (tick == 0f) outline else grid,
                        start = Offset(edge, y),
                        end = Offset(size.width - edge, y),
                        strokeWidth = if (tick == 0f) 1.5.dp.toPx() else 1.dp.toPx(),
                    )
                }
                bandGains.indices.forEach { index ->
                    val x = edge + index * xStep
                    drawLine(grid.copy(alpha = .35f), Offset(x, top), Offset(x, bottom), 1.dp.toPx())
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
                        colors = listOf(primary.copy(alpha = .24f), primary.copy(alpha = .015f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(curve, color = primary, style = Stroke(width = 3.dp.toPx()))
                points.forEachIndexed { index, point ->
                    if (index == selected) {
                        drawCircle(primary.copy(alpha = .18f), 13.dp.toPx(), point)
                    }
                    drawCircle(surface, if (index == selected) 6.dp.toPx() else 3.5.dp.toPx(), point)
                    drawCircle(
                        primary,
                        if (index == selected) 6.dp.toPx() else 3.5.dp.toPx(),
                        point,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                bandGains.chunked(5).forEachIndexed { rowIndex, rowBands ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowBands.forEachIndexed { columnIndex, band ->
                            val index = rowIndex * 5 + columnIndex
                            val isSelected = index == selected
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .selectable(selected = isSelected, role = Role.RadioButton) {
                                        if (index != selected) hapticView.performDolbySelectionHaptic()
                                        selected = index
                                        sliderPosition = band.gain / 100f
                                    },
                                shape = MaterialTheme.shapes.small,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                contentColor = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                border = if (isSelected) null else BorderStroke(
                                    1.dp,
                                    outline.copy(alpha = .55f),
                                ),
                            ) {
                                Text(
                                    text = formatFrequency(band.band),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            DolbySlider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                onValueChangeFinished = {
                    viewModel.setGain(selected, (sliderPosition * 100f).toInt())
                },
                valueRange = -1f..1f,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = gainDescription
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.dolby_eq_min_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dolby_eq_neutral_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.dolby_eq_max_gain), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatFrequency(frequency: Int) =
    if (frequency >= 1000) "${frequency / 1000} kHz" else "${frequency} Hz"

private fun formatGain(gain: Float): String {
    val db = gain * 10f
    return (if (db > 0) "+" else "") + "%.1f dB".format(db)
}
