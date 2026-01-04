/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import android.widget.ImageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppProfileSheet(context: Context, onDismiss: () -> Unit) {
    val manager = remember(context) { AppProfileManager(context) }
    var usageAccess by remember { mutableStateOf(manager.hasUsageAccess()) }
    var monitoring by remember { mutableStateOf(manager.isEnabled) }
    var showSetupGuide by remember { mutableStateOf(!manager.hasDismissedSetupGuide()) }
    var query by remember { mutableStateOf("") }
    var assignments by remember { mutableStateOf(manager.assignments()) }
    val apps = remember(context) { manager.installedApps() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        usageAccess = manager.hasUsageAccess()
        if (usageAccess) {
            manager.setEnabled(true)
            monitoring = true
            DolbyAppProfileService.start(context)
        }
    }
    val profileNames = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    val filteredApps = apps.filter { it.label.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.dolby_app_profiles), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.dolby_app_profiles_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showSetupGuide) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.dolby_app_profiles_setup_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            stringResource(R.string.dolby_app_profiles_setup_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        TextButton(onClick = {
                            manager.dismissSetupGuide()
                            showSetupGuide = false
                        }) {
                            Text(stringResource(R.string.dolby_app_profiles_setup_dismiss))
                        }
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.dolby_app_profiles_enable), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (usageAccess) stringResource(R.string.dolby_app_profiles_access_granted)
                            else stringResource(R.string.dolby_app_profiles_access_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = monitoring && usageAccess,
                        onCheckedChange = { checked ->
                            if (!checked) {
                                manager.setEnabled(false)
                                DolbyAppProfileService.stop(context)
                                monitoring = false
                            } else if (usageAccess) {
                                manager.setEnabled(true)
                                monitoring = true
                                DolbyAppProfileService.start(context)
                            } else {
                                launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        },
                    )
                }
            }
            if (!usageAccess) {
                TextButton(onClick = {
                    launcher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text(stringResource(R.string.dolby_app_profiles_grant_access))
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.dolby_app_profiles_search)) },
                shape = MaterialTheme.shapes.large,
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val selected = assignments[app.packageName] ?: AppProfileManager.NO_ASSIGNMENT
                    AppProfileRow(
                        app = app,
                        profileLabel = profileNames.getOrNull(profileValues.indexOf(selected.toString()))
                            ?: stringResource(R.string.dolby_app_profiles_follow_default),
                        profileNames = profileNames,
                        profileValues = profileValues,
                        onSelect = { profile ->
                            manager.setProfile(app.packageName, profile)
                            assignments = manager.assignments()
                            if (monitoring && usageAccess) DolbyAppProfileService.start(context)
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppProfileRow(
    app: DolbyApp,
    profileLabel: String,
    profileNames: Array<String>,
    profileValues: Array<String>,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageDrawable(app.icon)
                    contentDescription = app.label
                }
            },
            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium),
            update = { it.setImageDrawable(app.icon) },
        )
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(profileLabel, maxLines = 1, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dolby_app_profiles_follow_default)) },
                onClick = { expanded = false; onSelect(null) },
            )
            profileNames.forEachIndexed { index, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(profileValues.getOrNull(index)?.toIntOrNull())
                    },
                )
            }
        }
    }
}
