/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.DolbyFirstUseHelp
import co.aospa.dolby.xiaomi.DolbyHelpTopic
import co.aospa.dolby.xiaomi.performDolbySelectionHaptic

@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    showModeControl: Boolean = true,
) {
    val presetStateHolder = rememberSaveableStateHolder()
    // Keep result registrations alive while a profile refresh replaces the editor
    // with its loading/error UI. Transfer snapshots live in the ViewModel.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.readImportedPreset(it)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        viewModel.writeExportedPreset(it)
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val hasLoadError by viewModel.hasLoadError.collectAsState()
    val hasSaveError by viewModel.hasSaveError.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(hasSaveError) {
        if (hasSaveError) {
            Toast.makeText(context, R.string.dolby_eq_apply_error, Toast.LENGTH_SHORT).show()
            viewModel.dismissSaveError()
        }
    }
    DolbyFirstUseHelp(
        topic = DolbyHelpTopic.EQUALIZER,
        isVisible = isCurrentPage && enabled && !isLoading && !hasLoadError,
    )
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (enabled && hasLoadError) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.dolby_eq_load_error),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.dolby_eq_retry))
                        }
                    }
                }
            } else if (enabled && isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (!enabled) {
                        Card(
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.dolby_eq_disabled_summary),
                                Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        if (showModeControl) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                EqualizerModeControl(viewModel)
                            }
                        }
                        presetStateHolder.SaveableStateProvider("presetSelector") {
                            PresetSelector(
                                viewModel = viewModel,
                                onImport = { importLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                                onExport = { viewModel.prepareExport()?.let { exportLauncher.launch(it) } },
                            )
                        }
                        EqualizerBands(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EqualizerModeControl(viewModel: EqualizerViewModel, enabled: Boolean = true) {
    val proMode by viewModel.proMode.collectAsState()
    val view = LocalView.current
    FilterChip(
        modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp).width(132.dp),
        selected = proMode,
        enabled = enabled,
        onClick = {
            view.performDolbySelectionHaptic()
            viewModel.setProMode(!proMode)
        },
        label = {
            Text(
                stringResource(if (proMode) R.string.dolby_eq_pro_active else R.string.dolby_eq_normal_mode),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (proMode) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(
            1.dp,
            if (proMode) MaterialTheme.colorScheme.primary.copy(alpha = .45f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
