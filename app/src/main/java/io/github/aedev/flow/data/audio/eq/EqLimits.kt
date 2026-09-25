package io.github.aedev.flow.data.audio.eq

import kotlin.math.pow
import kotlin.math.round

object EqLimits {
    const val MIN_FREQUENCY = 20.0
    const val MAX_FREQUENCY = 20_000.0
    const val MIN_GAIN = -24.0
    const val MAX_GAIN = 24.0
    const val MIN_Q = 0.1
    const val MAX_Q = 20.0
    const val MIN_PREAMP = -24.0
    const val MAX_PREAMP = 24.0
    const val MAX_BANDS = 20
    const val MAX_BASS_BOOST = 15.0
    const val DEFAULT_PEAK_Q = 1.41

    /** Butterworth Q: the slope the old processor gave every shelf, whatever Q it was handed. */
    const val DEFAULT_SHELF_Q = 0.707
    const val BASS_BOOST_FREQUENCY = 60.0
    const val MAX_NAME_LENGTH = 40

    fun defaultQ(type: EqFilterType): Double = if (type == EqFilterType.PEAK) DEFAULT_PEAK_Q else DEFAULT_SHELF_Q
}

fun Double.roundToDecimals(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return round(this * factor) / factor
}

/** Null when the band cannot be made valid (not a number, or a zero or negative frequency or Q). */
fun EqBand.sanitized(): EqBand? {
    if (!frequency.isFinite() || !gain.isFinite() || !q.isFinite()) return null
    if (frequency <= 0.0 || q <= 0.0) return null
    return copy(
        frequency = frequency.coerceIn(EqLimits.MIN_FREQUENCY, EqLimits.MAX_FREQUENCY),
        gain = if (type.hasGain) gain.coerceIn(EqLimits.MIN_GAIN, EqLimits.MAX_GAIN) else 0.0,
        q = q.coerceIn(EqLimits.MIN_Q, EqLimits.MAX_Q),
    )
}

fun EqCurve.sanitized(): EqCurve =
    EqCurve(
        preamp = if (preamp.isFinite()) preamp.coerceIn(EqLimits.MIN_PREAMP, EqLimits.MAX_PREAMP) else 0.0,
        bands = bands.mapNotNull { it.sanitized() }.take(EqLimits.MAX_BANDS),
    )

/** Ten octave bands, 31 Hz to 16 kHz, as on Flow Desktop. */
object GraphicEq {
    val FREQUENCIES = listOf(31.0, 63.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)
    const val Q = 1.41
    const val MAX_GAIN = 12.0
    const val STEP = 0.5

    fun flatCurve(): EqCurve = curveOf(List(FREQUENCIES.size) { 0.0 })

    fun curveOf(
        gains: List<Double>,
        preamp: Double = 0.0,
    ): EqCurve =
        EqCurve(
            preamp = preamp,
            bands =
                FREQUENCIES.mapIndexed { index, frequency ->
                    EqBand(frequency = frequency, gain = gains.getOrElse(index) { 0.0 }, q = Q)
                },
        )

    /** Reads a stored graphic curve back as ten gains; anything malformed becomes flat. */
    fun gainsOf(curve: EqCurve): List<Double> =
        if (curve.bands.size == FREQUENCIES.size) {
            curve.bands.map { it.gain.coerceIn(-MAX_GAIN, MAX_GAIN) }
        } else {
            List(FREQUENCIES.size) { 0.0 }
        }
}
