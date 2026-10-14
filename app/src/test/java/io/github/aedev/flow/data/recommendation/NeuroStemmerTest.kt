/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.github.aedev.flow.data.recommendation.eval.RealTitleCorpus
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

/** Key spec 2, stage 8: stems by script and profile, never for the engine's own words (#907). */
class NeuroStemmerTest {
    private val tokenizer = NeuroTokenizer()

    private fun keys(
        profile: String,
        vararg words: String,
    ): List<String> {
        tokenizer.keyLanguage = profile
        return words.map(tokenizer::keyOf)
    }

    @Test
    fun `the unstemmed list holds every word the engine's lists compare keys with`() {
        val curated =
            (
                tokenizer.curatedWords + NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, tokenizer).curatedWords +
                    NeuroScoring.CURATED_WORDS
            ).flatMap { NeuroText.fold(it).split(' ') }
                .map(tokenizer::normalizeLemma)
                .filter { word -> word.isNotEmpty() && word.all { it.isLetter() } }
                .toSortedSet()
        val file = File("src/main/resources/neuro/keys/unstemmed.txt")
        if (System.getenv("NEURO_WRITE_UNSTEMMED") == "1") {
            file.parentFile.mkdirs()
            file.writeText(
                "# Words key spec 2 never stems: the engine's own word lists are written in them (NeuroStemmer).\n" +
                    "# Written by NeuroStemmerTest with NEURO_WRITE_UNSTEMMED=1. Flow Desktop reads the same file.\n" +
                    curated.joinToString("\n", postfix = "\n"),
            )
        }
        val listed = file.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.toSet()

        assertWithMessage("run the test with NEURO_WRITE_UNSTEMMED=1").that(curated - listed).isEmpty()
    }

    @Test
    fun `words share a key with their other forms in the viewer's language`() {
        assertThat(keys("en", "guitars", "guitar", "kayaks", "kayaking")).containsExactly("guitar", "guitar", "kayak", "kayak").inOrder()
        assertThat(keys("es", "croquetas", "croqueta")).containsExactly("croquet", "croquet")
        assertThat(keys("fr", "recettes", "recette")).containsExactly("recet", "recet")
        assertThat(keys("de", "rezepte", "rezept")).containsExactly("rezept", "rezept")
        assertThat(keys("ru", "рецепты", "рецептов", "рецепт")).containsExactly("рецепт", "рецепт", "рецепт")
    }

    @Test
    fun `the engine's own words and words with digits stay as written`() {
        assertThat(keys("en", "anime", "animation", "technology", "lyrics", "ps5", "4k", "node.js"))
            .containsExactly(
                "anime",
                "animation",
                "technology",
                "lyrics",
                "ps5",
                "4k",
                "node.js",
            ).inOrder()
    }

    @Test
    fun `the script and the profile choose the stemmer, never the title`() {
        assertThat(keys("tr", "oyunları", "guitars")).containsExactly("oyunları", "guitars").inOrder()
        assertThat(keys("ru", "guitars", "рецепты")).containsExactly("guitar", "рецепт").inOrder()
        assertThat(keys("uk", "рецепти")).containsExactly("рецепти")
        assertThat(keys("ja", "ギター")).containsExactly("ギター")
    }

    @Test
    fun `a key is always its own key`() {
        val words =
            (RealTitleCorpus.titles + NeuroLearningBenchmark.pool().map { it.title } + languageTitles())
                .flatMap { NeuroText.words(NeuroText.fold(it)) }
                .map { piece -> piece.trim { !it.isLetter() } }
                .filter { it.length >= 3 }
                .toSet()
        listOf("en", "es", "pt", "fr", "de", "it", "ru").forEach { profile ->
            tokenizer.keyLanguage = profile
            val changed = words.filter { word -> tokenizer.keyOf(word).let { tokenizer.keyOf(it) != it } }
            assertWithMessage(profile).that(changed).isEmpty()
        }
    }

    @Test
    fun `the lemma map never rewrites its own lemmas`() {
        val lemmas = tokenizer.curatedWords.map(tokenizer::normalizeLemma)
        assertThat(lemmas.filter { tokenizer.normalizeLemma(it) != it }).isEmpty()
    }

    @Test
    fun `a stem that stems again leaves the word as written`() {
        assertThat(keys("es", "casero", "casa")).containsExactly("casero", "cas").inOrder()
    }

    @Test
    fun `stemming on many threads gives the same keys as on one`() {
        val words = languageTitles().flatMap { it.lowercase().split(' ') }.filter { word -> word.length >= 3 && word.all { it.isLetter() } }
        val serial = words.map { NeuroStemmer.key(it, "es") }
        val pool = Executors.newFixedThreadPool(8)
        val parallel = List(8) { pool.submit<List<String>> { words.map { NeuroStemmer.key(it, "es") } } }.map { it.get() }
        pool.shutdown()

        parallel.forEach { assertThat(it).isEqualTo(serial) }
    }

    private fun languageTitles(): List<String> =
        javaClass
            .getResourceAsStream("/neuro/language_titles.tsv")!!
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringAfter('\t') }
}
