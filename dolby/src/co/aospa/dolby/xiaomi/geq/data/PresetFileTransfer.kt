/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.roundToInt

/** Keeps ten-band presets compatible and preserves native detail for Pro presets. */
internal object PresetFileTransfer {
    private const val FORMAT_VERSION = 2
    private const val MAX_FILE_BYTES = 64 * 1024
    private const val MAX_JSON_DEPTH = 32

    fun read(context: Context, uri: Uri): Preset {
        val source = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = ByteArray(MAX_FILE_BYTES)
            var length = 0
            while (length < bytes.size) {
                val count = input.read(bytes, length, bytes.size - length)
                if (count < 0) break
                if (count == 0) {
                    val next = input.read()
                    if (next < 0) break
                    bytes[length++] = next.toByte()
                } else {
                    length += count
                }
            }
            // Read one sentinel byte to detect oversize without buffering more input.
            require(length < bytes.size || input.read() == -1) { "Preset file is too large" }
            String(bytes, 0, length, Charsets.UTF_8)
        } ?: throw IllegalArgumentException("Unable to read preset file")
        requireShallowJson(source)
        val tokener = JSONTokener(source)
        val json = JSONObject(tokener)
        while (tokener.more()) {
            require(tokener.next() in " \t\r\n") { "Unexpected content after preset" }
        }
        val version = if (json.has("version")) json.requireInt("version") else 1
        require(version in 1..FORMAT_VERSION) { "Unsupported preset version" }
        val expectedBands = if (!json.has("bandMode")) {
            10
        } else {
            when (json.get("bandMode")) {
                "10" -> 10
                "15" -> 15
                "20" -> 20
                is Number -> json.requireInt("bandMode")
                else -> throw IllegalArgumentException("Unsupported preset band mode")
            }
        }
        require(expectedBands in listOf(10, 15, 20)) { "Unsupported preset band mode" }
        if (json.has("bandCount")) {
            require(json.requireInt("bandCount") == expectedBands) { "Preset band count does not match its mode" }
        }
        val bands = json.getJSONArray("bandGains")
        require(bands.length() == expectedBands) { "Preset band count does not match its mode" }
        val sourceBands = List(bands.length()) { index ->
            val band = bands.getJSONObject(index)
            val frequency = band.requireInt("frequency")
            require(frequency > 0) { "Preset frequency is invalid" }
            val gain = band.requireInt("gain")
            // Lunaris AutoEQ presets can reach +/-15 dB; this editor's DAX range is +/-10 dB.
            require(gain in -150..150) { "Preset gain is out of range" }
            frequency to gain
        }
        require(sourceBands.zipWithNext().all { (left, right) -> left.first < right.first }) {
            "Preset frequencies must be in ascending order"
        }
        // Standard presets retain the original ten-band mapping. Curves with
        // more points are resampled to the device's actual twenty GEQ bands.
        val frequencies = if (expectedBands == 10) {
            EqualizerCurve.standardFrequencies
        } else {
            EqualizerCurve.nativeFrequencies
        }
        val gains = frequencies.map { frequency ->
            val target = kotlin.math.ln(frequency.toDouble())
            val upperIndex = sourceBands.indexOfFirst { kotlin.math.ln(it.first.toDouble()) >= target }
            val gain = when {
                upperIndex < 0 -> sourceBands.last().second.toDouble()
                upperIndex == 0 -> sourceBands.first().second.toDouble()
                else -> {
                    val lower = sourceBands[upperIndex - 1]
                    val upper = sourceBands[upperIndex]
                    val fraction = (target - kotlin.math.ln(lower.first.toDouble())) /
                        (kotlin.math.ln(upper.first.toDouble()) - kotlin.math.ln(lower.first.toDouble()))
                    lower.second + fraction * (upper.second - lower.second)
                }
            }.roundToInt().coerceIn(-100, 100)
            BandGain(frequency, gain)
        }
        val rawName = json.get("name")
        require(rawName is String && rawName.length <= 50) { "Preset name is invalid" }
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Preset name is empty" }
        return Preset(name = name, bandGains = gains, isUserDefined = true)
    }

    fun write(context: Context, uri: Uri, preset: Preset) {
        require(EqualizerCurve.isEditable(preset.bandGains)) { "Preset curve is invalid" }
        val name = requireNotNull(preset.name) { "Preset name is invalid" }
        require(name.isNotBlank() && name.length <= 50) { "Preset name is invalid" }
        val json = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("name", name)
            put("timestamp", System.currentTimeMillis())
            put("createdBy", "Xiaomi Dolby")
            put("bandMode", preset.bandGains.size.toString())
            put("bandCount", preset.bandGains.size)
            put("bandGains", JSONArray().apply {
                preset.bandGains.forEach { band ->
                    put(JSONObject().apply {
                        put("frequency", band.band)
                        put("gain", band.gain)
                    })
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(json.toString(2))
        } ?: throw IllegalArgumentException("Unable to create preset file")
    }

    private fun JSONObject.requireInt(key: String): Int {
        val value = get(key)
        require(value is Number) { "Preset $key must be a number" }
        val number = value.toDouble()
        require(number.isFinite() && number >= Int.MIN_VALUE.toDouble() &&
            number <= Int.MAX_VALUE.toDouble() && number == number.toInt().toDouble()) {
            "Preset $key must be an integer in range"
        }
        return number.toInt()
    }

    private fun requireShallowJson(source: String) {
        // Scan without recursive parsing before JSONObject can recurse through untrusted data.
        val tokener = JSONTokener(source)
        var depth = 0
        while (tokener.more()) {
            when (val next = tokener.nextClean()) {
                '{', '[' -> {
                    depth++
                    require(depth <= MAX_JSON_DEPTH) { "Preset JSON is too deeply nested" }
                }
                '}', ']' -> {
                    depth--
                    require(depth >= 0) { "Preset JSON is malformed" }
                }
                '\"', '\'' -> tokener.nextString(next)
                ':', ',', ';', '=' -> Unit
                '/', '\\' -> throw IllegalArgumentException("Preset JSON is malformed")
                else -> tokener.nextTo("{}[]/\\:,=;# \t\u000c")
            }
        }
        require(depth == 0) { "Preset JSON is malformed" }
    }
}
