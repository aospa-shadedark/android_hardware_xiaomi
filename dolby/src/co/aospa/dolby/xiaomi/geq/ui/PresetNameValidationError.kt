/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import co.aospa.dolby.xiaomi.R

enum class PresetNameValidationError {
    NAME_EMPTY,
    NAME_EXISTS,
    NAME_TOO_LONG,
    PROFILE_NOT_READY;

    @Composable
    fun toErrorMessage() =
        stringResource(
            id = when (this) {
                NAME_EMPTY -> R.string.dolby_geq_preset_name_empty
                NAME_EXISTS -> R.string.dolby_geq_preset_name_exists
                NAME_TOO_LONG -> R.string.dolby_geq_preset_name_too_long
                PROFILE_NOT_READY -> R.string.dolby_geq_profile_not_ready
            }
        )
}
