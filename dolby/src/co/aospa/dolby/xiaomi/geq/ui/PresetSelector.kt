/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
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
    var showRenamePresetDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var importedPreset by remember { mutableStateOf<Preset?>(null) }
    var pendingExport by remember { mutableStateOf<Preset?>(null) }
    var transferError by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = currentPreset.name ?: stringResource(R.string.dolby_preset_custom),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.dolby_geq_preset)) },
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(),
                shape = androidx.compose.material3.MaterialTheme.shapes.large,
                modifier = Modifier.weight(1f)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .focusProperties { canFocus = false },
            )
            FilledTonalButton(
                modifier = Modifier.height(56.dp).widthIn(min = 104.dp),
                onClick = {
                    if (currentPreset.isUserDefined) showResetConfirmDialog = true
                    else viewModel.reset()
                },
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.reset_settings_24px),
                    contentDescription = null,
                )
                Text(stringResource(R.string.dolby_reset_action))
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name ?: stringResource(R.string.dolby_preset_custom)) },
                    onClick = {
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
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.dolby_geq_manage_presets),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.dolby_geq_preset_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = {
                        showNewPresetDialog = true
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.dolby_geq_save_new_preset), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Text(stringResource(R.string.dolby_geq_import_preset), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = currentPreset.isUserDefined,
                    onClick = {
                        pendingExport = currentPreset
                        exportLauncher.launch("${currentPreset.name.orEmpty().replace(' ', '_')}.ldp")
                    },
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.dolby_geq_export_preset), Modifier.padding(start = 8.dp))
                }
                if (!currentPreset.isUserDefined) {
                    Text(
                        text = stringResource(R.string.dolby_geq_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
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
                            onRename = {
                                viewModel.setPreset(preset)
                                showRenamePresetDialog = true
                            },
                            onDelete = {
                                viewModel.setPreset(preset)
                                showDeleteConfirmDialog = true
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showRenamePresetDialog) {
        PresetNameDialog(
            title = stringResource(R.string.dolby_geq_rename_preset),
            presetName = currentPreset.name.orEmpty(),
            onPresetNameSet = { viewModel.renamePreset(currentPreset, it) },
            onDismissDialog = { showRenamePresetDialog = false },
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
    if (showDeleteConfirmDialog) {
        ConfirmationDialog(
            text = stringResource(R.string.dolby_geq_delete_preset_prompt),
            onConfirm = { viewModel.deletePreset(currentPreset) },
            onDismiss = { showDeleteConfirmDialog = false },
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
        headlineContent = { Text(preset.name ?: stringResource(R.string.dolby_preset_custom)) },
        supportingContent = if (preset.isUserDefined) {
            { Text(stringResource(R.string.dolby_geq_custom_preset_hint)) }
        } else null,
        leadingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.dolby_geq_active_preset)) }
        } else null,
        trailingContent = if (preset.isUserDefined) {
            {
                Box {
                    IconButton(onClick = { actionsExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.dolby_geq_preset_actions))
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    )
}
