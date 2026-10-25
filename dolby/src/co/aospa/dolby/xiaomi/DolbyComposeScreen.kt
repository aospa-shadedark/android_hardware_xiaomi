/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DYNAMIC_ADVANCED_GUIDE_SHOWN
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_STEREO_WIDENING
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.geq.ui.EqualizerScreen
import co.aospa.dolby.xiaomi.geq.ui.EqualizerViewModel
import co.aospa.dolby.xiaomi.preference.DolbyPreferenceStore

private enum class DolbyDestination { HOME, EQUALIZER, ADVANCED }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DolbyComposeScreen(
    context: Context,
    controller: DolbyController,
    equalizerViewModel: EqualizerViewModel,
    onResetAll: () -> Unit,
) {
    val pagerState = rememberPagerState {
        DolbyDestination.values().size
    }
    val destination = DolbyDestination.values()[pagerState.currentPage]
    val navigationScope = rememberCoroutineScope()
    val pageStateHolder = rememberSaveableStateHolder()
    val hapticView = LocalView.current
    var showReset by remember { mutableStateOf(false) }
    var showAppProfiles by remember { mutableStateOf(false) }
    val enabled by controller.enabled.collectAsState()
    val profile by controller.activeProfile.collectAsState()
    val settingsRevision by controller.settingsRevision.collectAsState()
    var dynamicAdvancedGuideDismissed by remember(context) {
        mutableStateOf(
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(PREF_DYNAMIC_ADVANCED_GUIDE_SHOWN, false),
        )
    }
    LaunchedEffect(pagerState, hapticView) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .drop(1)
            .collect { hapticView.performDolbySelectionHaptic() }
    }

    val store = remember(context, profile) {
        DolbyPreferenceStore(context).apply { this.profile = profile }
    }
    val profileIds = remember(context) {
        context.resources.getStringArray(R.array.dolby_profile_values)
            .mapNotNull { it.toIntOrNull() }
    }
    fun updateAdvancedSettingForAllProfiles(update: (Int, DolbyPreferenceStore) -> Unit) {
        profileIds.forEach { profileId ->
            val profileStore = DolbyPreferenceStore(context).apply { this.profile = profileId }
            update(profileId, profileStore)
        }
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

    LaunchedEffect(profile, settingsRevision) {
        bass = controller.getBassEnhancerEnabled(profile)
        volume = controller.getVolumeLevelerEnabled(profile)
        dialogue = controller.getDialogueEnhancerEnabled(profile)
        hpVirtualizer = controller.getHeadphoneVirtEnabled(profile)
        spkVirtualizer = controller.getSpeakerVirtEnabled(profile)
        ieq = controller.getIeqPreset(profile)
        dialogueAmount = controller.getDialogueEnhancerAmount(profile).toFloat()
        stereoAmount = controller.getStereoWideningAmount(profile).toFloat()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(destinationTitle)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (destination == DolbyDestination.HOME) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            IconButton(
                                onClick = { showReset = true },
                                modifier = Modifier.height(42.dp).width(42.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.dolby_reset_all),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    if (destination == DolbyDestination.EQUALIZER) {
                        IconButton(onClick = { equalizerGuideDismissed = false }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.dolby_help),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (destination == DolbyDestination.ADVANCED && profile == 0) {
                        IconButton(onClick = { dynamicAdvancedGuideDismissed = false }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.dolby_help),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                val page = DolbyDestination.values()[pageIndex]
                pageStateHolder.SaveableStateProvider(page.name) {
                    when (page) {
                        DolbyDestination.HOME -> HomePage(
                            enabled = enabled,
                            profile = profile,
                            bass = bass,
                            onAppProfilesClick = { showAppProfiles = true },
                            onEnabledChange = { value ->
                                controller.setDsOnAndPersist(value)
                                val appProfiles = AppProfileManager(context)
                                if (appProfiles.isEnabled && appProfiles.hasUsageAccess()) {
                                    if (value) DolbyAppProfileService.start(context)
                                    else DolbyAppProfileService.stop(context)
                                }
                            },
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
                            showDynamicGuide = profile == 0 && !dynamicAdvancedGuideDismissed,
                            onDismissDynamicGuide = {
                                PreferenceManager.getDefaultSharedPreferences(context).edit()
                                    .putBoolean(PREF_DYNAMIC_ADVANCED_GUIDE_SHOWN, true)
                                    .apply()
                                dynamicAdvancedGuideDismissed = true
                            },
                            volume = volume,
                            dialogue = dialogue,
                            hpVirtualizer = hpVirtualizer,
                            spkVirtualizer = spkVirtualizer,
                            ieq = ieq,
                            dialogueAmount = dialogueAmount,
                            stereoAmount = stereoAmount,
                            onIeqChange = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setIeqPreset(value, targetProfile)
                                    targetStore.putString(PREF_IEQ, value.toString())
                                }
                                ieq = value
                            },
                            onSpeakerVirtualizerChange = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setSpeakerVirtEnabled(value, targetProfile)
                                    targetStore.putBoolean(PREF_SPK_VIRTUALIZER, value)
                                }
                                spkVirtualizer = value
                            },
                            onHeadphoneVirtualizerChange = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setHeadphoneVirtEnabled(value, targetProfile)
                                    targetStore.putBoolean(PREF_HP_VIRTUALIZER, value)
                                }
                                hpVirtualizer = value
                            },
                            onDialogueChange = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setDialogueEnhancerEnabled(value, targetProfile)
                                    targetStore.putBoolean(PREF_DIALOGUE, value)
                                }
                                dialogue = value
                            },
                            onVolumeChange = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setVolumeLevelerEnabled(value, targetProfile)
                                    targetStore.putBoolean(PREF_VOLUME, value)
                                }
                                volume = value
                            },
                            onDialogueAmountChange = { dialogueAmount = it },
                            onDialogueAmountFinished = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setDialogueEnhancerAmount(value, targetProfile)
                                    targetStore.putInt(PREF_DIALOGUE_AMOUNT, value)
                                }
                            },
                            onStereoAmountChange = { stereoAmount = it },
                            onStereoAmountFinished = { value ->
                                updateAdvancedSettingForAllProfiles { targetProfile, targetStore ->
                                    controller.setStereoWideningAmount(value, targetProfile)
                                    targetStore.putInt(PREF_STEREO_WIDENING, value)
                                }
                            },
                        )
                    }
                }
            }
            DolbyBottomBar(
                pagerState = pagerState,
                onSelect = { selected ->
                    navigationScope.launch { pagerState.animateScrollToPage(selected.ordinal) }
                },
            )
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

    if (showAppProfiles) {
        AppProfileSheet(context = context, onDismiss = { showAppProfiles = false })
    }
}

