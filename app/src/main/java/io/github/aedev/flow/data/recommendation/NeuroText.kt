/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/** Dictionary word breaker: consecutive tokens of a chunk, punctuation included. */
internal fun interface WordBreaker {
    fun split(chunk: String): List<String>
}

/** `java.text.BreakIterator`; ICU on Android. */
internal object PlatformWordBreaker : WordBreaker {
    private val wordBreaks = ThreadLocal.withInitial { BreakIterator.getWordInstance(Locale.ROOT) }

    override fun split(chunk: String): List<String> {
        val breaks = wordBreaks.get()
        breaks.setText(chunk)
        val tokens = ArrayList<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            tokens += chunk.substring(start, end)
            start = end
            end = breaks.next()
        }
        return tokens
    }
}

/** Han fold of [NeuroText.fold]: [UNLESS_KANA] skips strings with kana, [ALWAYS] ignores kana. */
internal enum class HanFold { NONE, UNLESS_KANA, ALWAYS }

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

    private val Apostrophes = setOf('\u2019', '\u2018', '\u02BC', '`', '\u00B4')

    // Turkish dotted capital I, the emoji variation selector and joiner, and the Unicode hyphens.
    private val Marks = setOf('\u0130', '\uFE0F', '\u200D', '\u2010', '\u2011')

    /** `resources/neuro/fold/hant-hans.txt`, from `scripts/neuro/gen_hant_hans.py`. */
    private val HantToHans: Map<Char, Char> by lazy {
        val stream = NeuroText::class.java.getResourceAsStream("/neuro/fold/hant-hans.txt") ?: return@lazy emptyMap()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.length == 2 && !it.startsWith("#") }.associate { it[0] to it[1] }
        }
    }

    /** [joined]: no character between this word and the previous one. */
    data class Piece(
        val text: String,
        val joined: Boolean,
    )

    /**
     * Lowercase plain letters: "𝙋𝙃𝙊𝙉𝙆", "ｐｈｏｎｋ", "ⓟⓗⓞⓝⓚ" and "ᴘʜᴏɴᴋ" all read "phonk". Every apostrophe
     * reads ', Turkish İ reads i, and emoji joiners are dropped ([NeuroKeySpec] stage 1). [han]: [HanFold].
     */
    fun fold(
        text: String,
        han: HanFold = HanFold.UNLESS_KANA,
    ): String {
        if (text.all { it.code < 0x80 }) return if ('`' in text) text.lowercase().replace('`', '\'') else text.lowercase()
        // Before NFKC, which turns ´ into a space and a combining accent.
        val marks =
            if (text.none { it in Apostrophes || it in Marks }) {
                text
            } else {
                buildString(text.length) {
                    text.forEach { c ->
                        when {
                            c in Apostrophes -> append('\'')
                            c == '\u0130' -> append('i')
                            c == '\u2010' || c == '\u2011' -> append('-')
                            c in Marks -> Unit
                            else -> append(c)
                        }
                    }
                }
            }
        // NFKC splits Thai and Lao SARA AM into two characters no dictionary or title uses; put it back.
        val normalized =
            Normalizer
                .normalize(marks, Normalizer.Form.NFKC)
                .replace("\u0E4D\u0E32", "\u0E33")
                .replace("\u0ECD\u0EB2", "\u0EB3")
        val plain = if (normalized.any { it in SmallCaps }) normalized.map { SmallCaps[it] ?: it }.joinToString("") else normalized
        val lower = plain.lowercase()
        return if (han == HanFold.NONE) lower else simplified(lower, ignoreKana = han == HanFold.ALWAYS)
    }

    fun hasKana(text: String): Boolean = text.any(::isKana)

    /** Hant to Hans per character; unchanged when [text] has kana unless [ignoreKana]. */
    fun simplified(
        text: String,
        ignoreKana: Boolean = false,
    ): String {
        val table = HantToHans
        val first = text.indexOfFirst { it in table }
        if (first < 0 || (!ignoreKana && hasKana(text))) return text
        val out = StringBuilder(text.length).append(text, 0, first)
        for (i in first until text.length) out.append(table[text[i]] ?: text[i])
        return out.toString()
    }

    fun isKana(c: Char): Boolean = c in '\u3040'..'\u30FF' || c in '\u31F0'..'\u31FF' || c in '\uFF66'..'\uFF9F'

    // Every Han block starts at U+2E80; below it the script lookup is skipped.
    fun isHan(c: Char): Boolean = c.code >= 0x2E80 && Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    /**
     * The words of folded text; unspaced scripts are split by [breaker].
     */
    fun words(
        folded: String,
        breaker: WordBreaker = PlatformWordBreaker,
    ): List<String> = pieces(folded, breaker).map { it.text }

    /** [words] with adjacency. */
    fun pieces(
        folded: String,
        breaker: WordBreaker = PlatformWordBreaker,
    ): List<Piece> {
        val out = ArrayList<Piece>()
        folded.split(Whitespace).forEach { chunk ->
            if (chunk.any(::isUnspaced)) segment(chunk, breaker, out) else out += Piece(chunk, false)
        }
        return out
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

    private fun segment(
        chunk: String,
        breaker: WordBreaker,
        out: MutableList<Piece>,
    ) {
        var joined = false
        breaker.split(chunk).forEach { token ->
            if (token.any(Char::isLetterOrDigit)) {
                out += Piece(token, joined)
                joined = true
            } else {
                joined = false
            }
        }
    }
}
