/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlinx.serialization.Serializable
import java.text.Normalizer

/** How titles write a key ("MMA", "Machine Learning"), and how sure that spelling is. */
@Serializable
data class DisplayForm(
    val form: String,
    val votes: Int = 1,
)

/**
 * Keys are folded and lemmatized for matching ("machine learn"); people read them as titles write
 * them. Each key keeps the spelling titles use most, for every label and every search text (#907).
 * Only keys written differently from the key itself are stored.
 */
internal object NeuroDisplayForms {
    const val MAX_ENTRIES = 2_000
    private const val MAX_VOTES = 20

    private val Whitespace = Regex("\\s+")

    /** The spelling of each of [keys] in [text], where [text] holds it. */
    fun spellingsIn(
        text: String,
        keys: Collection<String>,
        tokenizer: NeuroTokenizer,
    ): Map<String, String> {
        val wanted = keys.mapTo(HashSet(), NeuroScoring::stripDomainTag)
        if (wanted.isEmpty() || text.isBlank()) return emptyMap()
        val pieces =
            Normalizer
                .normalize(text, Normalizer.Form.NFKC)
                .split(Whitespace)
                .map { piece -> piece.trim { !NeuroText.isWordChar(it) && it != '+' && it != '#' }.removeSuffix("'s").removeSuffix("’s") }
        val words = ArrayList<String>()
        val pieceOf = ArrayList<Int>()
        pieces.forEachIndexed { index, piece ->
            tokenizer.tokenize(piece).forEach {
                words += it
                pieceOf += index
            }
        }
        val found = HashMap<String, String>()
        for (from in words.indices) {
            for (to in from + 1..minOf(words.size, from + NeuroKeySpec.MAX_RUN_WORDS)) {
                // Only words that stand next to each other in the title spell a key together.
                if (to - from > 1 && pieceOf[to - 1] - pieceOf[to - 2] > 1) break
                val key = words.subList(from, to).joinToString(" ")
                if (key in wanted && key !in found) {
                    val spelling = pieces.subList(pieceOf[from], pieceOf[to - 1] + 1).joinToString(" ")
                    if (spelling != key) found[key] = spelling
                }
            }
        }
        return found
    }

    /** [forms] after one more title wrote [spellings]: a spelling seen often enough replaces another. */
    fun record(
        forms: Map<String, DisplayForm>,
        spellings: Map<String, String>,
        keep: (String) -> Boolean,
    ): Map<String, DisplayForm> {
        if (spellings.isEmpty()) return forms
        val updated = LinkedHashMap(forms)
        spellings.forEach { (key, spelling) ->
            val current = updated[key]
            updated[key] =
                when {
                    current == null -> DisplayForm(spelling)
                    current.form == spelling -> current.copy(votes = (current.votes + 1).coerceAtMost(MAX_VOTES))
                    current.votes <= 1 -> DisplayForm(spelling)
                    else -> current.copy(votes = current.votes - 1)
                }
        }
        if (updated.size <= MAX_ENTRIES) return updated
        return updated.entries
            .sortedWith(compareByDescending<Map.Entry<String, DisplayForm>> { keep(it.key) }.thenByDescending { it.value.votes })
            .take(MAX_ENTRIES)
            .associate { it.key to it.value }
    }

    /** [key] as a label: its spelling in titles, else each word with a capital ("Claude 5.5"). */
    fun label(
        forms: Map<String, DisplayForm>,
        key: String,
    ): String {
        val base = NeuroScoring.stripDomainTag(key)
        return forms[base]?.form ?: capitalized(base)
    }

    /** [text] with each word capitalized, unless it already holds a capital ("iPhone" stays). */
    fun capitalized(text: String): String =
        if (text.any(Char::isUpperCase)) text else text.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    /**
     * [forms] given spellings from text that is not a title (tags, a channel name, a typed search):
     * they fill only keys no title has spelled yet, so "google pixel" never outvotes "Google Pixel".
     */
    fun fillGaps(
        forms: Map<String, DisplayForm>,
        spellings: Map<String, String>,
        keep: (String) -> Boolean,
    ): Map<String, DisplayForm> = record(forms, spellings.filterKeys { it !in forms }.mapValues { capitalized(it.value) }, keep)

    /** [query] with every key it holds written as titles write it ("machine learn" reads "machine learning"). */
    fun searchText(
        forms: Map<String, DisplayForm>,
        query: String,
    ): String {
        if (forms.isEmpty()) return query
        val words = query.split(' ')
        val out = ArrayList<String>(words.size)
        var i = 0
        while (i < words.size) {
            val match =
                (minOf(NeuroKeySpec.MAX_RUN_WORDS, words.size - i) downTo 1).firstNotNullOfOrNull { length ->
                    forms[words.subList(i, i + length).joinToString(" ")]?.let { it.form to length }
                }
            if (match != null) {
                out += match.first
                i += match.second
            } else {
                out += words[i]
                i++
            }
        }
        return out.joinToString(" ")
    }
}
