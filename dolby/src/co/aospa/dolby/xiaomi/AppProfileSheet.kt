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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppProfileSheet(context: Context, onDismiss: () -> Unit) {
    val manager = remember(context) { AppProfileManager(context) }
    var usageAccess by remember { mutableStateOf(manager.hasUsageAccess()) }
    var monitoring by remember { mutableStateOf(manager.isEnabled) }
    var showSetupGuide by remember { mutableStateOf(!manager.hasDismissedSetupGuide()) }
    var query by remember { mutableStateOf("") }
    var assignments by remember { mutableStateOf(manager.assignments()) }
    val apps by produceState<List<DolbyApp>?>(null, manager) {
        value = withContext(Dispatchers.IO) { manager.installedApps() }
    }
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
    val filteredApps = apps.orEmpty().filter { it.label.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().fillMaxHeight(0.82f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            }
            if (apps == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            }
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
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AndroidView(
            factory = { viewContext ->
                ImageView(viewContext).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageDrawable(app.icon)
                    contentDescription = null
                }
            },
            modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.medium),
            update = { it.setImageDrawable(app.icon) },
        )
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.widthIn(max = 168.dp)) {
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
                    Text(profileLabel, modifier = Modifier.weight(1f, fill = false), maxLines = 2,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dolby_app_profiles_follow_default)) },
                    trailingIcon = if (profileLabel == stringResource(R.string.dolby_app_profiles_follow_default)) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = { expanded = false; onSelect(null) },
                )
                profileNames.forEachIndexed { index, name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        trailingIcon = if (name == profileLabel) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        onClick = {
                            expanded = false
                            onSelect(profileValues.getOrNull(index)?.toIntOrNull())
                        },
                    )
                }
            }
        }
    }
}
