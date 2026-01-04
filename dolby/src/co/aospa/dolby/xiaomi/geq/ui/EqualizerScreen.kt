/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import co.aospa.dolby.xiaomi.R
import com.android.settingslib.spa.framework.theme.settingsBackground

@Composable
fun EqualizerScreen(
    viewModel: EqualizerViewModel,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val profileEntries = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    val profileName = profileEntries.getOrNull(profileValues.indexOf(activeProfile.toString()))
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.settingsBackground) {
        if (!enabled) {
            Card(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                profileName?.let {
                    Text(
                        stringResource(R.string.dolby_profile_context, it),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PresetSelector(viewModel = viewModel)
                EqualizerBands(viewModel = viewModel)
            }
        }
    }
}
