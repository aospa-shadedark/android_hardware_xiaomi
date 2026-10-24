/*
 * Copyright (C) 2023-26 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import co.aospa.dolby.xiaomi.geq.ui.EqualizerViewModel
import com.android.settingslib.spa.framework.theme.SettingsTheme

class DolbySettingsActivity : ComponentActivity() {

    private val dolbyController by lazy { DolbyController.getInstance(this) }
    private val equalizerViewModel: EqualizerViewModel by viewModels { EqualizerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                DolbyComposeScreen(
                    context = this,
                    controller = dolbyController,
                    equalizerViewModel = equalizerViewModel,
                    onResetAll = {
                        dolbyController.resetAllProfiles()
                        Toast.makeText(
                            this,
                            getString(R.string.dolby_reset_all_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }
        }
    }
}
