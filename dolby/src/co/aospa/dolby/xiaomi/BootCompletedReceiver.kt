/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "XiaomiDolby-Boot"

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received intent: ${intent.action}")
        handleAction(context.applicationContext, intent.action)
    }

    private fun handleAction(context: Context, action: String?, retryDelay: Long = 2_000L) {
        try {
            when (action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    // we perform everything in the initializer
                    DolbyController.getInstance(context)
                }
                Intent.ACTION_BOOT_COMPLETED -> {
                    val controller = DolbyController.getInstance(context)
                    val appProfiles = AppProfileManager(context)
                    if (controller.dsOn && appProfiles.isEnabled && appProfiles.hasUsageAccess()) {
                        DolbyAppProfileService.start(context)
                    }
                }
                Intent.ACTION_USER_UNLOCKED -> {
                    DolbyController.getInstance(context).onUserUnlocked()
                }
                else -> Log.e(TAG, "unhandled intent action")
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Dolby unavailable during $action; retrying", exception)
            Handler(Looper.getMainLooper()).postDelayed(
                { handleAction(context, action, (retryDelay * 2).coerceAtMost(60_000L)) },
                retryDelay,
            )
        }
    }
}
