/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.geq.data.BandGain
import co.aospa.dolby.xiaomi.geq.data.EqualizerRepository
import co.aospa.dolby.xiaomi.geq.data.Preset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

const val TAG = "EqViewModel"

class EqualizerViewModel(
    private val repository: EqualizerRepository
) : ViewModel() {

    private val _presets = MutableStateFlow(repository.builtInPresets)
    val presets = _presets.asStateFlow()
    private val _preset = MutableStateFlow(repository.defaultPreset)
    val preset = _preset.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError = _hasLoadError.asStateFlow()
    val activeProfile = repository.activeProfileFlow

    private var currentProfile = repository.activeProfile
    private var loadedRevision = repository.settingsRevision
    private val refreshRequests = MutableStateFlow(0L)
    // Capture the target profile when an edit happens, then persist edits in order.
    // Displaying or refreshing a curve must never write it back into DAX.
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            for (operation in operations) {
                try {
                    operation()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Log.e(TAG, "Failed to save equalizer settings", exception)
                    refresh()
                }
            }
        }

        repository.userPresets.onEach { userPresets ->
            _presets.value = userPresets + repository.builtInPresets
            if (!_isLoading.value && _preset.value.name == null && !_preset.value.isMutated) {
                _preset.value = resolvePreset(_preset.value.bandGains, _preset.value)
            }
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            combine(
                repository.activeProfileFlow,
                repository.settingsRevisionFlow,
                refreshRequests,
            ) { profile, revision, _ -> profile to revision }
                .collectLatest { (profile, revision) ->
                    _isLoading.value = true
                    _hasLoadError.value = false
                    dlog(TAG, "Loading equalizer: profile=$profile revision=$revision")
                    try {
                        // A quick return to a profile must see its last queued edit.
                        val pendingEditsFinished = CompletableDeferred<Unit>()
                        operations.send { pendingEditsFinished.complete(Unit) }
                        pendingEditsFinished.await()
                        val gains = repository.getBandGains(profile)
                        if (profile == repository.activeProfile && revision == repository.settingsRevision) {
                            val previous = _preset.value.takeIf {
                                profile == currentProfile && revision == loadedRevision
                            }
                            currentProfile = profile
                            loadedRevision = revision
                            _preset.value = resolvePreset(gains, previous)
                            _isLoading.value = false
                            dlog(TAG, "Loaded equalizer: profile=$profile preset=${_preset.value.name}")
                        } else {
                            // A profile switch or reset raced the read. Ask combine to load
                            // the latest snapshot instead of leaving the page in Loading.
                            refreshRequests.value++
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.e(TAG, "Failed to load equalizer for profile $profile", exception)
                        if (profile == repository.activeProfile && revision == repository.settingsRevision) {
                            _hasLoadError.value = true
                            _isLoading.value = false
                        } else {
                            refreshRequests.value++
                        }
                    }
                }
        }
    }

    private fun resolvePreset(gains: List<BandGain>, previous: Preset? = null): Preset {
        val named = _presets.value.find {
            it.name == previous?.name && it.isUserDefined == previous?.isUserDefined && it.bandGains == gains
        }
        // A reset should resolve to Flat even if a saved custom preset has the
        // same zero gains. Preserve an explicitly selected custom preset above.
        return named ?: repository.builtInPresets.find { it.bandGains == gains }
            ?: _presets.value.find { it.bandGains == gains } ?: Preset(bandGains = gains)
    }

    private fun canEdit() = !_isLoading.value && !_hasLoadError.value &&
        currentProfile == repository.activeProfile &&
        loadedRevision == repository.settingsRevision

    private fun applyPreset(preset: Preset) {
        if (!canEdit()) return
        val profile = currentProfile
        val revision = loadedRevision
        _preset.value = preset
        operations.trySend {
            if (repository.setBandGains(preset.bandGains, profile, revision) && preset.isUserDefined) {
                repository.addPreset(preset)
            }
        }
    }

    fun refresh() {
        _hasLoadError.value = false
        _isLoading.value = true
        refreshRequests.value++
    }

    fun reset() {
        dlog(TAG, "reset()")
        applyPreset(repository.defaultPreset)
    }

    fun setPreset(preset: Preset) {
        dlog(TAG, "setPreset($preset)")
        applyPreset(preset)
    }

    fun setGain(index: Int, gain: Int) {
        if (index !in _preset.value.bandGains.indices) return
        val current = _preset.value
        applyPreset(current.copy(
            name = if (current.isUserDefined) current.name else null,
            bandGains = current.bandGains.mapIndexed { bandIndex, band ->
                if (bandIndex == index) band.copy(gain = gain.coerceIn(-100, 100)) else band
            },
            isMutated = true,
        ))
    }

    private fun validatePresetName(name: String, excluding: Preset? = null): PresetNameValidationError? {
        val normalizedName = name.trim()
        return if (normalizedName.isEmpty()) {
            PresetNameValidationError.NAME_EMPTY
        } else if (_presets.value.any {
                it.name != excluding?.name && it.name?.equals(normalizedName, ignoreCase = true) == true
            }) {
            PresetNameValidationError.NAME_EXISTS
        } else if (normalizedName.length > 50) {
            PresetNameValidationError.NAME_TOO_LONG
        } else null
    }

    fun createNewPreset(name: String): PresetNameValidationError? {
        validatePresetName(name)?.let { return it }
        applyPreset(_preset.value.copy(name = name.trim(), isUserDefined = true, isMutated = false))
        return null
    }

    fun importPreset(name: String, bandGains: List<BandGain>): PresetNameValidationError? {
        validatePresetName(name)?.let { return it }
        applyPreset(Preset(name = name.trim(), bandGains = bandGains, isUserDefined = true))
        return null
    }

    fun renamePreset(preset: Preset, name: String): PresetNameValidationError? {
        val normalizedName = name.trim()
        validatePresetName(normalizedName, excluding = preset)?.let { return it }
        if (normalizedName == preset.name) return null
        val current = _preset.value
        val isActive = current.isUserDefined && current.name == preset.name
        val replacement = (if (isActive) current else preset).copy(
            name = normalizedName,
            isMutated = false,
        )
        if (isActive) _preset.value = replacement
        operations.trySend { repository.renamePreset(preset, replacement) }
        return null
    }

    fun deletePreset(preset: Preset, shouldReset: Boolean = true) {
        val current = _preset.value
        operations.trySend { repository.removePreset(preset) }
        if (shouldReset && current.isUserDefined && current.name == preset.name) {
            applyPreset(repository.defaultPreset)
        }
    }

    override fun onCleared() {
        operations.close()
        super.onCleared()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(
                    repository = EqualizerRepository(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    )
                )
            }
        }
    }
}
