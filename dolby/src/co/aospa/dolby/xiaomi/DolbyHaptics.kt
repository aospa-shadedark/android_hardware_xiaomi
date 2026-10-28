/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi

import android.view.HapticFeedbackConstants
import android.view.View

internal fun View.performDolbyToggleHaptic(enabled: Boolean) {
    performHapticFeedback(
        if (enabled) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF,
    )
}

internal fun View.performDolbySelectionHaptic() {
    performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
}

internal fun View.performDolbySliderReleaseHaptic() {
    performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
}

internal fun View.performDolbySliderTickHaptic() {
    performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
}
