/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_EQ_BAND_GUIDE_SHOWN
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
    val context = LocalContext.current
    var showBandGuide by remember(context) {
        mutableStateOf(
            !PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_EQ_BAND_GUIDE_SHOWN, false),
        )
    }
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
                if (showBandGuide) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                stringResource(R.string.dolby_eq_band_guide),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            TextButton(
                                onClick = {
                                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                                        .putBoolean(PREF_EQ_BAND_GUIDE_SHOWN, true)
                                        .apply()
                                    showBandGuide = false
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(stringResource(R.string.dolby_dynamic_controls_dismiss))
                            }
                        }
                    }
                }
                PresetSelector(viewModel = viewModel)
                EqualizerBands(viewModel = viewModel)
            }
        }
    }
}
