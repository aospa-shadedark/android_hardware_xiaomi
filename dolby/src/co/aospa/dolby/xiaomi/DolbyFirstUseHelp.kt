/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource

internal enum class DolbyHelpTopic(
    val preferenceKey: String,
    val title: Int,
    val message: Int,
) {
    EQUALIZER("equalizer_v2", R.string.dolby_eq_help_title, R.string.dolby_eq_help_message),
    ADVANCED("advanced_v3", R.string.dolby_advanced_help_title, R.string.dolby_advanced_help_message),
}

/** Guidance is acknowledged separately from audio settings and survives profile resets. */
@Composable
internal fun DolbyFirstUseHelp(topic: DolbyHelpTopic, isVisible: Boolean) {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences("dolby_first_use", Context.MODE_PRIVATE)
    }
    var dismissed by remember(prefs, topic) {
        mutableStateOf(prefs.all[topic.preferenceKey] == true)
    }
    DisposableEffect(prefs, topic) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == topic.preferenceKey) {
                dismissed = prefs.all[topic.preferenceKey] == true
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        listener.onSharedPreferenceChanged(prefs, topic.preferenceKey)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    // Adjacent pager pages are composed in advance. Show help only on the
    // settled, visible page after its settings have loaded successfully.
    if (!isVisible || dismissed) return

    fun dismiss() {
        dismissed = true
        prefs.edit().putBoolean(topic.preferenceKey, true).apply()
    }

    DolbyHelpDialog(
        title = stringResource(topic.title),
        message = stringResource(topic.message),
        onDismiss = ::dismiss,
    )
}

/** The same presentation is used for first-use tips and help reopened by the user. */
@Composable
internal fun DolbyHelpDialog(title: String, message: String, onDismiss: () -> Unit) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            TextButton(onClick = {
                view.performDolbySelectionHaptic()
                onDismiss()
            }) {
                Text(stringResource(R.string.dolby_help_got_it))
            }
        },
    )
}
