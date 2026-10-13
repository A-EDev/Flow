/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

/**
 * Stages 2 to 6 of [NeuroKeySpec]: folded text into chunks of words that belong together. A title
 * pair is only a candidate when nothing separates its words, so "Big Tech's Privacy Promises Are
 * Lies" never yields "promises lies" (#907).
 */
internal class NeuroChunker(
    private val isStopWord: (String) -> Boolean,
    private val normalizeLemma: (String) -> String,
) {
    /** A run of [words] inside one chunk, from word [from] up to but not including [to]. */
    data class Run(
        val key: String,
        val chunk: Int,
        val from: Int,
        val to: Int,
    )

    data class Chunks(
        val chunks: List<List<String>>,
        val modelPairs: List<String>,
    ) {
        /** Every word in reading order, [NeuroKeySpec.NEVER_ALONE] words included. */
        val words: List<String> get() = chunks.flatten()

        /** Every run of two or three words inside one chunk. */
        fun runs(): List<Run> =
            buildList {
                chunks.forEachIndexed { index, chunk ->
                    for (from in chunk.indices) {
                        var words = wordCount(chunk[from])
                        for (to in from + 2..chunk.size) {
                            words += wordCount(chunk[to - 1])
                            if (words > NeuroKeySpec.MAX_RUN_WORDS) break
                            val key = chunk.subList(from, to).joinToString(" ")
                            if (key.length <= NeuroKeySpec.MAX_KEY_LENGTH) add(Run(key, index, from, to))
                        }
                    }
                }
            }
    }

    private sealed interface Item {
        data class Word(
            val text: String,
            val whole: Boolean = false,
        ) : Item

        data class Number(
            val text: String,
        ) : Item

        data object Break : Item
    }

    fun chunk(text: String): Chunks {
        val items = ArrayList<Item>()
        NeuroText.words(NeuroText.fold(text)).forEach { piece -> split(piece, items) }
        val merged = mergeExpressions(items)
        val chunks = ArrayList<List<String>>()
        val modelPairs = ArrayList<String>()
        var current = ArrayList<String>()

        fun close() {
            if (current.isNotEmpty()) chunks += current
            current = ArrayList()
        }
        merged.forEach { item ->
            when (val word = classify(item)) {
                is Item.Word -> {
                    current += word.text
                }

                is Item.Number -> {
                    current.lastOrNull()?.let { previous -> modelPairs += "$previous ${word.text}" }
                    close()
                }

                Item.Break -> {
                    close()
                }
            }
        }
        close()
        return Chunks(chunks, modelPairs)
    }

    // Stage 2 and 4: one whitespace-separated piece into words and breaks.
    private fun split(
        piece: String,
        out: MutableList<Item>,
    ) {
        // A lone "&" stays a word until expressions are read, so "drum & bass" can still match one.
        if (piece == "&") {
            out += Item.Word(piece)
            return
        }
        val word = StringBuilder()

        fun flush() {
            if (word.isNotEmpty()) shape(word.toString(), out)
            word.setLength(0)
        }
        var i = 0
        while (i < piece.length) {
            val c = piece[i]
            val next = piece.getOrNull(i + 1)
            when {
                NeuroText.isWordChar(c) -> {
                    word.append(c)
                }

                c == '\'' -> {
                    if (word.isNotEmpty()) word.append(c)
                }

                c in JOINERS && word.isNotEmpty() && next != null && NeuroText.isWordChar(next) -> {
                    word.append(c)
                }

                // The last dot of an abbreviation ("a.i.") is part of it, not the end of a sentence.
                c == '.' && next == null && '.' in word -> {
                    Unit
                }

                c in KEEP_SUFFIX && word.isNotEmpty() && keepsWhole(word, piece, i) -> {
                    word.append(c)
                }

                else -> {
                    flush()
                    out += Item.Break
                }
            }
            i++
        }
        flush()
    }

    private fun keepsWhole(
        word: StringBuilder,
        piece: String,
        at: Int,
    ): Boolean {
        var end = at
        while (end < piece.length && piece[end] in KEEP_SUFFIX) end++
        return word.toString() + piece.substring(at, end) in NeuroKeySpec.KEEP_WHOLE
    }

    private fun shape(
        raw: String,
        out: MutableList<Item>,
    ) {
        if (raw in NeuroKeySpec.KEEP_WHOLE) {
            out += Item.Word(raw, whole = true)
            return
        }
        NeuroKeySpec.EXPRESSIONS[raw]?.let {
            out += Item.Word(it, whole = true)
            return
        }
        if (raw in NeuroKeySpec.CONTRACTIONS) {
            out += Item.Break
            return
        }
        var word = NeuroKeySpec.ELISIONS.firstOrNull { raw.startsWith(it) && raw.length > it.length }?.let { raw.removePrefix(it) } ?: raw
        word =
            when {
                word.endsWith("'s") -> word.dropLast(2)
                word.endsWith("'") -> word.dropLast(1)
                else -> word
            }.replace("'", "")
        NeuroKeySpec.EXPRESSIONS[word]?.let {
            out += Item.Word(it, whole = true)
            return
        }
        if ('-' in word) {
            val parts = word.split('-').filter { it.isNotEmpty() }
            if (parts.any { it.length <= 2 }) {
                out += Item.Word(parts.joinToString(""))
            } else {
                parts.forEach { out += Item.Word(it) }
            }
            return
        }
        if ('.' in word && !word.all { it.isDigit() || it == '.' }) {
            word.split('.').filter { it.isNotEmpty() }.forEach { out += Item.Word(it) }
            return
        }
        out += Item.Word(word)
    }

    // Stage 3, for expressions written as separate words ("lo fi", "drum n bass").
    private fun mergeExpressions(items: List<Item>): List<Item> {
        if (items.count { it is Item.Word } < 2) return items
        val out = ArrayList<Item>(items.size)
        var i = 0
        while (i < items.size) {
            val head = items[i]
            val match =
                if (head !is Item.Word || head.text !in NeuroKeySpec.EXPRESSION_FIRST_WORDS) {
                    null
                } else {
                    (NeuroKeySpec.LONGEST_EXPRESSION_WORDS downTo 2).firstNotNullOfOrNull { length ->
                        val window = items.subList(i, minOf(items.size, i + length))
                        if (window.size < length || window.any { it !is Item.Word || it.whole }) return@firstNotNullOfOrNull null
                        NeuroKeySpec.EXPRESSIONS[window.joinToString(" ") { (it as Item.Word).text }]?.let { it to length }
                    }
                }
            if (match != null) {
                out += Item.Word(match.first, whole = true)
                i += match.second
            } else {
                out += items[i]
                i++
            }
        }
        return out
    }

    // Stage 5: what ends a chunk.
    private fun classify(item: Item): Item {
        if (item !is Item.Word || item.whole) return item
        val text = item.text
        if (text.isEmpty()) return Item.Break
        if (text.all { it.isDigit() || it == '.' }) return if (YEAR.matches(text)) Item.Break else Item.Number(text)
        if (!NeuroText.isTopicSized(text)) return Item.Break
        val lemma = normalizeLemma(text)
        if (isStopWord(text) || isStopWord(lemma)) return Item.Break
        return Item.Word(lemma)
    }

    private companion object {
        val JOINERS = setOf('-', '.', '&')
        val KEEP_SUFFIX = setOf('+', '#')
        val YEAR = Regex("^(19|20)\\d\\d$")

        fun wordCount(word: String) = word.count { it == ' ' } + 1
    }
}