@Composable
private fun DolbyBottomBar(
    pagerState: PagerState,
    onSelect: (DolbyDestination) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(5.dp)) {
            val itemWidth = maxWidth / DolbyDestination.values().size
            val indicatorWidth = (itemWidth - 8.dp).coerceAtLeast(0.dp)
            val itemWidthPx = with(density) { itemWidth.toPx() }.coerceAtLeast(1f)
            val dragScale by rememberUpdatedState(
                pagerState.layoutInfo.pageSize / itemWidthPx *
                    if (layoutDirection == LayoutDirection.Rtl) -1f else 1f,
            )
            val dragState = remember(pagerState) {
                object : DraggableState {
                    override fun dispatchRawDelta(delta: Float) {
                        pagerState.dispatchRawDelta(delta * dragScale)
                    }

                    override suspend fun drag(
                        dragPriority: MutatePriority,
                        block: suspend DragScope.() -> Unit,
                    ) {
                        // Share the pager's scroll lock so dragging interrupts a tab animation.
                        pagerState.scroll(dragPriority) {
                            val pagerScroll = this
                            val dragScope = object : DragScope {
                                override fun dragBy(pixels: Float) {
                                    pagerScroll.scrollBy(pixels * dragScale)
                                }
                            }
                            dragScope.block()
                        }
                    }
                }
            }
            val boundedPosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                .coerceIn(0f, DolbyDestination.values().lastIndex.toFloat())
            val travelFraction = (abs(boundedPosition - boundedPosition.roundToInt()) * 2f)
                .coerceIn(0f, 1f)
            Box(
                Modifier.fillMaxWidth().draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val target = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                            .roundToInt().coerceIn(0, DolbyDestination.values().lastIndex)
                        pagerState.animateScrollToPage(target)
                    },
                ),
            ) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .offset(x = itemWidth * boundedPosition + 4.dp)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp)
                            .graphicsLayer { scaleX = 1f + travelFraction * 0.12f }
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                    )
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    val activeIndex = boundedPosition.roundToInt()
                    BottomBarItem(Modifier.weight(1f), DolbyDestination.HOME, activeIndex, R.drawable.ic_dolby, R.string.dolby_nav_home, onSelect)
                    BottomBarItem(Modifier.weight(1f), DolbyDestination.EQUALIZER, activeIndex, R.drawable.ic_equalizer_24, R.string.dolby_nav_equalizer, onSelect)
                    BottomBarItem(Modifier.weight(1f), DolbyDestination.ADVANCED, activeIndex, R.drawable.ic_tune_24, R.string.dolby_nav_advanced, onSelect)
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    modifier: Modifier,
    item: DolbyDestination,
    selectedIndex: Int,
    icon: Int,
    label: Int,
    onSelect: (DolbyDestination) -> Unit,
) {
    val active = item.ordinal == selectedIndex
    val iconColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "navigation item content color",
    )
    Column(
        modifier = modifier
            .selectable(
                selected = active,
                role = Role.Tab,
                indication = null,
                onClick = { onSelect(item) },
            )
            .fillMaxHeight()
            .heightIn(min = 58.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.height(20.dp),
        )
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = iconColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomePage(
    enabled: Boolean,
    profile: Int,
    bass: Boolean,
    onAppProfilesClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onProfileSelected: (Int) -> Unit,
    onBassChange: (Boolean) -> Unit,
) {
    val hapticView = LocalView.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val iconContainer by animateColorAsState(
                    targetValue = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    label = "Dolby power icon container",
                )
                val iconTint by animateColorAsState(
                    targetValue = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "Dolby power icon",
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = iconContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dolby),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.padding(11.dp).height(24.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        stringResource(R.string.dolby_enable),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(
                            if (enabled) R.string.dolby_enabled_summary
                            else R.string.dolby_powered_off_summary,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { value ->
                        hapticView.performDolbyToggleHaptic(value)
                        onEnabledChange(value)
                    },
                )
            }
        }
        if (enabled) {
            ProfilePicker(profile = profile, onSelected = onProfileSelected)
            Card(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAppProfilesClick),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.height(38.dp).width(38.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_app_profiles_24),
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp).fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.dolby_app_profiles), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.dolby_app_profiles_card_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.dolby_category_settings),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
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
@OptIn(ExperimentalMaterial3Api::class)
private fun ProfilePicker(profile: Int, onSelected: (Int) -> Unit) {
    val hapticView = LocalView.current
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
    val index = values.indexOf(profile.toString()).takeIf { it >= 0 } ?: 0
    var showProfiles by remember { mutableStateOf(false) }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth()) {
            Surface(
                onClick = { showProfiles = true },
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 102.dp).padding(horizontal = 16.dp, vertical = 14.dp),
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
                        shape = RoundedCornerShape(24.dp),
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
        }
    }
    if (showProfiles) {
        ModalBottomSheet(onDismissRequest = { showProfiles = false }) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.dolby_profile_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                entries.forEachIndexed { i, label ->
                    val profileValue = values.getOrNull(i)?.toIntOrNull() ?: return@forEachIndexed
                    val selected = profileValue == profile
                    val description = when (profileValue) {
                        0 -> R.string.dolby_profile_dynamic_summary
                        1 -> R.string.dolby_profile_video_summary
                        2 -> R.string.dolby_profile_music_summary
                        8 -> R.string.dolby_profile_voice_summary
                        4 -> R.string.dolby_profile_spatial_summary
                        else -> R.string.dolby_profile_custom_summary
                    }
                    Surface(
                        onClick = {
                            hapticView.performDolbySelectionHaptic()
                            showProfiles = false
                            onSelected(profileValue)
                        },
                        shape = MaterialTheme.shapes.large,
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.primary,
                            ) {
                                Icon(
                                    painterResource(ids[i]),
                                    contentDescription = null,
                                    modifier = Modifier.padding(10.dp).height(24.dp).width(24.dp),
                                )
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AdvancedPage(
    context: Context,
    enabled: Boolean,
    profile: Int,
    showDynamicGuide: Boolean,
    onDismissDynamicGuide: () -> Unit,
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
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (profile == 0 && showDynamicGuide) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.dolby_dynamic_controls_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(
                        onClick = onDismissDynamicGuide,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.dolby_dynamic_controls_dismiss))
                    }
                }
            }
        }
        if (!enabled) {
            InfoCard(stringResource(R.string.dolby_advanced_disabled))
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
                    if (context.resources.getBoolean(R.bool.dolby_stereo_widening_supported)) {
                        StrengthSettingContent(
                            title = stringResource(R.string.dolby_stereo_widening),
                            value = stereoAmount,
                            range = context.resources.getInteger(R.integer.stereo_widening_min).toFloat()..
                                context.resources.getInteger(R.integer.stereo_widening_max).toFloat(),
                            enabled = hpVirtualizer,
                            onValueChange = onStereoAmountChange,
                            onFinished = onStereoAmountFinished,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    SwitchSettingRow(stringResource(R.string.dolby_dialogue_enhancer), dialogue, onDialogueChange)
                    StrengthSettingContent(
                        title = stringResource(R.string.dolby_dialogue_strength),
                        value = dialogueAmount,
                        range = context.resources.getInteger(R.integer.dialogue_enhancer_min).toFloat()..
                            context.resources.getInteger(R.integer.dialogue_enhancer_max).toFloat(),
                        enabled = dialogue,
                        onValueChange = onDialogueAmountChange,
                        onFinished = onDialogueAmountFinished,
                    )
                    if (context.resources.getBoolean(R.bool.dolby_volume_leveler_supported)) {
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SwitchSettingRow(stringResource(R.string.dolby_volume_leveler), volume, onVolumeChange)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.dolby_advanced_shared_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.dolby_advanced_reset_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
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
    val hapticView = LocalView.current
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
            entries.indices.toList().chunked(2).forEach { rowIndices ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowIndices.forEach { i ->
                        val isSelected = i == index
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    hapticView.performDolbySelectionHaptic()
                                    onSelected(i)
                                },
                            ),
                            shape = MaterialTheme.shapes.large,
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.outlineVariant,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(entries[i], style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f), maxLines = 2)
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hapticView = LocalView.current
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = { value ->
                hapticView.performDolbyToggleHaptic(value)
                onCheckedChange(value)
            },
        )
            .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SwitchSettingRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val hapticView = LocalView.current
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).toggleable(
        value = checked,
        role = Role.Switch,
        onValueChange = { value ->
            hapticView.performDolbyToggleHaptic(value)
            onCheckedChange(value)
        },
    )
        .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun StrengthSettingContent(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onFinished: (Int) -> Unit,
) {
    val displayedValue = value.coerceIn(range).roundToInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (enabled) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    displayedValue.toString(),
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        DolbySlider(value = displayedValue.toFloat(), onValueChange = { onValueChange(it.roundToInt().toFloat()) },
            valueRange = range, enabled = enabled,
            steps = (range.endInclusive - range.start).roundToInt().minus(1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                .semantics { contentDescription = title },
            onValueChangeFinished = { onFinished(displayedValue) })
    }
}
