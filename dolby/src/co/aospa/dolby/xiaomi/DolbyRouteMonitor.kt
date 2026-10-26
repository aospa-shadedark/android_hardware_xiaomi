/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCodecType
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.util.Log

/** Tracks the media route whose PCM needs software DAP before LHDC encoding. */
internal class DolbyRouteMonitor(
    context: Context,
    private val onRouteChanged: () -> Unit,
) {
    private val context = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val handler = Handler(context.mainLooper)
    private val lock = Any()
    private var registration: Registration? = null
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private inner class Registration {
        var receiverRegistered = false
        var routeListenerRegistered = false
        var profileRequested = false
        var connectedProfile: BluetoothA2dp? = null
        // Retain a disconnected proxy too, so stop() can unregister it.
        var profileToClose: BluetoothA2dp? = null

        val changed = Runnable {
            if (synchronized(lock) { registration === this }) {
                onRouteChanged()
            }
        }

        val routeListener = AudioManager.OnDevicesForAttributesChangedListener { _, _ ->
            dispatchRouteChanged(this)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (synchronized(lock) { registration !== this@Registration }) return
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                            synchronized(lock) { connectedProfile = null }
                        }
                        BluetoothAdapter.STATE_ON -> runCatching {
                            requestProfile(this@Registration)
                        }.onFailure {
                            Log.w(TAG, "Unable to request Bluetooth profile proxy", it)
                        }
                    }
                }
                dispatchRouteChanged(this@Registration)
            }
        }

        val profileListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                // Always queue callbacks: start() may run during controller construction.
                handler.post { handleProfileConnected(this@Registration, profile, proxy) }
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile != BluetoothProfile.A2DP) return
                handler.post {
                    synchronized(lock) {
                        if (registration !== this@Registration) return@post
                        connectedProfile = null
                    }
                    // The framework reconnects an existing requested profile proxy.
                    dispatchRouteChanged(this@Registration)
                }
            }
        }
    }

    fun start() {
        synchronized(lock) {
            if (registration != null) return
            val current = Registration()
            registration = current
            try {
                context.registerReceiver(
                    current.receiver,
                    IntentFilter().apply {
                        addAction(BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED)
                        addAction(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED)
                        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    },
                    null,
                    handler,
                    Context.RECEIVER_EXPORTED,
                )
                current.receiverRegistered = true
                // Mark first so a registration that throws after reaching the service
                // is also removed by cleanup.
                current.routeListenerRegistered = true
                audioManager.addOnDevicesForAttributesChangedListener(
                    mediaAttributes,
                    context.mainExecutor,
                    current.routeListener,
                )
                requestProfile(current)
            } catch (exception: RuntimeException) {
                registration = null
                cleanup(current)
                throw exception
            }
        }
    }

    fun stop() {
        val previous = synchronized(lock) {
            registration.also { registration = null }
        } ?: return
        cleanup(previous)
    }

    /** Requires the actual media route and its active device to agree. */
    fun useSoftwareDapForMedia(): Boolean {
        val current = synchronized(lock) { registration } ?: return false
        val profile = synchronized(lock) { current.connectedProfile } ?: return false
        return try {
            // A global effect cannot select two backends for a duplicated route.
            val route = audioManager.getAudioDevicesForAttributes(mediaAttributes).singleOrNull()
                ?.takeIf { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ?: return false
            val device = adapter?.getActiveDevices(BluetoothProfile.A2DP)?.singleOrNull {
                route.address.equals(it.address, ignoreCase = true)
            } ?: return false
            val isLhdc = profile.getCodecStatus(device)?.codecConfig?.extendedCodecType?.codecId ==
                BluetoothCodecType.CODEC_ID_LHDCV5
            isLhdc && synchronized(lock) {
                registration === current && current.connectedProfile === profile
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Unable to query the active Bluetooth media codec", exception)
            false
        }
    }

    private fun requestProfile(current: Registration) {
        synchronized(lock) {
            if (registration !== current || current.profileRequested) return
            // A disabled adapter retains pending proxies without delivering a handle to close.
            if (adapter?.isEnabled != true) return
            // False means no request was registered. A later Bluetooth STATE_ON
            // broadcast may retry without adding a timer or a second proxy.
            current.profileRequested = adapter?.getProfileProxy(
                context,
                current.profileListener,
                BluetoothProfile.A2DP,
            ) == true
        }
    }

    private fun handleProfileConnected(
        current: Registration,
        profile: Int,
        proxy: BluetoothProfile,
    ) {
        val accepted = synchronized(lock) {
            if (registration !== current || profile != BluetoothProfile.A2DP ||
                proxy !is BluetoothA2dp) {
                false
            } else {
                current.connectedProfile = proxy
                current.profileToClose = proxy
                true
            }
        }
        if (!accepted) {
            // A request can complete after stop(), including after a later start().
            runCatching { adapter?.closeProfileProxy(profile, proxy) }
            return
        }
        dispatchRouteChanged(current)
    }

    private fun dispatchRouteChanged(current: Registration) {
        synchronized(lock) {
            if (registration !== current) return
            handler.removeCallbacks(current.changed)
            handler.post(current.changed)
        }
    }

    private fun cleanup(previous: Registration) {
        handler.removeCallbacks(previous.changed)
        if (previous.receiverRegistered) {
            runCatching { context.unregisterReceiver(previous.receiver) }
                .onFailure { Log.w(TAG, "Unable to unregister Bluetooth route receiver", it) }
        }
        if (previous.routeListenerRegistered) {
            runCatching {
                audioManager.removeOnDevicesForAttributesChangedListener(previous.routeListener)
            }.onFailure { Log.w(TAG, "Unable to unregister media route listener", it) }
        }
        previous.profileToClose?.let { proxy ->
            runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
                .onFailure { Log.w(TAG, "Unable to close Bluetooth profile proxy", it) }
        }
    }

    companion object {
        private const val TAG = "DolbyRouteMonitor"
    }
}
