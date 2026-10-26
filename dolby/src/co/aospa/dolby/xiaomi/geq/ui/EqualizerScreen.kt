/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.DolbyFirstUseHelp
import co.aospa.dolby.xiaomi.DolbyHelpTopic

@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
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
