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
                bandGains = deserializeGains(presets[index]),
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
                presetsSharedPrefs.all.map { (key, value) ->
                    Preset(
                        name = key,
                        bandGains = deserializeGains(value.toString()),
                        isUserDefined = true
                    )
                }
            )
        }

        presetsSharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        dlog(TAG, "presetsSharedPrefs registered listener")
        // trigger an initial emission
        listener.onSharedPreferenceChanged(presetsSharedPrefs, null)

        awaitClose {
            presetsSharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
            dlog(TAG, "presetsSharedPrefs unregistered listener")
        }
    }

    suspend fun getBandGains(profile: Int = activeProfile): List<BandGain> = withContext(Dispatchers.IO) {
        synchronized(dolbyController.settingsLock) {
            val gains = profileSharedPrefs(profile).getString(PREF_PRESET, null)
                ?: dolbyController.getPreset(profile)
            if (gains.isNullOrEmpty()) {
                defaultPreset.bandGains
            } else {
                deserializeGains(gains)
            }.also {
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
            val gains = serializeGains(bandGains)
            dolbyController.setPreset(gains, profile)
            profileSharedPrefs(profile).edit()
                .putString(PREF_PRESET, gains)
                .apply()
            true
        }
    }

    suspend fun addPreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "addPreset($preset)")
        presetsSharedPrefs.edit()
            .putString(preset.name, serializeGains(preset.bandGains))
            .apply()
    }

    suspend fun removePreset(preset: Preset) = withContext(Dispatchers.IO) {
        dlog(TAG, "removePreset($preset)")
        presetsSharedPrefs.edit()
            .remove(preset.name)
            .apply()
    }

    suspend fun renamePreset(previous: Preset, replacement: Preset) = withContext(Dispatchers.IO) {
        require(previous.isUserDefined && replacement.isUserDefined)
        presetsSharedPrefs.edit()
            .remove(previous.name)
            .putString(replacement.name, serializeGains(replacement.bandGains))
            .apply()
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

        // Only accepted operations run in this process scope. There is no idle
        // worker, and the completed tail is released so it cannot retain a screen.
        private val operationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val operationLock = Any()
        private var lastOperation: Job? = null

        val tenBandFreqs = intArrayOf(
            32,
            64,
            125,
            250,
            500,
            1000,
            2000,
            4000,
            8000,
            16000
        )

        fun deserializeGains(bandGains: String): List<BandGain> {
            val gains: List<Int> =
                bandGains.split(",").runCatching {
                    require(size == 20) {
                        "Preset must have 20 elements, has only $size!"
                    }
                    map { it.toInt() }
                        .twentyToTenBandGains()
                }.onFailure { exception ->
                    Log.e(TAG, "Failed to parse preset", exception)
                }.getOrDefault(
                    // fallback to flat
                    List<Int>(10) { 0 }
                )
            return List(10) { index ->
                BandGain(
                    band = tenBandFreqs[index],
                    gain = gains[index]
                )
            }
        }

        fun serializeGains(bandGains: List<BandGain>): String {
            return bandGains.map { it.gain }
                .tenToTwentyBandGains()
                .joinToString(",")
        }

        // we show only 10 bands in UI however backend requires 20 bands
        fun List<Int>.tenToTwentyBandGains() =
            List<Int>(20) { index ->
                if (index % 2 == 1 && index < 19) {
                    // every odd element is the average of its surrounding elements
                    (this[(index - 1) / 2] + this[(index + 1) / 2]) / 2
                } else {
                    this[index / 2]
                }
            }

        fun List<Int>.twentyToTenBandGains() =
            // skip every odd element
            filterIndexed { index, _ -> index % 2 == 0 }
    }
}
