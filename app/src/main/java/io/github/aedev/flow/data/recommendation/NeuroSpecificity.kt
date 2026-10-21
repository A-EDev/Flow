/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * How specific a key is in general language (#907): "pixel" or "snapdragon" name one subject,
 * while "better", "top", "normal" and "year" sit in titles of every subject. A viewer's own history
 * cannot tell them apart (its IDF only knows what this viewer watched), so the signal comes from
 * general word frequencies: `resources/neuro/freq/<lang>.txt`, made by `scripts/neuro/gen_wordfreq.py`
 * from OpenSubtitles counts, one "word zipf×10" line per word. The words are keyed here the way
 * titles are, so the table follows the key spec on its own.
 */
internal class NeuroSpecificity(
    private val keyOf: (String) -> String,
    languages: Collection<String>,
) {
    companion object {
        // Words at or below this Zipf frequency are fully specific; at or above HIGH_ZIPF, as generic as it gets.
        const val LOW_ZIPF = 4.0
        const val HIGH_ZIPF = 5.5
        const val MIN_SPECIFICITY = 0.05

        private val tables = ConcurrentHashMap<String, Map<String, Int>>()

        /** [language]'s words with their Zipf frequency ×10, or none when it has no table. Read once per process. */
        fun words(language: String): Map<String, Int> =
            tables.getOrPut(language) {
                val stream = NeuroSpecificity::class.java.getResourceAsStream("/neuro/freq/$language.txt") ?: return@getOrPut emptyMap()
                stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines
                        .mapNotNull { line ->
                            val space = line.lastIndexOf(' ')
                            if (space <=
                                0
                            ) {
                                null
                            } else {
                                NeuroText.fold(line.substring(0, space), NeuroLanguages.listFold(language)) to
                                    line.substring(space + 1).toInt()
                            }
                        }.toMap()
                }
            }
    }

    // A key many words lead to takes the most frequent of them: one common spelling makes it generic.
    private val zipfByKey: Map<String, Int> by lazy {
        HashMap<String, Int>().also { keys ->
            languages.distinct().forEach { language ->
                words(language).forEach { (word, zipf) ->
                    val key = keyOf(word)
                    if ((keys[key] ?: 0) < zipf) keys[key] = zipf
                }
            }
        }
    }

    /**
     * From [MIN_SPECIFICITY] for the commonest words to 1 for rare ones. A phrase takes the geometric
     * mean of its words: "galaxy s26" stays specific, "long term" stays generic.
     */
    fun of(key: String): Double {
        val words = NeuroScoring.stripDomainTag(key).split(' ')
        if (words.size == 1) return ofWord(words[0])
        return words.map(::ofWord).reduce(Double::times).pow(1.0 / words.size)
    }

    private fun ofWord(word: String): Double {
        val zipf = zipfByKey[word] ?: return 1.0
        return ((HIGH_ZIPF - zipf / 10.0) / (HIGH_ZIPF - LOW_ZIPF)).coerceIn(MIN_SPECIFICITY, 1.0)
    }
}
