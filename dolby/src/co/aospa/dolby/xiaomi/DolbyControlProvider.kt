/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */
package co.aospa.dolby.xiaomi

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.xiaomi.DolbyConstants.Companion.PREF_PROFILE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Signature-protected control surface for the ROM's game toolbox. */
class DolbyControlProvider : ContentProvider() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val controller by lazy { DolbyController.getInstance(requireNotNull(context)) }
    private val profiles by lazy { AppProfileManager(requireNotNull(context)) }
    private val validProfiles by lazy {
        requireNotNull(context).resources.getStringArray(R.array.dolby_profile_values)
            .map(String::toInt).toSet()
    }

    override fun onCreate(): Boolean {
        scope.launch {
            combine(controller.enabled, controller.activeProfile, controller.effectGeneration) { _, _, _ -> Unit }
                .collect { notifyChanged() }
        }
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        requireNotNull(context).enforceCallingOrSelfPermission(
            PERMISSION_CONTROL, "Dolby control requires the ROM signature"
        )
        return try {
            when (method) {
                GET_STATE -> state()
                SET_ENABLED -> {
                    val enabled = extras?.getBoolean(KEY_ENABLED)
                        ?: throw IllegalArgumentException("Missing enabled state")
                    controller.setDsOnAndPersist(enabled)
                    updateAppProfileService(enabled)
                    notifyChanged()
                    state()
                }
                SET_PROFILE -> {
                    val selected = extras?.getInt(KEY_PROFILE, Int.MIN_VALUE)
                        ?: throw IllegalArgumentException("Missing profile")
                    require(selected in validProfiles) { "Unsupported Dolby profile: $selected" }
                    controller.setDsOnAndPersist(true)
                    updateAppProfileService(true)
                    controller.profile = selected
                    PreferenceManager.getDefaultSharedPreferences(requireNotNull(context))
                        .edit().putString(PREF_PROFILE, selected.toString()).apply()
                    notifyChanged()
                    state()
                }
                GET_APP_PROFILE -> appProfile(arg)
                SET_APP_PROFILE -> {
                    val packageName = arg?.takeIf(String::isNotBlank)
                        ?: throw IllegalArgumentException("Missing package name")
                    val selected = extras?.getInt(KEY_PROFILE, Int.MIN_VALUE)
                        ?: throw IllegalArgumentException("Missing profile")
                    require(selected == AppProfileManager.NO_ASSIGNMENT || selected in validProfiles) {
                        "Unsupported Dolby profile: $selected"
                    }
                    profiles.setProfile(packageName,
                        selected.takeUnless { it == AppProfileManager.NO_ASSIGNMENT })
                    if (controller.enabled.value) updateAppProfileService(true)
                    notifyChanged()
                    appProfile(packageName)
                }
                else -> throw IllegalArgumentException("Unknown method: $method")
            }
        } catch (exception: RuntimeException) {
            Log.w(TAG, "Dolby control failed: $method", exception)
            Bundle().apply {
                putBoolean(KEY_SUCCESS, false)
                putString(KEY_ERROR, exception.message)
            }
        }
    }

    private fun state() = Bundle().apply {
        val live = runCatching { controller.dsOn }
        val homeProfile = PreferenceManager.getDefaultSharedPreferences(requireNotNull(context))
            .getString(PREF_PROFILE, "0")?.toIntOrNull()?.takeIf(validProfiles::contains) ?: 0
        putBoolean(KEY_SUCCESS, true)
        putBoolean(KEY_READY, live.isSuccess)
        putBoolean(KEY_ENABLED, live.getOrDefault(false))
        putInt(KEY_PROFILE, controller.activeProfile.value)
        putInt(KEY_HOME_PROFILE, homeProfile)
        putBoolean(KEY_APP_PROFILES_ENABLED, profiles.isEnabled && profiles.hasUsageAccess())
    }

    private fun appProfile(packageName: String?) = Bundle().apply {
        val name = packageName?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Missing package name")
        putBoolean(KEY_SUCCESS, true)
        putInt(KEY_PROFILE, profiles.profileFor(name) ?: AppProfileManager.NO_ASSIGNMENT)
        putBoolean(KEY_APP_PROFILES_ENABLED, profiles.isEnabled && profiles.hasUsageAccess())
    }

    private fun notifyChanged() {
        context?.contentResolver?.notifyChange(URI, null)
    }

    private fun updateAppProfileService(enabled: Boolean) {
        val appContext = requireNotNull(context)
        if (profiles.isEnabled && profiles.hasUsageAccess()) {
            if (enabled) DolbyAppProfileService.start(appContext)
            else DolbyAppProfileService.stop(appContext)
        }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<String>?): Int = 0

    companion object {
        private const val TAG = "DolbyControlProvider"
        private const val PERMISSION_CONTROL = "co.aospa.dolby.xiaomi.permission.CONTROL"
        private val URI = Uri.parse("content://co.aospa.dolby.xiaomi.control")
        private const val GET_STATE = "get_state"
        private const val SET_ENABLED = "set_enabled"
        private const val SET_PROFILE = "set_profile"
        private const val GET_APP_PROFILE = "get_app_profile"
        private const val SET_APP_PROFILE = "set_app_profile"
        private const val KEY_SUCCESS = "success"
        private const val KEY_ERROR = "error"
        private const val KEY_READY = "ready"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PROFILE = "profile"
        private const val KEY_HOME_PROFILE = "home_profile"
        private const val KEY_APP_PROFILES_ENABLED = "app_profiles_enabled"
    }
}
