/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.performDolbySelectionHaptic
import co.aospa.dolby.xiaomi.geq.data.Preset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelector(
    viewModel: EqualizerViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.preset.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showNewPresetDialog by remember { mutableStateOf(false) }
    var showPresetManager by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<Preset?>(null) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    val importedPreset by viewModel.importedPreset.collectAsState()
    val transferError by viewModel.transferError.collectAsState()
    val hapticView = LocalView.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f).height(64.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                        .align(Alignment.BottomCenter)
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            currentPreset.name ?: stringResource(R.string.dolby_preset_custom),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
                Text(
                    stringResource(R.string.dolby_geq_preset),
                    modifier = Modifier.align(Alignment.TopStart).offset(x = 14.dp, y = 1.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                modifier = Modifier.height(56.dp).width(48.dp).align(Alignment.Bottom),
                onClick = {
                    hapticView.performDolbySelectionHaptic()
                    viewModel.reset()
                },
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    modifier = Modifier.size(20.dp),
                    contentDescription = stringResource(R.string.dolby_reset_action),
                )
            }
            FilledTonalButton(
                modifier = Modifier.height(56.dp).width(48.dp).align(Alignment.Bottom),
                onClick = {
                    hapticView.performDolbySelectionHaptic()
                    expanded = false
                    showNewPresetDialog = true
                },
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.dolby_geq_add_preset_description),
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            presets.forEach { preset ->
                val isSelected = preset.name == currentPreset.name && preset.bandGains == currentPreset.bandGains
                DropdownMenuItem(
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        leadingIconColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                        )
                        .border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                            else Color.Transparent,
                            MaterialTheme.shapes.medium,
                        ),
                    text = { Text(preset.name ?: stringResource(R.string.dolby_preset_custom)) },
                    leadingIcon = {
                        PresetLeadingIcon(preset, isSelected)
                    },
                    onClick = {
                        hapticView.performDolbySelectionHaptic()
                        viewModel.setPreset(preset)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dolby_geq_manage_presets)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    showPresetManager = true
                },
            )
        }
    }

    if (showNewPresetDialog) {
        PresetNameDialog(
            title = stringResource(R.string.dolby_geq_new_preset),
            onPresetNameSet = { viewModel.createNewPreset(it) },
            onDismissDialog = { showNewPresetDialog = false },
        )
    }
    if (showPresetManager) {
        ModalBottomSheet(onDismissRequest = { showPresetManager = false }) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.82f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.dolby_geq_manage_presets),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            onClick = { showNewPresetDialog = true },
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text(stringResource(R.string.dolby_geq_save_new_preset), Modifier.padding(start = 8.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        PresetTransferActions(
                            canExport = currentPreset.isUserDefined,
                            onImport = onImport,
                            onExport = onExport,
                        )
                        Text(
                            text = stringResource(R.string.dolby_geq_preset_help),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        if (!currentPreset.isUserDefined) {
                            Text(
                                text = stringResource(R.string.dolby_geq_export_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.dolby_geq_built_in_presets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
                items(presets.filterNot { it.isUserDefined }, key = { it.name ?: "built-in" }) { preset ->
                    PresetRow(
                        preset = preset,
                        selected = preset.name == currentPreset.name && preset.bandGains == currentPreset.bandGains,
                        onSelect = {
                            viewModel.setPreset(preset)
                            showPresetManager = false
                        },
                    )
                }
                val userPresets = presets.filter { it.isUserDefined }
                if (userPresets.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.dolby_geq_your_presets),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                items(userPresets, key = { it.name ?: "user-preset" }) { preset ->
                    PresetRow(
                        preset = preset,
                        selected = preset.name == currentPreset.name && preset.bandGains == currentPreset.bandGains,
                        onSelect = {
                            viewModel.setPreset(preset)
                            showPresetManager = false
                        },
                        onRename = { presetToRename = preset },
                        onDelete = { presetToDelete = preset },
                    )
                }
            }
        }
    }
    presetToRename?.let { target ->
        PresetNameDialog(
            title = stringResource(R.string.dolby_geq_rename_preset),
            presetName = target.name.orEmpty(),
            onPresetNameSet = { viewModel.renamePreset(target, it) },
            onDismissDialog = { presetToRename = null },
        )
    }
    importedPreset?.let { imported ->
        PresetNameDialog(
            title = stringResource(R.string.dolby_geq_import_title),
            presetName = imported.name.orEmpty(),
            onPresetNameSet = { name ->
                viewModel.importPreset(name, imported.bandGains).also {
                    if (it == null) showPresetManager = false
                }
            },
            onDismissDialog = viewModel::dismissImportedPreset,
        )
    }
    if (transferError) {
        AlertDialog(
            onDismissRequest = viewModel::dismissTransferError,
            title = { Text(stringResource(R.string.dolby_geq_transfer_error_title)) },
            text = { Text(stringResource(R.string.dolby_geq_transfer_error)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = viewModel::dismissTransferError) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
    presetToDelete?.let { target ->
        ConfirmationDialog(
            text = stringResource(R.string.dolby_geq_delete_preset_prompt),
            onConfirm = { viewModel.deletePreset(target) },
            onDismiss = { presetToDelete = null },
        )
    }
}

@Composable
private fun PresetRow(
    preset: Preset,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var actionsExpanded by remember(preset) { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            headlineColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
            leadingIconColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            supportingColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            trailingIconColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        headlineContent = { Text(preset.name ?: stringResource(R.string.dolby_preset_custom)) },
        supportingContent = if (preset.isUserDefined) {
            { Text(stringResource(R.string.dolby_geq_custom_preset_hint)) }
        } else null,
        leadingContent = {
            PresetLeadingIcon(preset, selected)
        },
        trailingContent = if (preset.isUserDefined) {
            {
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.dolby_geq_preset_actions),
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dolby_geq_rename_preset)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { actionsExpanded = false; onRename?.invoke() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.dolby_geq_delete_preset)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = { actionsExpanded = false; onDelete?.invoke() },
                        )
                    }
                }
            }
        } else null,
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.large)
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                    MaterialTheme.shapes.large,
                ) else Modifier,
            )
            .clickable(onClick = onSelect),
    )
}

