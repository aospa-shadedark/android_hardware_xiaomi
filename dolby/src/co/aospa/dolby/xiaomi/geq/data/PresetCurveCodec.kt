/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

/** Parses the native representation without imposing the editor's gain limits on vendor curves. */
internal object PresetCurveCodec {
    fun parseNative(serialized: String): IntArray? {
        if (serialized.length > 1024) return null
        val tokens = serialized.split(',')
        if (tokens.size != 20) return null
        val gains = IntArray(tokens.size)
        tokens.forEachIndexed { index, token ->
            gains[index] = token.trim().toIntOrNull() ?: return null
        }
        return gains
    }

    fun parseEditable(serialized: String): IntArray? =
        parseNative(serialized)?.takeIf { gains -> gains.all { it in -100..100 } }
}
