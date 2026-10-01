/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * How the engine reads raw text before it becomes topics: styled letters fold to plain ones, short
 * topic names survive, and scripts written without spaces are split into words (#907).
 */
internal object NeuroText {
    /** Two-letter names that are whole topics. Everything else under three letters is noise. */
    val SHORT_TOPICS = setOf("ai", "ml", "2d", "3d", "4k", "8k", "f1", "pc", "tv", "vr", "ar", "dj", "ui", "ux", "ev", "rc", "uk")

    // NFKC has no mapping for small capitals, a common way to style titles.
    private val SmallCaps =
        mapOf(
            'ᴀ' to 'a',
            'ʙ' to 'b',
            'ᴄ' to 'c',
            'ᴅ' to 'd',
            'ᴇ' to 'e',
            'ꜰ' to 'f',
            'ɢ' to 'g',
            'ʜ' to 'h',
            'ɪ' to 'i',
            'ᴊ' to 'j',
            'ᴋ' to 'k',
            'ʟ' to 'l',
            'ᴍ' to 'm',
            'ɴ' to 'n',
            'ᴏ' to 'o',
            'ᴘ' to 'p',
            'ꞯ' to 'q',
            'ʀ' to 'r',
            'ꜱ' to 's',
            'ᴛ' to 't',
            'ᴜ' to 'u',
            'ᴠ' to 'v',
            'ᴡ' to 'w',
            'ʏ' to 'y',
            'ᴢ' to 'z',
        )

    private val UnspacedScripts =
        setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.THAI,
            Character.UnicodeScript.LAO,
            Character.UnicodeScript.KHMER,
            Character.UnicodeScript.MYANMAR,
        )

    private val Whitespace = Regex("\\s+")

    private val wordBreaks = ThreadLocal.withInitial { BreakIterator.getWordInstance(Locale.ROOT) }

    /** Lowercase plain letters: "𝙋𝙃𝙊𝙉𝙆", "ｐｈｏｎｋ", "ⓟⓗⓞⓝⓚ" and "ᴘʜᴏɴᴋ" all read "phonk". */
    fun fold(text: String): String {
        if (text.all { it.code < 0x80 }) return text.lowercase()
        // NFKC splits Thai and Lao SARA AM into two characters no dictionary or title uses; put it back.
        val normalized =
            Normalizer
                .normalize(text, Normalizer.Form.NFKC)
                .replace("\u0E4D\u0E32", "\u0E33")
                .replace("\u0ECD\u0EB2", "\u0EB3")
        val plain = if (normalized.any { it in SmallCaps }) normalized.map { SmallCaps[it] ?: it }.joinToString("") else normalized
        return plain.lowercase()
    }

    /**
     * The words of folded text. Chinese, Japanese, Thai, Lao, Khmer and Burmese runs are split by the
     * platform's dictionary word breaker, since those scripts put no spaces between words.
     */
    fun words(folded: String): List<String> =
        folded.split(Whitespace).flatMap { chunk ->
            if (chunk.any(::isUnspaced)) segment(chunk) else listOf(chunk)
        }

    /** A letter, digit or combining mark: the marks spell vowels and tones in Thai and Devanagari words. */
    fun isWordChar(c: Char): Boolean =
        c.isLetterOrDigit() ||
            when (Character.getType(c).toByte()) {
                Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
                else -> false
            }

    /** Long enough to be a topic: three letters, a known short name, or two characters of an unspaced script. */
    fun isTopicSized(word: String): Boolean =
        when {
            word.length >= 3 -> !isGrammarKana(word)
            word in SHORT_TOPICS -> true
            word.length == 2 -> word.all(::isUnspaced) && !isGrammarKana(word)
            else -> false
        }

    private fun isUnspaced(c: Char): Boolean = !c.isWhitespace() && Character.UnicodeScript.of(c.code) in UnspacedScripts

    // Hiragana-only words are almost always particles and inflections, not subjects.
    private fun isGrammarKana(word: String): Boolean = word.all { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HIRAGANA }

    private fun segment(chunk: String): List<String> {
        val breaks = wordBreaks.get()
        breaks.setText(chunk)
        val words = ArrayList<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            val word = chunk.substring(start, end)
            if (word.any(Char::isLetterOrDigit)) words += word
            start = end
            end = breaks.next()
        }
        return words
    }
}
