package io.github.aedev.flow.data.audio.eq

import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

/** What an EqualizerAPO / AutoEQ `ParametricEQ.txt` import produced, and what it left out. */
data class AutoEqImport(
    val curve: EqCurve,
    val skippedFilters: Int,
    val ignoredLines: Int,
    val truncatedFilters: Int,
)

/**
 * Reads and writes the EqualizerAPO parametric text format that AutoEQ publishes, for example
 * `Filter 1: ON PK Fc 105 Hz Gain -2.3 dB Q 0.70`. Lenient about case, spacing, comma decimals and
 * the short type names; strict about anything that would break the audio, such as Q 0.
 */
object AutoEqFormat {
    private val FILTER_PREFIX = Regex("^filter\\s*\\d*\\s*:", RegexOption.IGNORE_CASE)
    private val PREAMP_PREFIX = Regex("^preamp\\s*:", RegexOption.IGNORE_CASE)
    private val NUMBER = Regex("^[-+]?\\d+(?:[.,]\\d+)?$")
    private val THOUSANDS = Regex("^\\d{1,2},\\d{3}$")

    private val TYPES =
        mapOf(
            "PK" to EqFilterType.PEAK,
            "PEQ" to EqFilterType.PEAK,
            "MODAL" to EqFilterType.PEAK,
            "LS" to EqFilterType.LOW_SHELF,
            "LSC" to EqFilterType.LOW_SHELF,
            "LSQ" to EqFilterType.LOW_SHELF,
            "HS" to EqFilterType.HIGH_SHELF,
            "HSC" to EqFilterType.HIGH_SHELF,
            "HSQ" to EqFilterType.HIGH_SHELF,
            "LP" to EqFilterType.LOW_PASS,
            "LPQ" to EqFilterType.LOW_PASS,
            "HP" to EqFilterType.HIGH_PASS,
            "HPQ" to EqFilterType.HIGH_PASS,
        )

    /** Null when the text holds no usable filter and no preamp. */
    fun parse(text: String): AutoEqImport? {
        var preamp: Double? = null
        val bands = mutableListOf<EqBand>()
        var skipped = 0
        var ignored = 0
        for (raw in text.lineSequence()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue
            when {
                PREAMP_PREFIX.containsMatchIn(line) -> {
                    preamp = line.substringAfter(':').tokens().firstNotNullOfOrNull { it.toNumber() } ?: preamp
                }

                FILTER_PREFIX.containsMatchIn(line) -> {
                    val band = parseFilter(line.substringAfter(':').tokens())
                    if (band != null) bands += band else skipped++
                }

                else -> {
                    ignored++
                }
            }
        }
        if (bands.isEmpty() && preamp == null) return null
        val truncated = (bands.size - EqLimits.MAX_BANDS).coerceAtLeast(0)
        return AutoEqImport(
            curve = EqCurve(preamp = preamp ?: 0.0, bands = bands.take(EqLimits.MAX_BANDS)).sanitized(),
            skippedFilters = skipped,
            ignoredLines = ignored,
            truncatedFilters = truncated,
        )
    }

    fun format(curve: EqCurve): String =
        buildString {
            append("Preamp: ").append(curve.preamp.format(1)).append(" dB\n")
            curve.bands.forEachIndexed { index, band ->
                append("Filter ").append(index + 1).append(": ")
                append(if (band.enabled) "ON " else "OFF ")
                append(band.type.apoCode)
                append(" Fc ").append(band.frequency.format(0)).append(" Hz")
                if (band.type.hasGain) append(" Gain ").append(band.gain.format(1)).append(" dB")
                append(" Q ").append(band.q.format(3)).append('\n')
            }
        }

    private fun parseFilter(tokens: List<String>): EqBand? {
        if (tokens.size < 2 || !tokens[0].equals("ON", ignoreCase = true)) return null
        val type = TYPES[tokens[1].uppercase(Locale.ROOT)] ?: return null
        var frequency: Double? = null
        var gain = 0.0
        var q: Double? = null
        var i = 2
        while (i < tokens.size) {
            val key = tokens[i].lowercase(Locale.ROOT)
            val value = tokens.getOrNull(i + 1)?.toNumber()
            when {
                key == "fc" && value != null -> {
                    frequency = tokens[i + 1].toFrequency()
                    i += 2
                }

                key == "gain" && value != null -> {
                    gain = value
                    i += 2
                }

                key == "q" && value != null -> {
                    q = value
                    i += 2
                }

                key == "bw" && tokens.getOrNull(i + 1).equals("oct", ignoreCase = true) -> {
                    q = tokens.getOrNull(i + 2)?.toNumber()?.let(::bandwidthToQ) ?: q
                    i += 3
                }

                else -> {
                    i++
                }
            }
        }
        return EqBand(
            frequency = frequency ?: return null,
            gain = gain,
            q = q ?: EqLimits.defaultQ(type),
            type = type,
        ).sanitized()
    }

    private fun bandwidthToQ(octaves: Double): Double? {
        if (octaves <= 0.0) return null
        val ratio = 2.0.pow(octaves)
        return sqrt(ratio) / (ratio - 1.0)
    }

    private fun String.tokens(): List<String> = trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun String.toNumber(): Double? = if (NUMBER.matches(this)) replace(',', '.').toDoubleOrNull() else null

    /** "1,000" in a frequency is a thousands separator: no one sets a filter at 1 Hz. */
    private fun String.toFrequency(): Double? = if (THOUSANDS.matches(this)) replace(",", "").toDoubleOrNull() else toNumber()

    private fun Double.format(decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", this)
}
