/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log

/** Applies the assigned DAX profile for the currently focused app. */
internal class DolbyAppProfileService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: AppProfileManager
    private lateinit var controller: DolbyController
    private var lastForegroundPackage: String? = null
    private var lastAppliedProfile: Int? = null
    private var taskListenerRegistered = false
    @Volatile
    private var destroyed = false

    private val monitor = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (destroyed) return
            if (!manager.isEnabled || !manager.hasUsageAccess()) {
                stopSelf()
                return
            }
            try {
                if (!controller.dsOn) {
                    controller.setAppProfileOverride(null)
                    lastAppliedProfile = null
                    lastForegroundPackage = null
                } else {
                    applyForPackage(foregroundPackage())
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Unable to apply app profile; retrying", exception)
            }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private val taskStackListener = object : TaskStackListener() {
        override fun onTaskStackChanged() {
            scheduleMonitor()
        }
    }

    private fun scheduleMonitor() {
        if (destroyed) return
        handler.removeCallbacks(monitor)
        handler.post(monitor)
    }

    override fun onCreate() {
        super.onCreate()
        manager = AppProfileManager(this)
        controller = DolbyController.getInstance(this)
        try {
            ActivityTaskManager.getService().registerTaskStackListener(taskStackListener)
            taskListenerRegistered = true
        } catch (exception: RemoteException) {
            Log.w(TAG, "Task events unavailable; using polling", exception)
        } catch (exception: SecurityException) {
            Log.w(TAG, "Task events unavailable; using polling", exception)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!manager.isEnabled || !manager.hasUsageAccess()) {
            manager.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        scheduleMonitor()
        return START_STICKY
    }

    private fun foregroundPackage(): String? {
        // This platform app runs as the system UID, which can query the focused task.
        // A usage-event window misses long-running activities after a service restart
        // and can keep reporting an activity that has since paused or stopped.
        return ActivityTaskManager.getService().getFocusedRootTaskInfo()
            ?.topActivity?.packageName
    }

    private fun applyForPackage(packageName: String?) {
        val assignment = packageName?.let(manager::profileFor)
        if (packageName == lastForegroundPackage && assignment == lastAppliedProfile &&
            (assignment == null || controller.profile == assignment)
        ) return
        controller.setAppProfileOverride(assignment)
        lastForegroundPackage = packageName
        lastAppliedProfile = assignment
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacks(monitor)
        if (taskListenerRegistered) {
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(taskStackListener)
            } catch (exception: RemoteException) {
                Log.w(TAG, "Unable to unregister task events", exception)
            } catch (exception: SecurityException) {
                Log.w(TAG, "Unable to unregister task events", exception)
            }
        }
        if (::controller.isInitialized) {
            try {
                controller.setAppProfileOverride(null)
            } catch (exception: RuntimeException) {
                Log.w(TAG, "Unable to restore Home profile", exception)
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "DolbyAppProfiles"
        private const val CHECK_INTERVAL_MS = 2_000L

        fun start(context: Context) {
            context.startService(Intent(context, DolbyAppProfileService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DolbyAppProfileService::class.java))
        }
    }
}
