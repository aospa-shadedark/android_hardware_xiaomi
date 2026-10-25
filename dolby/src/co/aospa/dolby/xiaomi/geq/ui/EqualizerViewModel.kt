/*
 * Copyright (C) 2024-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.dlog
import co.aospa.dolby.xiaomi.geq.data.BandGain
import co.aospa.dolby.xiaomi.geq.data.EqualizerRepository
import co.aospa.dolby.xiaomi.geq.data.Preset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

const val TAG = "EqViewModel"

class EqualizerViewModel(
    private val repository: EqualizerRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _presets = MutableStateFlow(repository.builtInPresets)
    val presets = _presets.asStateFlow()
    private val _preset = MutableStateFlow(repository.defaultPreset)
    val preset = _preset.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    private val _hasLoadError = MutableStateFlow(false)
    val hasLoadError = _hasLoadError.asStateFlow()
    private val _hasSaveError = MutableStateFlow(false)
    val hasSaveError = _hasSaveError.asStateFlow()
    val activeProfile = repository.activeProfileFlow
    private val _importedPreset = MutableStateFlow(savedStateHandle.get<Bundle>(STATE_IMPORT)?.toPreset())
    val importedPreset = _importedPreset.asStateFlow()
    private val _transferError = MutableStateFlow(savedStateHandle.get<Boolean>(STATE_TRANSFER_ERROR) ?: false)
    val transferError = _transferError.asStateFlow()

    private var currentProfile = repository.activeProfile
    private var loadedRevision = repository.settingsRevision
    private var editGeneration = 0L
    private var cleared = false
    private val refreshRequests = MutableStateFlow(0L)

    init {
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
                    val generation = editGeneration
                    if (profile != currentProfile || revision != loadedRevision) {
                        _isLoading.value = true
                    }
                    _hasLoadError.value = false
                    dlog(TAG, "Loading equalizer: profile=$profile revision=$revision")
                    try {
                        // A quick return to a profile must see its last queued edit.
                        repository.awaitPendingOperations()
                        val gains = repository.getBandGains(profile)
                        if (profile == repository.activeProfile && revision == repository.settingsRevision) {
                            // A selection made during this read is already visible and
                            // queued for persistence. An older read must not replace it.
                            if (generation == editGeneration) {
                                val previous = _preset.value.takeIf {
                                    profile == currentProfile && revision == loadedRevision
                                }
                                currentProfile = profile
                                loadedRevision = revision
                                _preset.value = resolvePreset(gains, previous)
                                _isLoading.value = false
                                dlog(TAG, "Loaded equalizer: profile=$profile preset=${_preset.value.name}")
                            }
                        } else {
                            // A profile switch or reset raced the read. Ask combine to load
                            // the latest snapshot instead of leaving the page in Loading.
                            refreshRequests.value++
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.e(TAG, "Failed to load equalizer for profile $profile", exception)
                        if (profile == repository.activeProfile && revision == repository.settingsRevision &&
                            generation == editGeneration) {
                            _hasLoadError.value = true
                            _isLoading.value = false
                        } else if (profile != repository.activeProfile || revision != repository.settingsRevision) {
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

    private fun enqueueOperation(operation: suspend () -> Unit) {
        repository.enqueueOperation {
            try {
                operation()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save equalizer settings", exception)
                if (!cleared) {
                    _hasSaveError.value = true
                    refresh()
                }
            }
        }
    }

    private fun applyPreset(preset: Preset, explicitSelection: Boolean = false) {
        if (cleared || (!explicitSelection && !canEdit())) return
        // A preset/reset is a complete curve, so it can target the current profile
        // even before its old curve has loaded. Band edits still require that load.
        val profile = if (explicitSelection) repository.activeProfile else currentProfile
        val revision = if (explicitSelection) repository.settingsRevision else loadedRevision
        editGeneration++
        currentProfile = profile
        loadedRevision = revision
        _preset.value = preset
        _hasLoadError.value = false
        _isLoading.value = false
        enqueueOperation {
            if (repository.setBandGains(preset.bandGains, profile, revision) && preset.isUserDefined) {
                repository.addPreset(preset)
            }
        }
    }

    fun readImportedPreset(uri: Uri?) {
        if (uri == null || cleared) return
        viewModelScope.launch {
            try {
                val imported = repository.readPreset(uri)
                if (!cleared) {
                    savedStateHandle[STATE_IMPORT] = imported.toTransferState()
                    _importedPreset.value = imported
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to import equalizer preset", exception)
                showTransferError()
            }
        }
    }

    fun dismissImportedPreset() {
        savedStateHandle.set<Bundle?>(STATE_IMPORT, null)
        _importedPreset.value = null
    }

    fun prepareExport(): String? {
        val current = _preset.value
        if (!current.isUserDefined || cleared) return null
        // Store the exact curve selected when the picker opens. A profile switch
        // or activity recreation must not substitute a different current curve.
        savedStateHandle[STATE_EXPORT] = current.toTransferState()
        return "${current.name.orEmpty().replace(' ', '_')}.ldp"
    }

    fun writeExportedPreset(uri: Uri?) {
        val pending = savedStateHandle.get<Bundle>(STATE_EXPORT)?.toPreset()
        savedStateHandle.set<Bundle?>(STATE_EXPORT, null)
        if (uri == null || cleared) return
        if (pending == null) {
            showTransferError()
            return
        }
        repository.enqueueExport {
            try {
                repository.writePreset(uri, pending)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to export equalizer preset", exception)
                showTransferError()
            }
        }
    }

    private fun showTransferError() {
        if (cleared) return
        savedStateHandle[STATE_TRANSFER_ERROR] = true
        _transferError.value = true
    }

    fun dismissTransferError() {
        savedStateHandle[STATE_TRANSFER_ERROR] = false
        _transferError.value = false
    }

    fun dismissSaveError() {
        _hasSaveError.value = false
    }

    fun refresh() {
        _hasLoadError.value = false
        _isLoading.value = true
        refreshRequests.value++
    }

    fun reset() {
        dlog(TAG, "reset()")
        applyPreset(repository.defaultPreset, explicitSelection = true)
    }

    fun setPreset(preset: Preset) {
        dlog(TAG, "setPreset($preset)")
        applyPreset(preset, explicitSelection = true)
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
        if (cleared || !canEdit()) return PresetNameValidationError.PROFILE_NOT_READY
        validatePresetName(name)?.let { return it }
        applyPreset(_preset.value.copy(name = name.trim(), isUserDefined = true, isMutated = false))
        return null
    }

    fun importPreset(name: String, bandGains: List<BandGain>): PresetNameValidationError? {
        validatePresetName(name)?.let { return it }
        applyPreset(Preset(name = name.trim(), bandGains = bandGains, isUserDefined = true), explicitSelection = true)
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
        enqueueOperation { repository.renamePreset(preset, replacement) }
        return null
    }

    fun deletePreset(preset: Preset, shouldReset: Boolean = true) {
        val current = _preset.value
        enqueueOperation { repository.removePreset(preset) }
        if (shouldReset && current.isUserDefined && current.name == preset.name) {
            applyPreset(repository.defaultPreset, explicitSelection = true)
        }
    }

    override fun onCleared() {
        // Refresh collectors stop with the ViewModel. The shared repository queue
        // finishes accepted writes and releases their callbacks when they finish.
        cleared = true
        super.onCleared()
    }

    companion object {
        private const val STATE_EXPORT = "equalizer.pendingExport"
        private const val STATE_IMPORT = "equalizer.importedPreset"
        private const val STATE_TRANSFER_ERROR = "equalizer.transferError"

        val Factory = viewModelFactory {
            initializer {
                EqualizerViewModel(
                    repository = EqualizerRepository(
                        this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                    ),
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}

private fun Preset.toTransferState() = Bundle().apply {
    putString("name", name)
    putIntArray("frequencies", bandGains.map { it.band }.toIntArray())
    putIntArray("gains", bandGains.map { it.gain }.toIntArray())
}

private fun Bundle.toPreset(): Preset? {
    val frequencies = getIntArray("frequencies") ?: return null
    val gains = getIntArray("gains") ?: return null
    if (frequencies.size != 10 || gains.size != frequencies.size) return null
    return Preset(
        name = getString("name"),
        bandGains = frequencies.indices.map { BandGain(frequencies[it], gains[it]) },
        isUserDefined = true,
    )
}
