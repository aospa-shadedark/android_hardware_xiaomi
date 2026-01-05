/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Reads 10-, 15-, and 20-band .ldp files; exports the ten-band curve used by this editor. */
internal object PresetFileTransfer {
    private const val FORMAT_VERSION = 2
    private val frequencies = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun read(context: Context, uri: Uri): Preset {
        val source = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalArgumentException("Unable to read preset file")
        val json = JSONObject(source)
        require(json.optInt("version", 1) in 1..FORMAT_VERSION) { "Unsupported preset version" }
        val expectedBands = when (json.optString("bandMode", "10")) {
            "10" -> 10
            "15" -> 15
            "20" -> 20
            else -> throw IllegalArgumentException("Unsupported preset band mode")
        }
        if (json.has("bandCount")) {
            require(json.getInt("bandCount") == expectedBands) { "Preset band count does not match its mode" }
        }
        val bands = json.getJSONArray("bandGains")
        require(bands.length() == expectedBands) { "Preset band count does not match its mode" }
        val sourceBands = List(bands.length()) { index ->
            val band = bands.getJSONObject(index)
            val frequency = band.getInt("frequency")
            require(frequency > 0) { "Preset frequency is invalid" }
            val gain = band.getInt("gain")
            // Lunaris AutoEQ presets can reach +/-15 dB; this editor's DAX range is +/-10 dB.
            require(gain in -150..150) { "Preset gain is out of range" }
            frequency to gain
        }
        require(sourceBands.zipWithNext().all { (left, right) -> left.first < right.first }) {
            "Preset frequencies must be in ascending order"
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
        val name = json.getString("name").trim()
        require(name.isNotEmpty()) { "Preset name is empty" }
        return Preset(name = name, bandGains = gains, isUserDefined = true)
    }

    fun write(context: Context, uri: Uri, preset: Preset) {
        require(preset.bandGains.size == frequencies.size) { "Preset must contain ten bands" }
        val json = JSONObject().apply {
            put("version", FORMAT_VERSION)
            put("name", preset.name.orEmpty())
            put("timestamp", System.currentTimeMillis())
            put("createdBy", "Xiaomi Dolby")
            put("bandMode", "10")
            put("bandCount", frequencies.size)
            put("bandGains", JSONArray().apply {
                preset.bandGains.forEachIndexed { index, band ->
                    put(JSONObject().apply {
                        put("frequency", frequencies[index])
                        put("gain", band.gain)
                    })
                }
            })
        }
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(json.toString(2))
        } ?: throw IllegalArgumentException("Unable to create preset file")
    }
}
