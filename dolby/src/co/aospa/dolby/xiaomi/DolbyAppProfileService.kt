/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Applies the assigned DAX profile for the most recently foregrounded app. */
internal class DolbyAppProfileService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: AppProfileManager
    private lateinit var controller: DolbyController
    private var lastForegroundPackage: String? = null
    private var lastAppliedProfile: Int? = null

    private val monitor = object : Runnable {
        override fun run() {
            if (!manager.isEnabled || !manager.hasUsageAccess()) {
                stopSelf()
                return
            }
            if (!controller.dsOn) {
                controller.setAppProfileOverride(null)
                lastAppliedProfile = null
                lastForegroundPackage = null
                handler.postDelayed(this, CHECK_INTERVAL_MS)
                return
            }
            foregroundPackage()?.let(::applyForPackage)
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = AppProfileManager(this)
        controller = DolbyController.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!manager.isEnabled || !manager.hasUsageAccess()) {
            manager.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        lastForegroundPackage = null
        handler.removeCallbacks(monitor)
        handler.post(monitor)
        return START_STICKY
    }

    private fun foregroundPackage(): String? {
        val now = System.currentTimeMillis()
        val events = (getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
            .queryEvents(now - EVENT_LOOKBACK_MS, now)
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val resumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (resumed) foreground = event.packageName
        }
        if (foreground != null) return foreground
        return lastForegroundPackage
    }

    private fun applyForPackage(packageName: String) {
        val assignment = manager.profileFor(packageName)
        if (packageName == lastForegroundPackage && assignment == lastAppliedProfile &&
            (assignment == null || controller.profile == assignment)
        ) return
        lastForegroundPackage = packageName
        lastAppliedProfile = assignment
        controller.setAppProfileOverride(assignment)
    }

    override fun onDestroy() {
        handler.removeCallbacks(monitor)
        if (::controller.isInitialized) controller.setAppProfileOverride(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHECK_INTERVAL_MS = 2_000L
        private const val EVENT_LOOKBACK_MS = 60_000L

        fun start(context: Context) {
            context.startService(Intent(context, DolbyAppProfileService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DolbyAppProfileService::class.java))
        }
    }
}
