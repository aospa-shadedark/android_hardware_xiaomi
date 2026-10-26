/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_DIALOGUE_AMOUNT
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_STEREO_WIDENING
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.xiaomi.preference.DolbyPreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Retain confirmed UI values across Activity recreation in the persistent Dolby process.
// A reset or native-effect replacement changes the key and cannot reuse these values.
private val advancedSettingsSnapshots = mutableMapOf<Triple<Int, Long, Long>, ProfileSettings>()

/** Edits an explicit profile without selecting it for playback or changing Home. */
@Composable
internal fun AdvancedProfileEditor(
    context: Context,
    controller: DolbyController,
    enabled: Boolean,
    isCurrentPage: Boolean,
) {
    val profileIds = remember(context) {
        context.resources.getStringArray(R.array.dolby_profile_values).map { it.toInt() }
    }
    var editingProfile by rememberSaveable {
        mutableIntStateOf(
            (PreferenceManager.getDefaultSharedPreferences(context).all[PREF_PROFILE] as? String)
                ?.toIntOrNull()?.takeIf(profileIds::contains) ?: 0,
        )
    }
    var editorStarted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage, enabled) {
        if (isCurrentPage && enabled && !editorStarted) {
            // Pager precomposition can happen before the user finishes choosing Home.
            // Resolve its saved profile on the first visible visit, then retain explicit choices.
            val home = (PreferenceManager.getDefaultSharedPreferences(context).all[PREF_PROFILE] as? String)
                ?.toIntOrNull()?.takeIf(profileIds::contains) ?: 0
            editingProfile = home
            editorStarted = true
        }
    }
    val revision by controller.settingsRevision.collectAsState()
    val generation by controller.effectGeneration.collectAsState()
    val settingsKey = Triple(editingProfile, revision, generation)
    val latestKey by rememberUpdatedState(settingsKey)
    var snapshot by remember(settingsKey) { mutableStateOf(advancedSettingsSnapshots[settingsKey]) }
    var loadError by remember(settingsKey) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var localEditRevision by remember(settingsKey) { mutableIntStateOf(0) }
    var dialogueAmount by remember(settingsKey) { mutableStateOf(snapshot?.dialogueAmount?.toFloat() ?: 0f) }
    var stereoAmount by remember(settingsKey) { mutableStateOf(snapshot?.stereoAmount?.toFloat() ?: 0f) }

    SideEffect {
        snapshot?.let { settings ->
            advancedSettingsSnapshots.keys.removeAll {
                it.second != settingsKey.second || it.third != settingsKey.third
            }
            advancedSettingsSnapshots[settingsKey] = settings
        }
    }

    LaunchedEffect(settingsKey, retry, enabled) {
        loadError = false
        if (!enabled) return@LaunchedEffect
        val editsAtStart = localEditRevision
        try {
            val settings = withContext(Dispatchers.IO) {
                controller.readProfileSettings(settingsKey.first)
            }
            // The editing target can differ from the currently playing profile.
            if (latestKey != settingsKey || localEditRevision != editsAtStart ||
                controller.settingsRevision.value != settingsKey.second ||
                controller.effectGeneration.value != settingsKey.third
            ) return@LaunchedEffect
            dialogueAmount = settings.dialogueAmount.toFloat()
            stereoAmount = settings.stereoAmount.toFloat()
            snapshot = settings
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Log.e("DolbyAdvanced", "Unable to read profile ${settingsKey.first}", exception)
            if (latestKey == settingsKey && localEditRevision == editsAtStart) {
                advancedSettingsSnapshots.remove(settingsKey)
                snapshot = null
                loadError = true
            }
        }
    }

    fun applySetting(
        onApplied: () -> Unit = {},
        update: (Int, DolbyPreferenceStore) -> Unit,
    ) {
        // Capture the target of this rendered control. A reset or profile change
        // must not let a stale slider release write its old value into a new target.
        val target = settingsKey
        try {
            val applied = synchronized(controller.settingsLock) {
                if (!controller.enabled.value || snapshot == null || latestKey.first != target.first ||
                    latestKey.second != target.second || controller.settingsRevision.value != target.second
                ) return@synchronized false
                val store = DolbyPreferenceStore(context).apply { profile = target.first }
                update(target.first, store)
                true
            }
            if (applied) {
                localEditRevision++
                onApplied()
            } else retry++
        } catch (exception: Exception) {
            Log.e("DolbyAdvanced", "Unable to update profile ${target.first}", exception)
            Toast.makeText(context, R.string.dolby_settings_apply_error, Toast.LENGTH_SHORT).show()
            retry++
        }
    }

    // Recreate gesture state when changing editing target or restoring defaults.
    key(editingProfile, revision) {
        AdvancedPage(
            context = context,
            profile = editingProfile,
            onProfileSelected = { if (it in profileIds) editingProfile = it },
            enabled = enabled,
            isCurrentPage = isCurrentPage,
            settingsLoaded = snapshot != null,
            settingsLoadError = loadError,
            onRetrySettings = { retry++ },
            volume = snapshot?.volume ?: false,
            dialogue = snapshot?.dialogue ?: false,
            hpVirtualizer = snapshot?.hpVirtualizer ?: false,
            spkVirtualizer = snapshot?.spkVirtualizer ?: false,
            ieq = snapshot?.ieq ?: 0,
            dialogueAmount = dialogueAmount,
            stereoAmount = stereoAmount,
            onIeqChange = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(ieq = value) }) { target, store ->
                    controller.setIeqPreset(value, target)
                    store.putString(PREF_IEQ, value.toString())
                }
            },
            onSpeakerVirtualizerChange = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(spkVirtualizer = value) }) { target, store ->
                    controller.setSpeakerVirtEnabled(value, target)
                    store.putBoolean(PREF_SPK_VIRTUALIZER, value)
                }
            },
            onHeadphoneVirtualizerChange = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(hpVirtualizer = value) }) { target, store ->
                    controller.setHeadphoneVirtEnabled(value, target)
                    store.putBoolean(PREF_HP_VIRTUALIZER, value)
                }
            },
            onDialogueChange = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(dialogue = value) }) { target, store ->
                    controller.setDialogueEnhancerEnabled(value, target)
                    store.putBoolean(PREF_DIALOGUE, value)
                }
            },
            onVolumeChange = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(volume = value) }) { target, store ->
                    controller.setVolumeLevelerEnabled(value, target)
                    store.putBoolean(PREF_VOLUME, value)
                }
            },
            onDialogueAmountChange = {
                localEditRevision++
                dialogueAmount = it
            },
            onDialogueAmountFinished = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(dialogueAmount = value) }) { target, store ->
                    controller.setDialogueEnhancerAmount(value, target)
                    store.putInt(PREF_DIALOGUE_AMOUNT, value)
                }
            },
            onStereoAmountChange = {
                localEditRevision++
                stereoAmount = it
            },
            onStereoAmountFinished = { value ->
                applySetting(onApplied = { snapshot = snapshot?.copy(stereoAmount = value) }) { target, store ->
                    controller.setStereoWideningAmount(value, target)
                    store.putInt(PREF_STEREO_WIDENING, value)
                }
            },
        )
    }
}
