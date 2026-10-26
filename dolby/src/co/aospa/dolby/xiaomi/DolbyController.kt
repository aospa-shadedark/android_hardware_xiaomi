/*
 * Copyright (C) 2023-25 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.DolbyConstants.DsParam
import co.aospa.dolby.xiaomi.R
import co.aospa.dolby.xiaomi.geq.data.PresetCurveCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ProfileSettings(
    val bass: Boolean,
    val volume: Boolean,
    val dialogue: Boolean,
    val hpVirtualizer: Boolean,
    val spkVirtualizer: Boolean,
    val ieq: Int,
    val dialogueAmount: Int,
    val stereoAmount: Int,
)

internal class DolbyController private constructor(
    private val context: Context
) {
    // Serialize native access with effect replacement and queued profile edits.
    internal val settingsLock = Any()
    private var dolbyEffect: DolbyAudioEffect? = null
    private var startupResetPending = true
    private val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val profileIds = context.resources.getStringArray(R.array.dolby_profile_values)
        .map { it.toInt() }
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val handler = Handler(context.mainLooper)
    private val routeMonitor = DolbyRouteMonitor(context) {
        restoreCurrentProfileSafely()
    }
    private val stereoWideningSupported =
        context.getResources().getBoolean(R.bool.dolby_stereo_widening_supported)
    private val volumeLevelerSupported =
        context.getResources().getBoolean(R.bool.dolby_volume_leveler_supported)

    // Before the first successful connection these retain the saved desired state.
    // Native operations still fail explicitly until the effect is available.
    private val _enabled = MutableStateFlow(readSavedPower())
    val enabled = _enabled.asStateFlow()
    private val _activeProfile = MutableStateFlow(readSavedHomeProfile())
    val activeProfile = _activeProfile.asStateFlow()
    private val _settingsRevision = MutableStateFlow(0L)
    internal val settingsRevision = _settingsRevision.asStateFlow()
    private val _effectGeneration = MutableStateFlow(0L)
    internal val effectGeneration = _effectGeneration.asStateFlow()
    private var appProfileOverride: Int? = null

    // Restore current profile on every media session
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val isPlaying = configs.any {
                it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            }
            dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
            if (isPlaying)
                restoreCurrentProfileSafely()
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            restoreCurrentProfileSafely()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            restoreCurrentProfileSafely()
        }
    }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                try {
                    audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
                    routeMonitor.start()
                } catch (exception: RuntimeException) {
                    routeMonitor.stop()
                    audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
                    audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                    throw exception
                }
            } else {
                routeMonitor.stop()
                audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
            field = value
        }

    private var recoveryRetryDelay = RECOVERY_RETRY_MS
    private var recoveryRetryScheduled = false
    private var recoveryRetryAt = 0L
    private var lastRecoveryFailure: RuntimeException? = null
    private val profileRestoreRetry = Runnable {
        synchronized(settingsLock) {
            recoveryRetryScheduled = false
            recoveryRetryAt = 0L
        }
        restoreCurrentProfileSafely()
    }

    var dsOn: Boolean
        get() = withEffect { effect ->
            effect.dsOn.also {
                dlog(TAG, "getDsOn: $it")
            }
        }
        set(value) {
            setPower(value, persist = false)
        }

    var profile: Int
        get() = withEffect { effect ->
            effect.profile.also {
                dlog(TAG, "getProfile: $it")
            }
        }
        set(value) {
            synchronized(settingsLock) {
                dlog(TAG, "setProfile: $value")
                appProfileOverride = null
                applyProfile(value)
            }
        }

    init {
        restoreCurrentProfileSafely()
    }

    fun onUserUnlocked() {
        dlog(TAG, "onUserUnlocked()")

        // Migrate presets from credential protected storage if needed
        maybeMigratePresets()
    }

    private fun <T : Any> savedOverride(
        prefs: SharedPreferences,
        key: String,
        parse: (Any) -> T?,
    ): T? {
        val stored = prefs.all[key] ?: return null
        val value = parse(stored)
        if (value == null) {
            Log.w(TAG, "Removing malformed saved setting: $key")
            prefs.edit().remove(key).apply()
        }
        return value
    }

    private fun readSavedPower(): Boolean =
        savedOverride(defaultPrefs, DolbyConstants.PREF_ENABLE) { it as? Boolean } ?: true

    private fun readSavedHomeProfile(): Int =
        savedOverride(defaultPrefs, DolbyConstants.PREF_PROFILE) {
            (it as? String)?.toIntOrNull()?.takeIf(profileIds::contains)
        } ?: 0

    private fun desiredProfile(): Int = appProfileOverride ?: readSavedHomeProfile()

    private fun <T : Any> restoreOverride(
        effect: DolbyAudioEffect,
        prefs: SharedPreferences,
        key: String,
        parse: (Any) -> T?,
        apply: (T) -> Unit,
    ) {
        val value = savedOverride(prefs, key, parse) ?: return
        try {
            apply(value)
        } catch (exception: IllegalArgumentException) {
            // AudioEffect maps ERROR_BAD_VALUE to IllegalArgumentException.
            // Only discard a rejected edit while this handle is still healthy;
            // binder/control loss must preserve saved settings for the next attempt.
            if (!hasControl(effect)) throw exception
            if (effect.usesSoftwareDap) {
                Log.w(TAG, "Software DAP rejected $key; keeping the saved hardware setting", exception)
                return
            }
            Log.w(TAG, "Removing rejected saved setting: $key", exception)
            prefs.edit().remove(key).apply()
        }
    }

    private fun restoreSettings(effect: DolbyAudioEffect, profile: Int) {
        val prefs = context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
        // Only replay saved edits. Native defaults must not be converted to an
        // editor curve or replaced with guessed toggle/amount values.
        restoreOverride(effect, prefs, DolbyConstants.PREF_PRESET,
            parse = { (it as? String)?.let(PresetCurveCodec::parseNative) },
            apply = { effect.setDapParameter(DsParam.GEQ_BAND_GAINS, it, profile) },
        )

        listOf(
            DolbyConstants.PREF_BASS to DsParam.BASS_ENHANCER_ENABLE,
            DolbyConstants.PREF_VOLUME to DsParam.VOLUME_LEVELER_ENABLE,
            DolbyConstants.PREF_HP_VIRTUALIZER to DsParam.HEADPHONE_VIRTUALIZER,
            DolbyConstants.PREF_SPK_VIRTUALIZER to DsParam.SPEAKER_VIRTUALIZER,
            DolbyConstants.PREF_DIALOGUE to DsParam.DIALOGUE_ENHANCER_ENABLE,
        ).forEach { (key, parameter) ->
            restoreOverride(effect, prefs, key,
                parse = { it as? Boolean },
                apply = {
                    if (parameter != DsParam.VOLUME_LEVELER_ENABLE || volumeLevelerSupported) {
                        effect.setDapParameter(parameter, it, profile)
                    }
                },
            )
        }
        // Keep the existing device support override, including on a fresh profile.
        if (!volumeLevelerSupported) {
            effect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, false, profile)
        }

        val ieqValues = context.resources.getStringArray(R.array.dolby_ieq_values)
            .map { it.toInt() }
        restoreOverride(effect, prefs, DolbyConstants.PREF_IEQ,
            parse = { (it as? String)?.toIntOrNull()?.takeIf(ieqValues::contains) },
            apply = { effect.setDapParameter(DsParam.IEQ_PRESET, it, profile) },
        )

        val dialogueRange = context.resources.getInteger(R.integer.dialogue_enhancer_min)..
            context.resources.getInteger(R.integer.dialogue_enhancer_max)
        restoreOverride(effect, prefs, DolbyConstants.PREF_DIALOGUE_AMOUNT,
            parse = { (it as? Int)?.takeIf { amount -> amount == 0 || amount in dialogueRange } },
            apply = { effect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, it, profile) },
        )

        val stereoRange = context.resources.getInteger(R.integer.stereo_widening_min)..
            context.resources.getInteger(R.integer.stereo_widening_max)
        restoreOverride(effect, prefs, DolbyConstants.PREF_STEREO_WIDENING,
            parse = { (it as? Int)?.takeIf { amount -> amount == 0 || amount in stereoRange } },
            apply = {
                if (stereoWideningSupported) {
                    effect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, it, profile)
                }
            },
        )
    }

    private fun maybeMigratePresets() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.all[PREF_KEY_PRESETS_MIGRATED] == true) {
            return
        }
        val ceContext = context.createCredentialProtectedStorageContext()
        val cePrefs = ceContext.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        if (cePrefs.all.isEmpty()) {
            dlog(TAG, "no presets to migrate")
            return
        }
        if (context.moveSharedPreferencesFrom(ceContext, PREF_PRESETS)) {
            prefs.edit().putBoolean(PREF_KEY_PRESETS_MIGRATED, true).apply()
            dlog(TAG, "presets migrated successfully")
        } else {
            Log.w(TAG, "failed to migrate presets")
        }
    }

    private fun hasControl(effect: DolbyAudioEffect): Boolean = try {
        effect.hasControl()
    } catch (exception: RuntimeException) {
        false
    }

    private fun checkEffect(): DolbyAudioEffect = synchronized(settingsLock) {
        val useSoftwareDap = routeMonitor.useSoftwareDapForMedia() &&
            DolbyAudioEffect.isSoftwareDapAvailable()
        val previous = dolbyEffect
        if (previous != null && previous.usesSoftwareDap == useSoftwareDap && hasControl(previous)) {
            return@synchronized previous
        }
        if (SystemClock.uptimeMillis() < recoveryRetryAt) {
            throw IllegalStateException("Dolby effect recovery is pending", lastRecoveryFailure)
        }

        Log.w(TAG, "connecting Dolby effect: software=$useSoftwareDap")
        val previousEnabled = previous?.let { runCatching { it.enabled }.getOrDefault(false) } ?: false
        val power = readSavedPower()
        val selectedProfile = desiredProfile()
        var pending: DolbyAudioEffect? = null
        val replacement: DolbyAudioEffect
        try {
            // Disable only this handle; dsOn also changes Dolby's shared master setting.
            if (previous != null && hasControl(previous)) previous.setProcessingEnabled(false)
            replacement = DolbyAudioEffect(
                EFFECT_PRIORITY, audioSession = 0, usesSoftwareDap = useSoftwareDap
            )
            pending = replacement
            profileIds.forEach { profile ->
                if (startupResetPending) replacement.resetProfileSpecificSettings(profile)
                restoreSettings(replacement, profile)
            }
            replacement.dsOn = power
            replacement.profile = selectedProfile
        } catch (exception: RuntimeException) {
            pending?.let { runCatching { it.release() } }
            if (previous != null && hasControl(previous)) {
                runCatching { previous.setProcessingEnabled(previousEnabled) }
            }
            // A healthy old handle can still be on the wrong backend after a failed switch.
            lastRecoveryFailure = exception
            scheduleProfileRestore()
            throw exception
        }

        dolbyEffect = replacement
        startupResetPending = false
        previous?.let { runCatching { it.release() } }
        _enabled.value = power
        _activeProfile.value = selectedProfile
        // Recovery refreshes readers without invalidating edits accepted before it.
        // Only an explicit profile reset advances the write barrier above.
        _effectGeneration.value++
        clearRecoveryRetry()
        replacement
    }

    private fun <T> withEffect(operation: (DolbyAudioEffect) -> T): T = synchronized(settingsLock) {
        try {
            val effect = checkEffect()
            try {
                operation(effect)
            } catch (exception: RuntimeException) {
                // Retry once when a binder dies between acquisition and the operation.
                // An invalid parameter on a healthy effect must still reach the caller.
                if (hasControl(effect)) throw exception
                operation(checkEffect())
            }
        } catch (exception: RuntimeException) {
            if (dolbyEffect?.let(::hasControl) != true) {
                if (!recoveryRetryScheduled) lastRecoveryFailure = exception
                scheduleProfileRestore()
            }
            throw exception
        }
    }

    private fun restoreCurrentProfileSafely() {
        try {
            synchronized(settingsLock) {
                setCurrentProfile()
                registerCallbacks = _enabled.value
                clearRecoveryRetry()
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to restore Dolby profile; retrying", exception)
            scheduleProfileRestore()
        }
    }

    private fun scheduleProfileRestore() {
        synchronized(settingsLock) {
            if (recoveryRetryScheduled) return
            recoveryRetryScheduled = true
            recoveryRetryAt = SystemClock.uptimeMillis() + recoveryRetryDelay
            handler.postDelayed(profileRestoreRetry, recoveryRetryDelay)
            recoveryRetryDelay = (recoveryRetryDelay * 2).coerceAtMost(MAX_RECOVERY_RETRY_MS)
        }
    }

    private fun clearRecoveryRetry() {
        handler.removeCallbacks(profileRestoreRetry)
        recoveryRetryScheduled = false
        recoveryRetryAt = 0L
        recoveryRetryDelay = RECOVERY_RETRY_MS
        lastRecoveryFailure = null
    }

    private fun setPower(value: Boolean, persist: Boolean) {
        synchronized(settingsLock) {
            val current = dolbyEffect
            if (!value && current != null && hasControl(current)) {
                // A failed route switch must not prevent disabling the retained engine.
                current.dsOn = false
            } else {
                withEffect { it.dsOn = value }
            }
            _enabled.value = value
            if (persist) {
                defaultPrefs.edit().putBoolean(DolbyConstants.PREF_ENABLE, value).apply()
            }
            // Power has succeeded. A later profile/callback failure must not undo
            // its preference or prevent callers from updating the monitor lifecycle.
            try {
                registerCallbacks = value
                if (value) setCurrentProfile() else clearRecoveryRetry()
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Power applied; profile restoration will retry", exception)
                scheduleProfileRestore()
            }
        }
    }

    private fun setCurrentProfile() {
        synchronized(settingsLock) {
            dlog(TAG, "setCurrentProfile")
            applyProfile(desiredProfile())
        }
    }

    internal fun setAppProfileOverride(profile: Int?) {
        synchronized(settingsLock) {
            appProfileOverride = profile?.takeIf(profileIds::contains)
            setCurrentProfile()
        }
    }

    private fun applyProfile(value: Int) {
        synchronized(settingsLock) {
            require(value in profileIds) { "Unknown Dolby profile: $value" }
            withEffect { it.profile = value }
            _activeProfile.value = value
        }
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        setPower(dsOn, persist = true)
    }

    fun getProfileName(profile: Int = this.profile): String? {
        val profileValue = profile.toString()
        val profiles = context.resources.getStringArray(R.array.dolby_profile_values)
        val profileIndex = profiles.indexOf(profileValue)
        dlog(TAG, "getProfileName: profile=$profile index=$profileIndex")
        return if (profileIndex == -1) null else context.resources.getStringArray(
            R.array.dolby_profile_entries
        )[profileIndex]
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        synchronized(settingsLock) {
            dlog(TAG, "resetProfileSpecificSettings($profile)")
            withEffect { it.resetProfileSpecificSettings(profile) }
            // Clear existing instances too: stores held by an open screen must not
            // write their pre-reset values back after the reset.
            context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
                .edit().clear().apply()
            _settingsRevision.value++
        }
    }

    fun resetAllProfiles() {
        synchronized(settingsLock) {
            dlog(TAG, "resetAllProfiles")
            checkEffect()
            // Reset profile-specific settings
            profileIds.forEach { resetProfileSpecificSettings(it) }
            // Set default dynamic profile
            profile = 0
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(DolbyConstants.PREF_PROFILE, "0")
                .apply()
        }
    }

    fun getPreset(profile: Int = this.profile): String = withEffect { effect ->
        val gains = effect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        gains.joinToString(separator = ",").also {
            dlog(TAG, "getPreset: $it")
        }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
        val gains = requireNotNull(PresetCurveCodec.parseNative(value)) {
            "Preset must contain exactly 20 integer gains"
        }
        withEffect { it.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile) }
    }

    fun getPresetName(): String {
        val preset = getPreset()
        val presets = context.resources.getStringArray(R.array.dolby_preset_values)
        val userPresets = context.getSharedPreferences(PREF_PRESETS, Context.MODE_PRIVATE)
        return if (presets.contains(preset)) {
            context.resources.getStringArray(R.array.dolby_preset_entries)[presets.indexOf(preset)]
        } else {
            userPresets.all.entries.firstOrNull { it.value == preset }?.key
                ?: context.getString(R.string.dolby_preset_custom)
        }
    }

    internal fun readProfileSettings(profile: Int): ProfileSettings = withEffect { effect ->
        ProfileSettings(
            bass = effect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile),
            volume = effect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile),
            dialogue = effect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile),
            hpVirtualizer = effect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile),
            spkVirtualizer = effect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile),
            ieq = effect.getDapParameterInt(DsParam.IEQ_PRESET, profile),
            dialogueAmount = effect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile),
            stereoAmount = if (stereoWideningSupported) {
                effect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile)
            } else 0,
        )
    }

    private fun getDapParameterBool(param: DsParam, profile: Int) = withEffect { effect ->
        effect.getDapParameterBool(param, profile)
    }

    private fun getDapParameterInt(param: DsParam, profile: Int) = withEffect { effect ->
        effect.getDapParameterInt(param, profile)
    }

    private fun setDapParameter(param: DsParam, value: Boolean, profile: Int) {
        withEffect { it.setDapParameter(param, value, profile) }
    }

    private fun setDapParameter(param: DsParam, value: Int, profile: Int) {
        withEffect { it.setDapParameter(param, value, profile) }
    }

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getBassEnhancerEnabled: $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled: $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
    }

    fun getStereoWideningAmount(profile: Int = this.profile) =
        if (!stereoWideningSupported) {
            0
        } else {
            getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile).also {
                dlog(TAG, "getStereoWideningAmount: $it")
            }
        }

    fun setStereoWideningAmount(value: Int, profile: Int = this.profile) {
        if (!stereoWideningSupported) return
        dlog(TAG, "setStereoWideningAmount: $value")
        setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value, profile)
    }

    fun getDialogueEnhancerEnabled(profile: Int = this.profile) =
        getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getDialogueEnhancerEnabled: $it")
        }

    fun setDialogueEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerEnabled: $value")
        setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, value, profile)
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile) =
        getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile).also {
            dlog(TAG, "getDialogueEnhancerAmount: $it")
        }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerAmount: $value")
        setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
    }

    fun getIeqPreset(profile: Int = this.profile) =
        getDapParameterInt(DsParam.IEQ_PRESET, profile).also {
            dlog(TAG, "getIeqPreset: $it")
        }

    fun setIeqPreset(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setIeqPreset: $value")
        setDapParameter(DsParam.IEQ_PRESET, value, profile)
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val RECOVERY_RETRY_MS = 2_000L
        private const val MAX_RECOVERY_RETRY_MS = 60_000L
        private const val PREF_PRESETS = "presets"
        private const val PREF_KEY_PRESETS_MIGRATED = "presets_migrated"

        @Volatile
        private var instance: DolbyController? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context.applicationContext).also { instance = it }
            }
    }
}
