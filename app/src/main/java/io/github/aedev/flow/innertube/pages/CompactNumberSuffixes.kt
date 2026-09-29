package io.github.aedev.flow.innertube.pages

import android.icu.text.CompactDecimalFormat
import android.icu.util.ULocale
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** The suffixes a host language abbreviates counts with ("M", "Mio.", "万", "लाख"), each to its multiplier. */
internal fun interface CompactSuffixSource {
    /** Lowercased suffix to multiplier for [hl]; empty when the language is unknown. */
    fun suffixes(hl: String): Map<String, Long>
}

/**
 * Reads the suffixes off the platform's own CLDR data by formatting powers of ten, so every
 * language YouTube renders counts in is covered without a hand-kept table. Plural forms of the
 * long style ("millón", "millones", "миллиона") come from formatting 1, 2 and 5 of each magnitude.
 */
internal object IcuCompactSuffixSource : CompactSuffixSource {
    private val cache = ConcurrentHashMap<String, Map<String, Long>>()

    override fun suffixes(hl: String): Map<String, Long> = cache.getOrPut(hl.lowercase(Locale.ROOT)) { build(hl) }

    // Plain JVM unit tests see android.jar stubs here, which answer null; that reads as no table.
    private fun build(hl: String): Map<String, Long> =
        runCatching {
            val locale = ULocale.forLanguageTag(hl.replace('_', '-'))
            val table = LinkedHashMap<String, Long>()
            for (style in listOf(CompactDecimalFormat.CompactStyle.SHORT, CompactDecimalFormat.CompactStyle.LONG)) {
                val format = CompactDecimalFormat.getInstance(locale, style)
                for (exponent in 3..12) {
                    val magnitude = POWERS_OF_TEN[exponent]
                    for (lead in PLURAL_LEADS) {
                        val value = lead * magnitude
                        val (shown, suffix) = splitFormatted(format.format(value)) ?: continue
                        if (suffix.isEmpty() || shown <= 0L || value % shown != 0L) continue
                        table.putIfAbsent(suffix, value / shown)
                    }
                }
            }
            table.toMap()
        }.getOrElse { emptyMap() }

    private fun splitFormatted(formatted: String?): Pair<Long, String>? {
        val text = normalizeCountText(formatted ?: return null)
        val digits = text.filter(Char::isDigit)
        if (digits.isEmpty() || digits.length > 15) return null
        val suffix =
            text
                .filterNot { it.isDigit() || it in NUMBER_PUNCTUATION }
                .trim()
                .lowercase(Locale.ROOT)
        return digits.toLong() to suffix
    }

    private val POWERS_OF_TEN = LongArray(13) { exponent -> (1..exponent).fold(1L) { acc, _ -> acc * 10L } }
    private val PLURAL_LEADS = longArrayOf(1L, 2L, 5L)
    private const val NUMBER_PUNCTUATION = ".,'’\u066B\u066C"
}

private const val BIDI_MARKS = "\u200E\u200F\u061C"

/**
 * Plain spaces for the no-break and thin ones number formats use, ASCII digits for every script's,
 * and no direction marks.
 */
internal fun normalizeCountText(raw: String): String =
    buildString(raw.length) {
        for (char in raw) {
            when {
                char in BIDI_MARKS -> Unit
                char.isDigit() -> append('0' + Character.digit(char, 10))
                Character.isSpaceChar(char) || char.isWhitespace() -> append(' ')
                else -> append(char)
            }
        }
    }
