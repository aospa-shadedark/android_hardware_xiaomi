/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyController
import co.aospa.dolby.xiaomi.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EqualizerRepository(
    context: Context
) {

    private val context = context.applicationContext

    private val dolbyController by lazy { DolbyController.getInstance(this.context) }

    // Preset is saved as a string of comma separated gains in SharedPreferences
    // and is unique to each profile ID
    // Use the same profile snapshot as Home and the refresh flow. Reading the
    // native effect again can briefly return an older profile during a switch.
    val activeProfile: Int get() = activeProfileFlow.value
    val activeProfileFlow get() = dolbyController.activeProfile
    val settingsRevisionFlow get() = dolbyController.settingsRevision
    val settingsRevision: Long get() = settingsRevisionFlow.value
    val effectGenerationFlow get() = dolbyController.effectGeneration
    val effectGeneration: Long get() = effectGenerationFlow.value

    private fun profileSharedPrefs(profile: Int = activeProfile) =
        this.context.getSharedPreferences(
            "profile_$profile",
            Context.MODE_PRIVATE
        )

    private val presetsSharedPrefs by lazy {
        this.context.getSharedPreferences(
            "presets",
            Context.MODE_PRIVATE
        )
    }

    // Keep gain strings compatible with existing Dolby readers. This separate
    // metadata records whether a named curve was saved using the Pro editor.
    private val presetBandCounts by lazy {
        this.context.getSharedPreferences("preset_band_counts", Context.MODE_PRIVATE)
    }

    private val editorSharedPrefs by lazy {
        this.context.getSharedPreferences("equalizer_editor", Context.MODE_PRIVATE)
    }

    val proMode: Boolean get() = editorSharedPrefs.all[PREF_PRO_MODE] as? Boolean ?: false

    val proModeFlow: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PREF_PRO_MODE) trySend(proMode)
        }
        editorSharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(proMode)
        awaitClose { editorSharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setProMode(enabled: Boolean) {
        editorSharedPrefs.edit().putBoolean(PREF_PRO_MODE, enabled).apply()
    }

    val builtInPresets: List<Preset> by lazy {
        val names = this.context.resources.getStringArray(
            R.array.dolby_preset_entries
        )
        val presets = this.context.resources.getStringArray(
            R.array.dolby_preset_values
        )
        List(names.size) { index ->
            Preset(
                name = names[index],
                bandGains = EqualizerCurve.toStandard(
                    requireNotNull(EqualizerCurve.deserializeNative(presets[index]))
                ),
            )
        }
    }

    val defaultPreset by lazy { builtInPresets[0] } // Flat

    // User defined presets are stored in a SharedPreferences as
    // key - preset name
    // value - comma separated string of gains
    val userPresets: Flow<List<Preset>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            dlog(TAG, "presetsSharedPrefs changed")
            trySend(
                synchronized(presetLock) {
                    presetsSharedPrefs.all.mapNotNull { (key, value) ->
                        val serialized = value as? String ?: return@mapNotNull null
                        if (PresetCurveCodec.parseEditable(serialized) == null) {
                            Log.w(TAG, "Skipping invalid saved preset: $key")
                            return@mapNotNull null
                        }
                        Preset(
                            name = key,
                            bandGains = requireNotNull(EqualizerCurve.deserializeNative(serialized)).let { native ->
                                val standard = EqualizerCurve.toStandard(native)
                                // Metadata may be missing after an interrupted write.
                                // Never discard independent native points in that case.
                                if (presetBandCounts.all[key] == 20 || EqualizerCurve.toNative(standard) != native) {
                                    native
                                } else {
                                    standard
                                }
                            },
                            isUserDefined = true
                        )
                    }
                }
            )
        }

        presetsSharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        presetBandCounts.registerOnSharedPreferenceChangeListener(listener)
        dlog(TAG, "presetsSharedPrefs registered listener")
        // trigger an initial emission
        listener.onSharedPreferenceChanged(presetsSharedPrefs, null)

        awaitClose {
            presetsSharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            presetBandCounts.unregisterOnSharedPreferenceChangeListener(listener)
            dlog(TAG, "presetsSharedPrefs unregistered listener")
        }
    }

    suspend fun getBandGains(profile: Int = activeProfile): List<BandGain> = withContext(Dispatchers.IO) {
        synchronized(dolbyController.settingsLock) {
            val prefs = profileSharedPrefs(profile)
            val stored = prefs.all[PREF_PRESET]
            val storedGains = (stored as? String)?.let(EqualizerCurve::deserializeNative)
            if (stored != null && storedGains == null) {
                Log.w(TAG, "Removing invalid curve for profile $profile")
                prefs.edit().remove(PREF_PRESET).apply()
            }
            (storedGains ?: requireNotNull(EqualizerCurve.deserializeNative(dolbyController.getPreset(profile))) {
                "Dolby returned an invalid equalizer curve"
            }).also {
                dlog(TAG, "getBandGains: $it")
            }
        }
    }

    suspend fun setBandGains(
        bandGains: List<BandGain>,
        profile: Int,
        expectedRevision: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        synchronized(dolbyController.settingsLock) {
            // A reset invalidates edits queued before it. Do not restore an old curve.
            if (settingsRevision != expectedRevision) return@synchronized false
            dlog(TAG, "setBandGains($bandGains)")
            val gains = EqualizerCurve.serialize(bandGains)
            dolbyController.setPreset(gains, profile)
            profileSharedPrefs(profile).edit()
                .putString(PREF_PRESET, gains)
                .apply()
            true
        }
    }

    suspend fun addPreset(preset: Preset, isNew: Boolean = false) = withContext(Dispatchers.IO) {
        dlog(TAG, "addPreset($preset)")
        synchronized(presetLock) {
            val name = requireNotNull(preset.name)
            require(isNew || presetsSharedPrefs.contains(name)) { "Preset no longer exists" }
            requirePresetNameAvailable(name, excluding = name.takeUnless { isNew })
            val serialized = EqualizerCurve.serialize(preset.bandGains)
            presetBandCounts.edit().putInt(name, preset.bandGains.size).apply()
            presetsSharedPrefs.edit()
                .putString(name, serialized)
                .apply()
        }
    }

    suspend fun validatePresetSave(preset: Preset, isNew: Boolean) = withContext(Dispatchers.IO) {
        synchronized(presetLock) {
            val name = requireNotNull(preset.name)
            require(isNew || presetsSharedPrefs.contains(name)) { "Preset no longer exists" }
            requirePresetNameAvailable(name, excluding = name.takeUnless { isNew })
            EqualizerCurve.serialize(preset.bandGains)
            Unit
        }
    }

    suspend fun removePreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "removePreset($preset)")
        synchronized(presetLock) {
            presetsSharedPrefs.edit()
                .remove(preset.name)
                .apply()
            presetBandCounts.edit().remove(preset.name).apply()
        }
    }

    suspend fun renamePreset(previous: Preset, replacement: Preset) = withContext(Dispatchers.IO) {
        require(previous.isUserDefined && replacement.isUserDefined)
        synchronized(presetLock) {
            val name = requireNotNull(replacement.name)
            require(presetsSharedPrefs.contains(previous.name)) { "Preset no longer exists" }
            requirePresetNameAvailable(name, excluding = previous.name)
            val serialized = EqualizerCurve.serialize(replacement.bandGains)
            presetBandCounts.edit()
                .remove(previous.name)
                .putInt(name, replacement.bandGains.size)
                .apply()
            presetsSharedPrefs.edit()
                .remove(previous.name)
                .putString(name, serialized)
                .apply()
        }
    }

    private fun requirePresetNameAvailable(name: String, excluding: String? = null) {
        require(name.isNotBlank() && name == name.trim() && name.length <= 50) { "Invalid preset name" }
        require(builtInPresets.none { it.name?.equals(name, ignoreCase = true) == true } &&
            presetsSharedPrefs.all.none { (key, value) ->
                key != excluding && key.equals(name, ignoreCase = true) &&
                    (value as? String)?.let(PresetCurveCodec::parseEditable) != null
            }) {
            "Preset name already exists"
        }
    }

    suspend fun readPreset(uri: Uri): Preset = withContext(Dispatchers.IO) {
        PresetFileTransfer.read(context, uri)
    }

    suspend fun writePreset(uri: Uri, preset: Preset) = withContext(Dispatchers.IO) {
        PresetFileTransfer.write(context, uri, preset)
    }

    fun enqueueExport(operation: suspend () -> Unit) {
        // A document provider can be slow; exporting must not hold up EQ writes
        // or the barrier used when a profile is loaded.
        operationScope.launch { operation() }
    }

    /** Accepted writes outlive an activity, and reopening waits for those same writes. */
    fun enqueueOperation(operation: suspend () -> Unit) {
        synchronized(operationLock) {
            val previous = lastOperation
            val job = operationScope.launch(start = CoroutineStart.LAZY) {
                previous?.join()
                operation()
            }
            lastOperation = job
            job.invokeOnCompletion {
                synchronized(operationLock) {
                    if (lastOperation === job) lastOperation = null
                }
            }
            job.start()
        }
    }

    suspend fun awaitPendingOperations() {
        val pending = synchronized(operationLock) { lastOperation }
        pending?.join()
    }

    private companion object {
        const val TAG = "EqRepository"
        const val PREF_PRO_MODE = "pro_mode"

        // Only accepted operations run in this process scope. There is no idle
        // worker, and the completed tail is released so it cannot retain a screen.
        private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val operationLock = Any()
        private val presetLock = Any()
        private var lastOperation: Job? = null
    }
}
