/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.data.recommendation.NeuroDisplayForms
import io.github.aedev.flow.data.recommendation.NeuroText
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import org.junit.Test

/**
 * The gate key spec 2 ships through (#907): one spelling, one key. Over English, Spanish, Russian and
 * Arabic titles read by one viewer, at most 1 % of spellings may end up under more than one key.
 */
class KeySpecGateTest {
    private val titles: List<String> =
        RealTitleCorpus.titles +
            javaClass
                .getResourceAsStream("/neuro/language_titles.tsv")!!
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .filter { it.substringBefore('\t') in setOf("en", "es", "ru", "ar") }
                .map { it.substringAfter('\t') }

    @Test
    fun `a spelling has one key for one viewer`() {
        listOf("en", "es", "ru").forEach { profile ->
            val tokenizer =
                NeuroTokenizer().also {
                    it.languageProfile = profile
                    it.keyLanguage = profile
                }
            val keysBySpelling = HashMap<String, MutableSet<String>>()
            titles.forEach { title ->
                val keys = tokenizer.chunk(title).words.filter { ' ' !in it }
                NeuroDisplayForms.spellingsIn(title, keys, tokenizer).forEach { (key, spelling) ->
                    // "steam-cooled" holds two words, so two keys; only a single word counts.
                    val word = NeuroText.fold(spelling)
                    if (word.all(NeuroText::isWordChar)) keysBySpelling.getOrPut(word) { HashSet() } += key
                }
            }
            val split = keysBySpelling.filterValues { it.size > 1 }
            val share = split.size.toDouble() / keysBySpelling.size.coerceAtLeast(1)
            println(
                "profile $profile: ${keysBySpelling.size} spellings, ${split.size} with two keys (${"%.2f".format(100 * share)} %) $split",
            )

            assertWithMessage(profile).that(share).isAtMost(0.01)
        }
    }
}
