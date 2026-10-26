/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Process

internal data class DolbyApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

internal class AppProfileManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // Existing enabled installations have already opted in. A fresh install or
    // cleared app data must explicitly approve monitoring even on a privileged ROM.
    fun hasMonitoringConsent(): Boolean = prefs.getBoolean(KEY_MONITORING_CONSENT, isEnabled)

    fun confirmMonitoringConsent() {
        prefs.edit().putBoolean(KEY_MONITORING_CONSENT, true).apply()
    }

    fun hasDismissedSetupGuide(): Boolean = prefs.getBoolean(KEY_SETUP_GUIDE_DISMISSED, false)

    fun dismissSetupGuide() {
        prefs.edit().putBoolean(KEY_SETUP_GUIDE_DISMISSED, true).apply()
    }

    fun assignments(): Map<String, Int> = prefs.all.mapNotNull { (key, value) ->
        if (!key.startsWith(KEY_APP_PREFIX)) return@mapNotNull null
        val profile = (value as? Int) ?: return@mapNotNull null
        key.removePrefix(KEY_APP_PREFIX) to profile
    }.toMap()

    fun profileFor(packageName: String): Int? =
        prefs.getInt(KEY_APP_PREFIX + packageName, NO_ASSIGNMENT).takeIf { it != NO_ASSIGNMENT }

    fun setProfile(packageName: String, profile: Int?) {
        prefs.edit().apply {
            if (profile == null) remove(KEY_APP_PREFIX + packageName)
            else putInt(KEY_APP_PREFIX + packageName, profile)
        }.apply()
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun installedApps(): List<DolbyApp> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        return context.packageManager.queryIntentActivities(launchIntent, 0)
            .asSequence()
            .mapNotNull { resolve ->
                val activity = resolve.activityInfo ?: return@mapNotNull null
                if (activity.packageName == context.packageName) return@mapNotNull null
                DolbyApp(
                    packageName = activity.packageName,
                    label = resolve.loadLabel(context.packageManager).toString(),
                    icon = resolve.loadIcon(context.packageManager),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    companion object {
        const val NO_ASSIGNMENT = -1
        const val PREFS = "dolby_app_profiles"
        const val KEY_ENABLED = "monitoring_enabled"
        const val KEY_APP_PREFIX = "app_profile:"
        private const val KEY_MONITORING_CONSENT = "monitoring_consent"
        private const val KEY_SETUP_GUIDE_DISMISSED = "setup_guide_dismissed_v4"
    }
}
