/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private const val TAG = "DolbyTileService"

class DolbyTileService : TileService() {

    private val dolbyController by lazy { DolbyController.getInstance(applicationContext) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listeningJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listeningJob?.cancel()
        listeningJob = scope.launch {
            combine(dolbyController.enabled, dolbyController.activeProfile) { enabled, profile ->
                enabled to profile
            }.collect { (enabled, profile) ->
                updateTileState(enabled, profile)
            }
        }
    }

    override fun onStopListening() {
        listeningJob?.cancel()
        listeningJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        try {
            val isDsOn = dolbyController.dsOn
            dolbyController.setDsOnAndPersist(!isDsOn) // toggle
            val appProfiles = AppProfileManager(applicationContext)
            if (isDsOn) {
                DolbyAppProfileService.stop(applicationContext)
            } else if (appProfiles.isEnabled && appProfiles.hasUsageAccess()) {
                DolbyAppProfileService.start(applicationContext)
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to toggle Dolby", exception)
        }
        updateTileState()
        super.onClick()
    }

    private fun updateTileState(
        enabled: Boolean = dolbyController.enabled.value,
        profile: Int = dolbyController.activeProfile.value,
    ) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = dolbyController.getProfileName(profile) ?: getString(R.string.dolby_unknown)
            updateTile()
        }
    }
}