@Composable
private fun PresetLeadingIcon(preset: Preset, selected: Boolean) {
    val iconResource = when (preset.name) {
        stringResource(R.string.dolby_preset_default) -> R.drawable.ic_preset_flat
        stringResource(R.string.dolby_preset_rock) -> R.drawable.ic_preset_rock
        stringResource(R.string.dolby_preset_jazz) -> R.drawable.ic_preset_jazz
        stringResource(R.string.dolby_preset_pop) -> R.drawable.ic_preset_pop
        stringResource(R.string.dolby_preset_classical) -> R.drawable.ic_preset_classical
        stringResource(R.string.dolby_preset_hiphop) -> R.drawable.ic_preset_hiphop
        stringResource(R.string.dolby_preset_blues) -> R.drawable.ic_preset_blues
        stringResource(R.string.dolby_preset_electronic) -> R.drawable.ic_preset_electronic
        stringResource(R.string.dolby_preset_metal) -> R.drawable.ic_preset_metal
        else -> null
    }

    if (preset.isUserDefined || iconResource == null) {
        PresetSignatureIcon(preset, selected)
    } else {
        PresetGenreIcon(preset, selected, iconResource)
    }
}

@Composable
private fun PresetGenreIcon(preset: Preset, selected: Boolean, iconResource: Int) {
    val scheme = MaterialTheme.colorScheme
    val tones = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    val toneIndex = (preset.name.hashCode() and Int.MAX_VALUE) % tones.size
    val (toneBackground, toneForeground) = tones[toneIndex]
    val background = if (selected) scheme.primaryContainer else toneBackground
    val foreground = if (selected) scheme.onPrimaryContainer else toneForeground

    Box(Modifier.width(40.dp).height(40.dp)) {
        Box(
            Modifier.fillMaxWidth().height(40.dp)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconResource),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.width(22.dp).height(22.dp),
            )
        }
        PresetSelectedBadge(selected, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PresetSignatureIcon(preset: Preset, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val tones = listOf(
        scheme.primaryContainer to scheme.onPrimaryContainer,
        scheme.secondaryContainer to scheme.onSecondaryContainer,
        scheme.tertiaryContainer to scheme.onTertiaryContainer,
    )
    // Derive the icon's tonal variant from the curve itself so imported presets
    // with different tuning do not all receive the same generic badge.
    val curveHash = preset.bandGains.fold(17) { hash, band -> hash * 31 + band.gain }
    val toneIndex = (curveHash and Int.MAX_VALUE) % tones.size
    val (toneBackground, toneForeground) = tones[toneIndex]
    val background = if (selected) scheme.primaryContainer else toneBackground
    val foreground = if (selected) scheme.onPrimaryContainer else toneForeground

    Box(Modifier.width(40.dp).height(40.dp)) {
        Box(
            Modifier.fillMaxWidth().height(40.dp)
                .clip(CircleShape)
                .background(background),
        ) {
            Canvas(Modifier.fillMaxWidth().height(40.dp).padding(7.dp)) {
                val values = preset.bandGains
                val centerY = size.height / 2f
                val amplitude = size.height * 0.36f
                drawLine(
                    color = foreground.copy(alpha = 0.35f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 1.dp.toPx(),
                )
                val pointCount = values.size.coerceIn(1, 20)
                val points = List(pointCount) { index ->
                    val sourceIndex = if (pointCount == 1 || values.size <= 1) 0
                    else index * (values.size - 1) / (pointCount - 1)
                    val gain = (values.getOrNull(sourceIndex)?.gain ?: 0)
                        .coerceIn(-100, 100) / 100f
                    Offset(
                        x = if (pointCount == 1) size.width / 2f
                        else index * size.width / (pointCount - 1),
                        y = centerY - gain * amplitude,
                    )
                }
                if (points.isNotEmpty()) {
                    val fill = Path().apply {
                        moveTo(points.first().x, centerY)
                        points.forEach { lineTo(it.x, it.y) }
                        lineTo(points.last().x, centerY)
                        close()
                    }
                    drawPath(fill, foreground.copy(alpha = 0.16f))

                    val curve = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = curve,
                        color = foreground,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
                    )
                    points.forEach { point ->
                        drawCircle(foreground, radius = 1.1.dp.toPx(), center = point)
                    }
                }
            }
        }
        PresetSelectedBadge(selected, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PresetSelectedBadge(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            modifier = modifier.width(16.dp).height(16.dp),
            shape = CircleShape,
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            border = BorderStroke(1.dp, scheme.surfaceContainerHigh),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.dolby_geq_active_preset),
                modifier = Modifier.padding(3.dp),
            )
        }
    }
}


@Composable
private fun PresetTransferActions(canExport: Boolean, onImport: () -> Unit, onExport: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackActions = maxWidth < 340.dp || LocalDensity.current.fontScale > 1.15f
        val importAction: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedButton(
                modifier = modifier.heightIn(min = 50.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                onClick = onImport,
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Text(stringResource(R.string.dolby_geq_import_preset), Modifier.padding(start = 6.dp))
            }
        }
        val exportAction: @Composable (Modifier) -> Unit = { modifier ->
            OutlinedButton(
                modifier = modifier.heightIn(min = 50.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                enabled = canExport,
                onClick = onExport,
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null)
                Text(stringResource(R.string.dolby_geq_export_preset), Modifier.padding(start = 6.dp))
            }
        }
        if (stackActions) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                importAction(Modifier.fillMaxWidth())
                exportAction(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                importAction(Modifier.weight(1f))
                exportAction(Modifier.weight(1f))
            }
        }
    }
}
