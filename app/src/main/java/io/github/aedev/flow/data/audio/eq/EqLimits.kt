package io.github.aedev.flow.data.audio.eq

import kotlin.math.abs
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

    /**
     * The ten gains whose response comes closest to [curve] across the whole range: a least-squares
     * fit on a log grid, with a penalty on the step between neighbouring bands so the sliders do not zigzag, then refined
     * against the real, overlapping filters.
     */
    fun fit(curve: EqCurve): EqCurve {
        val grid = List(FIT_POINTS) { EqFilterMath.frequencyAt(it.toDouble() / (FIT_POINTS - 1)) }
        val target = grid.map { EqFilterMath.magnitudeDb(curve.bands, it) }
        val basis =
            FREQUENCIES.map { centre ->
                val unit = listOf(EqBand(centre, 1.0, Q))
                grid.map { EqFilterMath.magnitudeDb(unit, it) }
            }
        var gains = List(FREQUENCIES.size) { 0.0 }
        repeat(FIT_PASSES) {
            val bands = curveOf(gains).bands
            val residual = grid.mapIndexed { k, f -> target[k] - EqFilterMath.magnitudeDb(bands, f) }
            val step = solveDamped(basis, residual)
            gains = gains.mapIndexed { i, gain -> (gain + step[i]).coerceIn(-MAX_GAIN, MAX_GAIN) }
        }
        return curveOf(gains.map { round(it / STEP) * STEP + 0.0 }, curve.preamp)
    }

    /** Solves (B·Bᵀ + λ·DᵀD)·x = B·r, D being the difference between neighbouring bands, by Gaussian elimination. */
    private fun solveDamped(
        basis: List<List<Double>>,
        residual: List<Double>,
    ): DoubleArray {
        val n = basis.size
        val a = Array(n) { i -> DoubleArray(n + 1) }
        for (i in 0 until n) {
            for (j in 0 until n) a[i][j] = basis[i].indices.sumOf { k -> basis[i][k] * basis[j][k] }
            a[i][i] += FIT_SMOOTHING * (if (i == 0 || i == n - 1) 1 else 2)
            if (i > 0) a[i][i - 1] -= FIT_SMOOTHING
            if (i < n - 1) a[i][i + 1] -= FIT_SMOOTHING
            a[i][n] = basis[i].indices.sumOf { k -> basis[i][k] * residual[k] }
        }
        for (col in 0 until n) {
            val pivot = (col until n).maxBy { abs(a[it][col]) }
            val swap = a[col]
            a[col] = a[pivot]
            a[pivot] = swap
            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col] / a[col][col]
                for (c in col..n) a[row][c] -= factor * a[col][c]
            }
        }
        return DoubleArray(n) { a[it][n] / a[it][it] }
    }

    private const val FIT_POINTS = 96
    private const val FIT_PASSES = 4
    private const val FIT_SMOOTHING = 6.0

    /** Reads a stored graphic curve back as ten gains; anything malformed becomes flat. */
    fun gainsOf(curve: EqCurve): List<Double> =
        if (curve.bands.size == FREQUENCIES.size) {
            curve.bands.map { it.gain.coerceIn(-MAX_GAIN, MAX_GAIN) }
        } else {
            List(FREQUENCIES.size) { 0.0 }
        }
}
