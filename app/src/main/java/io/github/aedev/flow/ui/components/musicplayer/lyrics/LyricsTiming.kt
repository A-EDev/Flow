package io.github.aedev.flow.ui.components.musicplayer.lyrics

import io.github.aedev.flow.data.lyrics.WordTimestamp
import java.text.Normalizer

/** Roughly how long a sung syllable lasts; spreads a short line's fill when its end is unknown. */
private const val SYLLABLE_MS = 320L

/** The slowest a syllable is drawn out before the rest of a long gap is treated as a pause. */
private const val MAX_SYLLABLE_MS = 650L

/** The fastest a syllable is filled, so a crowded line still reads as sung, not flashed. */
private const val MIN_SYLLABLE_MS = 110L

private const val MAX_BREATH_MS = 450L
private const val BREATH_SHARE = 0.12

private val LATIN_VOWELS = "aeiouy".toSet()

/**
 * Word timings for a line that only has a start time: the line's words are spread across the time
 * until [nextLineMs], weighted by their syllables, leaving a short breath before the next line. A
 * long gap after the line is not stretched into: syllables cap at a natural length and the rest
 * becomes a pause. Scripts written without spaces (Chinese, Japanese) are filled per character.
 */
internal fun synthesizeWordTimings(
    text: String,
    lineStartMs: Long,
    nextLineMs: Long?,
): List<WordTimestamp> {
    val tokens = lyricTokens(text)
    if (tokens.isEmpty()) return emptyList()
    val weights = tokens.map { syllableWeight(it) }
    val totalWeight = weights.sum().coerceAtLeast(1)

    val available =
        nextLineMs
            ?.minus(lineStartMs)
            ?.takeIf { it > 0 }
            ?: (totalWeight * SYLLABLE_MS + MAX_BREATH_MS)
    val breath = minOf(MAX_BREATH_MS, (available * BREATH_SHARE).toLong())
    val sung =
        (available - breath)
            .coerceAtMost(totalWeight * MAX_SYLLABLE_MS)
            .coerceAtLeast(minOf(available, totalWeight * MIN_SYLLABLE_MS))

    var cursor = lineStartMs
    return tokens.mapIndexed { index, token ->
        val duration = sung * weights[index] / totalWeight
        val start = cursor
        cursor += duration
        WordTimestamp(text = token, startTime = start, endTime = cursor)
    }
}

/** Words split on spaces; a line written without spaces in a CJK script is split per character. */
internal fun lyricTokens(text: String): List<String> {
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.size != 1 || !words.first().any(::isCjk)) return words
    val result = mutableListOf<String>()
    var i = 0
    val word = words.first()
    while (i < word.length) {
        val next = word.offsetByCodePoints(i, 1)
        val piece = word.substring(i, next)
        // Punctuation rides with the character before it rather than taking a beat of its own.
        if (result.isNotEmpty() && !piece.codePointAt(0).let { Character.isLetterOrDigit(it) }) {
            result[result.lastIndex] = result.last() + piece
        } else {
            result += piece
        }
        i = next
    }
    return result
}

/** A rough syllable count: vowel groups for alphabetic scripts, one per character for CJK. */
internal fun syllableWeight(token: String): Int {
    if (token.any(::isCjk)) return token.count { isCjk(it) }.coerceAtLeast(1)
    val plain =
        Normalizer
            .normalize(token.lowercase(), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
    val letters = plain.count(Char::isLetter)
    if (letters == 0) return 1
    val hasLatinVowels = plain.any { it in LATIN_VOWELS }
    if (!hasLatinVowels) {
        // Abjads and scripts without these vowels: about one syllable per two or three letters.
        return ((letters + 2) / 3).coerceAtLeast(1)
    }
    var groups = 0
    var inVowel = false
    for (c in plain) {
        val vowel = c in LATIN_VOWELS
        if (vowel && !inVowel) groups++
        inVowel = vowel
    }
    return groups.coerceAtLeast(1)
}

private fun isCjk(c: Char): Boolean {
    val block = Character.UnicodeBlock.of(c)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}

/**
 * The character range each word covers in [mainText], found in order, or null when a word cannot
 * be located. Background lines have their outer parentheses stripped from the text, so the first
 * and last words are matched without them.
 */
internal fun wordCharRanges(
    mainText: String,
    words: List<WordTimestamp>,
    isBackground: Boolean,
): List<IntRange?> {
    var cursor = 0
    return words.mapIndexed { index, word ->
        var raw = word.text.trim()
        if (isBackground) {
            if (index == 0) raw = raw.removePrefix("(")
            if (index == words.lastIndex) raw = raw.removeSuffix(")")
        }
        if (raw.isEmpty()) return@mapIndexed null
        val start = mainText.indexOf(raw, cursor)
        if (start == -1) return@mapIndexed null
        cursor = start + raw.length
        start until cursor
    }
}
