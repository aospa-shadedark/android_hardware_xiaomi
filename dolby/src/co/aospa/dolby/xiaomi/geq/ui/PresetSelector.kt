/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.performDolbySelectionHaptic
import co.aospa.dolby.xiaomi.geq.data.Preset
import co.aospa.dolby.xiaomi.geq.data.PresetFileTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetSelector(viewModel: EqualizerViewModel) {
    val presets by viewModel.presets.collectAsState()
    val currentPreset by viewModel.preset.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showNewPresetDialog by remember { mutableStateOf(false) }
    var showPresetManager by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<Preset?>(null) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var importedPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingExport by remember { mutableStateOf<Preset?>(null) }
    var transferError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val hapticView = LocalView.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { PresetFileTransfer.read(context, uri) } }
            result.onSuccess { importedPreset = it }.onFailure { transferError = true }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val preset = pendingExport
        if (uri != null && preset != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { PresetFileTransfer.write(context, uri, preset) } }
            if (result.isFailure) transferError = true
            pendingExport = null
        } else pendingExport = null
    }

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
                modifier = Modifier.height(56.dp).widthIn(min = 104.dp).align(Alignment.Bottom),
                onClick = {
                    if (currentPreset.isUserDefined) showResetConfirmDialog = true
                    else viewModel.reset()
                },
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.dolby_reset_action))
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
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
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
                text = { Text(stringResource(R.string.dolby_geq_save_new_preset)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    showNewPresetDialog = true
                },
            )
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
                            onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                            onExport = {
                                pendingExport = currentPreset
                                exportLauncher.launch("${currentPreset.name.orEmpty().replace(' ', '_')}.ldp")
                            },
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
            onDismissDialog = { importedPreset = null },
        )
    }
    if (transferError) {
        AlertDialog(
            onDismissRequest = { transferError = false },
            title = { Text(stringResource(R.string.dolby_geq_transfer_error_title)) },
            text = { Text(stringResource(R.string.dolby_geq_transfer_error)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { transferError = false }) {
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
    if (showResetConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.dolby_geq_reset_gains_prompt),
            onConfirm = { viewModel.reset() },
            onDismiss = { showResetConfirmDialog = false },
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
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.dolby_geq_active_preset))
            } else {
                Spacer(Modifier.width(24.dp))
            }
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
