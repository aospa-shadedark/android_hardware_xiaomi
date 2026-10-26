/*
 * Copyright (C) 2026 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.xiaomi.geq.data

/** Keeps the legacy editor mapping separate from the device's native GEQ bands. */
object EqualizerCurve {
    val standardFrequencies = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    // GEQ order in the device DAX configuration. These are not the IEQ preset controls.
    val nativeFrequencies = listOf(
        47, 141, 234, 328, 469, 656, 844, 1031, 1313, 1688,
        2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688,
    )

    fun isEditable(bands: List<BandGain>): Boolean {
        val frequencies = frequenciesFor(bands.size) ?: return false
        return hasFrequencies(bands, frequencies) && bands.all { it.gain in -100..100 }
    }

    /** Expands a ten-band preset with the original mapping, or keeps all native points. */
    fun toNative(bands: List<BandGain>): List<BandGain> {
        val frequencies = requireNotNull(frequenciesFor(bands.size)) { "Preset curve is invalid" }
        require(hasFrequencies(bands, frequencies)) { "Preset frequencies are invalid" }
        if (bands.size == nativeFrequencies.size) return bands.map { it.copy() }
        return nativeFrequencies.mapIndexed { index, frequency ->
            val gain = if (index % 2 == 1 && index < nativeFrequencies.lastIndex) {
                ((bands[(index - 1) / 2].gain.toLong() + bands[(index + 1) / 2].gain) / 2).toInt()
            } else {
                bands[index / 2].gain
            }
            BandGain(frequency, gain)
        }
    }

    /** Returns the original ten controls without changing the underlying twenty-point curve. */
    fun toStandard(nativeBands: List<BandGain>): List<BandGain> {
        requireNativeOrder(nativeBands)
        return standardFrequencies.mapIndexed { index, frequency ->
            BandGain(frequency, nativeBands[index * 2].gain)
        }
    }

    /** Applies legacy interpolation changes while retaining detail added in Pro. */
    fun mergeStandard(
        nativeBands: List<BandGain>,
        standardBands: List<BandGain>,
    ): List<BandGain> {
        requireNativeOrder(nativeBands)
        require(hasFrequencies(standardBands, standardFrequencies)) {
            "Preset must contain the ten standard bands"
        }
        val previousBase = toNative(toStandard(nativeBands))
        val replacementBase = toNative(standardBands)
        return nativeBands.mapIndexed { index, band ->
            val delta = replacementBase[index].gain.toLong() - previousBase[index].gain
            if (delta == 0L) band.copy() else band.copy(gain = (band.gain + delta).coerceIn(-100, 100).toInt())
        }
    }

    fun deserializeNative(serialized: String): List<BandGain>? {
        val gains = PresetCurveCodec.parseNative(serialized) ?: return null
        return nativeFrequencies.mapIndexed { index, frequency -> BandGain(frequency, gains[index]) }
    }

    fun serialize(bands: List<BandGain>): String {
        require(isEditable(bands)) { "Preset must contain ten or twenty bands within the gain range" }
        return toNative(bands).joinToString(",") { it.gain.toString() }
    }

    private fun requireNativeOrder(bands: List<BandGain>) {
        require(hasFrequencies(bands, nativeFrequencies)) { "Preset must contain the twenty native bands" }
    }

    private fun frequenciesFor(size: Int): List<Int>? = when (size) {
        standardFrequencies.size -> standardFrequencies
        nativeFrequencies.size -> nativeFrequencies
        else -> null
    }

    private fun hasFrequencies(bands: List<BandGain>, frequencies: List<Int>): Boolean =
        bands.size == frequencies.size && bands.withIndex().all { (index, band) ->
            band.band == frequencies[index]
        }
}
