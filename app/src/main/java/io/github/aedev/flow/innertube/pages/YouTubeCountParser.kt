package io.github.aedev.flow.innertube.pages

import java.math.BigDecimal
import java.util.Locale

/**
 * Reads a count as YouTube renders it in the request's host language: an exact one
 * ("1.013.511 visualizaciones", "334 435 просмотров") or an abbreviated one ("2,4 M de
 * visualizaciones", "33万回視聴", "3.3 लाख व्यूज़"). The suffix is looked up in [hl]'s own table and
 * then as English K/M/B, which YouTube also uses in some languages ("334 K visualizaciones").
 */
internal object YouTubeCountParser {
    fun parse(
        text: String?,
        hl: String?,
        suffixSource: CompactSuffixSource = IcuCompactSuffixSource,
    ): Long? {
        val normalized = normalizeCountText(text ?: return null)
        val number = NUMBER.find(normalized) ?: return null
        val value = readNumber(number.value) ?: return null
        val rest = normalized.substring(number.range.last + 1).trimStart().lowercase(Locale.ROOT)
        val multiplier =
            hl
                ?.takeIf(String::isNotBlank)
                ?.let { matchSuffix(rest, suffixSource.suffixes(it)) }
                ?: matchSuffix(rest, ENGLISH_SUFFIXES)
                ?: 1L
        return runCatching { value.multiply(BigDecimal.valueOf(multiplier)).toLong() }.getOrNull()
    }

    private val NUMBER = Regex("""[0-9](?:[0-9.,'’ ٫٬]*[0-9])?""")

    private val ENGLISH_SUFFIXES = mapOf("k" to 1_000L, "m" to 1_000_000L, "b" to 1_000_000_000L)

    // A grouping separator is always followed by three digits and a compact decimal never is, so the
    // text itself tells "2,4 M" from "334.435" without trusting the locale's symbols, which differ
    // from what YouTube prints in some languages (Arabic's U+066B against YouTube's ".").
    private fun readNumber(token: String): BigDecimal? {
        val lastSeparator = token.indexOfLast { !it.isDigit() }
        val digitsAfter = token.length - lastSeparator - 1
        val isDecimal = lastSeparator >= 0 && token[lastSeparator] in DECIMAL_MARKS && digitsAfter != 3
        val plain =
            if (isDecimal) {
                token.substring(0, lastSeparator).filter(Char::isDigit) + "." + token.substring(lastSeparator + 1)
            } else {
                token.filter(Char::isDigit)
            }
        return plain.toBigDecimalOrNull()
    }

    private fun matchSuffix(
        rest: String,
        suffixes: Map<String, Long>,
    ): Long? =
        suffixes.entries
            .filter { (suffix, _) -> rest.startsWith(suffix) && endsWord(rest, suffix) }
            .maxByOrNull { (suffix, _) -> suffix.length }
            ?.value

    // Han and Hangul suffixes run straight into the next word ("33万回視聴", "33만회"); alphabetic
    // ones must end where a word does, or "mil" would read as English "m".
    private fun endsWord(
        rest: String,
        suffix: String,
    ): Boolean {
        val next = rest.getOrNull(suffix.length) ?: return true
        val last = suffix.last()
        val joinsNext =
            Character.UnicodeScript.of(last.code) in UNSPACED_SCRIPTS ||
                !last.isLetter()
        return joinsNext || !next.isLetter()
    }

    private val UNSPACED_SCRIPTS = setOf(Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL)
    private const val DECIMAL_MARKS = ".,٫"
}
