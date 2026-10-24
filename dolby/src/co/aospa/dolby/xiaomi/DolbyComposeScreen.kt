/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_STEREO_WIDENING
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.geq.ui.EqualizerScreen
import co.aospa.dolby.xiaomi.geq.ui.EqualizerViewModel
import co.aospa.dolby.xiaomi.preference.DolbyPreferenceStore
import com.android.settingslib.spa.framework.theme.settingsBackground

private enum class DolbyDestination { HOME, EQUALIZER, ADVANCED }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DolbyComposeScreen(
    context: Context,
    controller: DolbyController,
    equalizerViewModel: EqualizerViewModel,
    onResetAll: () -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(DolbyDestination.HOME) }
    var showReset by remember { mutableStateOf(false) }
    val enabled by controller.enabled.collectAsState()
    val profile by controller.activeProfile.collectAsState()
    var refreshToken by remember { mutableStateOf(0) }

    val store = remember(context, profile) {
        DolbyPreferenceStore(context).apply { this.profile = profile }
    }
    var bass by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(false) }
    var dialogue by remember { mutableStateOf(false) }
    var hpVirtualizer by remember { mutableStateOf(false) }
    var spkVirtualizer by remember { mutableStateOf(false) }
    var ieq by remember { mutableStateOf(0) }
    var dialogueAmount by remember { mutableStateOf(0f) }
    var stereoAmount by remember { mutableStateOf(0f) }
    val destinationTitle = when (destination) {
        DolbyDestination.HOME -> R.string.dolby_nav_home
        DolbyDestination.EQUALIZER -> R.string.dolby_nav_equalizer
        DolbyDestination.ADVANCED -> R.string.dolby_nav_advanced
    }

    LaunchedEffect(profile, refreshToken) {
        bass = controller.getBassEnhancerEnabled(profile)
        volume = controller.getVolumeLevelerEnabled(profile)
        if (profile == 0) {
            dialogue = false
            hpVirtualizer = false
            spkVirtualizer = false
            ieq = 0
            dialogueAmount = 0f
            stereoAmount = 0f
        } else {
            dialogue = controller.getDialogueEnhancerEnabled(profile)
            hpVirtualizer = controller.getHeadphoneVirtEnabled(profile)
            spkVirtualizer = controller.getSpeakerVirtEnabled(profile)
            ieq = controller.getIeqPreset(profile)
            dialogueAmount = controller.getDialogueEnhancerAmount(profile).toFloat()
            stereoAmount = controller.getStereoWideningAmount(profile).toFloat()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.settingsBackground,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(destinationTitle)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.settingsBackground,
                ),
                actions = {
                    if (destination == DolbyDestination.HOME) {
                        IconButton(onClick = { showReset = true }) {
                            Icon(
                                painter = painterResource(R.drawable.reset_wrench_24px),
                                contentDescription = stringResource(R.string.dolby_reset_all),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInHorizontally(tween(240)) { it / 12 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { -it / 16 })
                },
                label = "Dolby destination",
            ) { page ->
                when (page) {
                    DolbyDestination.HOME -> HomePage(
                        enabled = enabled,
                        profile = profile,
                        bass = bass,
                        onEnabledChange = controller::setDsOnAndPersist,
                        onProfileSelected = { selected ->
                            controller.profile = selected
                            PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putString(PREF_PROFILE, selected.toString()).apply()
                        },
                        onBassChange = { value ->
                            controller.setBassEnhancerEnabled(value, profile)
                            store.putBoolean(PREF_BASS, value)
                            bass = value
                        },
                    )
                    DolbyDestination.EQUALIZER -> EqualizerScreen(
                        viewModel = equalizerViewModel,
                        enabled = enabled,
                        modifier = Modifier.fillMaxSize(),
                    )
                    DolbyDestination.ADVANCED -> AdvancedPage(
                        context = context,
                        enabled = enabled,
                        profile = profile,
                        volume = volume,
                        dialogue = dialogue,
                        hpVirtualizer = hpVirtualizer,
                        spkVirtualizer = spkVirtualizer,
                        ieq = ieq,
                        dialogueAmount = dialogueAmount,
                        stereoAmount = stereoAmount,
                        onIeqChange = { value ->
                            controller.setIeqPreset(value, profile)
                            store.putString(PREF_IEQ, value.toString())
                            ieq = value
                        },
                        onSpeakerVirtualizerChange = { value ->
                            controller.setSpeakerVirtEnabled(value, profile)
                            store.putBoolean(PREF_SPK_VIRTUALIZER, value)
                            spkVirtualizer = value
                        },
                        onHeadphoneVirtualizerChange = { value ->
                            controller.setHeadphoneVirtEnabled(value, profile)
                            store.putBoolean(PREF_HP_VIRTUALIZER, value)
                            hpVirtualizer = value
                        },
                        onDialogueChange = { value ->
                            controller.setDialogueEnhancerEnabled(value, profile)
                            store.putBoolean(PREF_DIALOGUE, value)
                            dialogue = value
                        },
                        onVolumeChange = { value ->
                            controller.setVolumeLevelerEnabled(value, profile)
                            store.putBoolean(PREF_VOLUME, value)
                            volume = value
                        },
                        onDialogueAmountChange = { dialogueAmount = it },
                        onDialogueAmountFinished = { value ->
                            controller.setDialogueEnhancerAmount(value, profile)
                            store.putInt(PREF_DIALOGUE_AMOUNT, value)
                        },
                        onStereoAmountChange = { stereoAmount = it },
                        onStereoAmountFinished = { value ->
                            controller.setStereoWideningAmount(value, profile)
                            store.putInt(PREF_STEREO_WIDENING, value)
                        },
                    )
                }
            }
            DolbyBottomBar(destination = destination, onSelect = { destination = it })
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text(stringResource(R.string.dolby_reset_all)) },
            text = { Text(stringResource(R.string.dolby_reset_all_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onResetAll()
                    equalizerViewModel.refresh()
                    refreshToken++
                    showReset = false
                }) { Text(stringResource(R.string.dolby_reset_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DolbyBottomBar(destination: DolbyDestination, onSelect: (DolbyDestination) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(Modifier.weight(1f), DolbyDestination.HOME, destination, R.drawable.ic_dolby, R.string.dolby_nav_home, onSelect)
            BottomBarItem(Modifier.weight(1f), DolbyDestination.EQUALIZER, destination, R.drawable.ic_equalizer_24, R.string.dolby_nav_equalizer, onSelect)
            BottomBarItem(Modifier.weight(1f), DolbyDestination.ADVANCED, destination, R.drawable.ic_tune_24, R.string.dolby_nav_advanced, onSelect)
        }
    }
}

@Composable
private fun BottomBarItem(
    modifier: Modifier,
    item: DolbyDestination,
    selected: DolbyDestination,
    icon: Int,
    label: Int,
    onSelect: (DolbyDestination) -> Unit,
) {
    val active = item == selected
    val itemColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        label = "navigation item color",
    )
    val iconSize by animateDpAsState(
        targetValue = if (active) 22.dp else 20.dp,
        label = "navigation icon size",
    )
    Surface(
        modifier = modifier.padding(horizontal = 2.dp),
        shape = RoundedCornerShape(20.dp),
        color = itemColor,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .selectable(
                    selected = active,
                    role = Role.Tab,
                    onClick = { onSelect(item) },
                )
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(iconSize),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomePage(
    enabled: Boolean,
    profile: Int,
    bass: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onBassChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.dolby_enable),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }
        }
        if (enabled) {
            ProfilePicker(profile = profile, onSelected = onProfileSelected)
            Text(stringResource(R.string.dolby_category_settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp))
            SwitchSetting(
                title = stringResource(R.string.dolby_bass_enhancer),
                summary = stringResource(R.string.dolby_bass_summary),
                checked = bass,
                onCheckedChange = onBassChange,
            )
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Text(stringResource(R.string.dolby_disabled_summary), Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfilePicker(profile: Int, onSelected: (Int) -> Unit) {
    val entries = stringArrayResource(R.array.dolby_profile_entries)
    val values = stringArrayResource(R.array.dolby_profile_values)
    val ids = intArrayOf(
        R.drawable.ic_dolby_profile_dynamic,
        R.drawable.ic_dolby_profile_video,
        R.drawable.ic_dolby_profile_music,
        R.drawable.ic_dolby_profile_voice,
        R.drawable.ic_dolby_profile_spatial,
        R.drawable.ic_dolby_profile_custom,
    )
    val index = values.indexOf(profile.toString()).coerceAtLeast(0)
    var expanded by remember { mutableStateOf(false) }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Surface(
                onClick = { expanded = true },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(painterResource(ids[index]), null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dolby_profile_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entries[index], style = MaterialTheme.typography.titleLarge)
                    }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.dolby_profile_change),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { Icon(painterResource(ids[i]), null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = {
                            expanded = false
                            values.getOrNull(i)?.toIntOrNull()?.let(onSelected)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedPage(
    context: Context,
    enabled: Boolean,
    profile: Int,
    volume: Boolean,
    dialogue: Boolean,
    hpVirtualizer: Boolean,
    spkVirtualizer: Boolean,
    ieq: Int,
    dialogueAmount: Float,
    stereoAmount: Float,
    onIeqChange: (Int) -> Unit,
    onSpeakerVirtualizerChange: (Boolean) -> Unit,
    onHeadphoneVirtualizerChange: (Boolean) -> Unit,
    onDialogueChange: (Boolean) -> Unit,
    onVolumeChange: (Boolean) -> Unit,
    onDialogueAmountChange: (Float) -> Unit,
    onDialogueAmountFinished: (Int) -> Unit,
    onStereoAmountChange: (Float) -> Unit,
    onStereoAmountFinished: (Int) -> Unit,
) {
    val profileEntries = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values)
    val profileIndex = profileValues.indexOf(profile.toString())
    val profileName = profileEntries.getOrNull(profileIndex) ?: stringResource(R.string.dolby_unknown)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dolby_profile_context, profileName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!enabled) {
            InfoCard(stringResource(R.string.dolby_advanced_disabled))
        } else if (profile == 0) {
            InfoCard(stringResource(R.string.dolby_dynamic_controls_summary))
        } else {
            val ieqEntries = stringArrayResource(R.array.dolby_ieq_entries)
            val ieqValues = stringArrayResource(R.array.dolby_ieq_values)
            ChoiceSetting(
                title = stringResource(R.string.dolby_ieq),
                summary = stringResource(R.string.dolby_ieq_summary),
                selected = ieq,
                entries = ieqEntries,
                values = ieqValues,
                onSelected = { ieqValues.getOrNull(it)?.toIntOrNull()?.let(onIeqChange) },
            )
            Card(shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    SwitchSettingRow(stringResource(R.string.dolby_spk_virtualizer), spkVirtualizer, onSpeakerVirtualizerChange)
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchSettingRow(stringResource(R.string.dolby_hp_virtualizer), hpVirtualizer, onHeadphoneVirtualizerChange)
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchSettingRow(stringResource(R.string.dolby_dialogue_enhancer), dialogue, onDialogueChange)
                    if (context.resources.getBoolean(R.bool.dolby_volume_leveler_supported)) {
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchSettingRow(stringResource(R.string.dolby_volume_leveler), volume, onVolumeChange)
                    }
                }
            }
            StrengthSetting(
                title = stringResource(R.string.dolby_dialogue_strength),
                value = dialogueAmount,
                range = context.resources.getInteger(R.integer.dialogue_enhancer_min).toFloat()..
                    context.resources.getInteger(R.integer.dialogue_enhancer_max).toFloat(),
                enabled = dialogue,
                onValueChange = onDialogueAmountChange,
                onFinished = onDialogueAmountFinished,
            )
            if (context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)) {
                StrengthSetting(
                    title = stringResource(R.string.dolby_stereo_widening),
                    value = stereoAmount,
                    range = context.resources.getInteger(R.integer.stereo_widening_min).toFloat()..
                        context.resources.getInteger(R.integer.stereo_widening_max).toFloat(),
                    enabled = hpVirtualizer,
                    onValueChange = onStereoAmountChange,
                    onFinished = onStereoAmountFinished,
                )
            }
        }
    }
}

@Composable
private fun InfoCard(message: String) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(message, Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    summary: String? = null,
    selected: Int,
    entries: Array<String>,
    values: Array<String>,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val index = values.indexOf(selected.toString()).coerceAtLeast(0)
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            summary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(onClick = { expanded = true }, shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(entries.getOrElse(index) { "" }, style = MaterialTheme.typography.bodyLarge)
                    Icon(
                        painter = painterResource(R.drawable.ic_expand_more_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                entries.forEachIndexed { i, label ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        expanded = false
                        onSelected(i)
                    })
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
            .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SwitchSettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(
        value = checked,
        role = Role.Switch,
        onValueChange = onCheckedChange,
    )
        .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StrengthSetting(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: (Int) -> Unit,
) {
    val displayedValue = value.coerceIn(range).roundToInt()
    val integerSteps = (range.endInclusive - range.start).roundToInt().coerceAtLeast(1) - 1
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(displayedValue.toString(), style = MaterialTheme.typography.labelLarge)
            }
            Slider(value = displayedValue.toFloat(), onValueChange = { onValueChange(it.roundToInt().toFloat()) },
                valueRange = range, enabled = enabled,
                steps = integerSteps,
                onValueChangeFinished = { onFinished(displayedValue) })
        }
    }
}
