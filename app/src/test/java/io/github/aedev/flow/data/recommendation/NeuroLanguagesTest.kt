/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Title language detection and the stop lists it picks (#907). */
class NeuroLanguagesTest {
    private val labelled: List<Pair<String, String>> =
        javaClass
            .getResourceAsStream("/neuro/language_titles.tsv")!!
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.substringBefore('\t') to it.substringAfter('\t') }

    private fun detect(title: String) = NeuroLanguages.detect(NeuroText.words(NeuroText.fold(title)))

    @Test
    fun `a confident detection is right at least 98 times in 100`() {
        val results = labelled.map { (language, title) -> Triple(language, title, detect(title)) }
        val confident = results.filter { it.third != null }
        val wrong = confident.filter { it.first != it.third }
        results.groupBy { it.first }.forEach { (language, rows) ->
            println("$language: ${rows.count { it.third == language }} of ${rows.size} detected")
        }
        wrong.forEach { println("wrong: ${it.first} read as ${it.third}: ${it.second}") }

        assertThat(1.0 - wrong.size.toDouble() / confident.size).isAtLeast(0.98)
        assertThat(confident.size.toDouble() / results.size).isAtLeast(0.6)
    }

    @Test
    fun `scripts one language uses decide alone`() {
        assertThat(detect("Обзор нового iPhone 17")).isEqualTo("ru")
        assertThat(detect("Огляд iPhone 17: чи варто купувати")).isEqualTo("uk")
        assertThat(detect("Преглед новог иПхоне ђак")).isNull()
        assertThat(detect("بررسی آیفون")).isEqualTo("fa")
        assertThat(detect("مراجعة آيفون")).isEqualTo("ar")
        assertThat(detect("iPhone 17 レビュー")).isEqualTo("ja")
    }

    @Test
    fun `a title with too few function words has no language`() {
        assertThat(detect("Minecraft speedrun world record")).isNull()
        assertThat(detect("de la")).isNull()
    }

    @Test
    fun `every stop list loads without its header`() {
        NeuroLanguages.STOP_LIST_LANGUAGES.forEach { language ->
            val list = NeuroLanguages.stopList(language)
            assertWithMessage(language).that(list).isNotEmpty()
            assertWithMessage(language).that(list.none { it.startsWith("#") || ' ' in it }).isTrue()
        }
        assertThat(NeuroLanguages.stopList("es")).containsAtLeast("los", "para", "como")
        assertThat(NeuroLanguages.stopList("fr")).containsAtLeast("des", "les")
        assertThat(NeuroLanguages.stopList("ru")).contains("как")
        assertThat(NeuroLanguages.stopList("xx")).isEmpty()
    }

    @Test
    fun `the profile moves only when most of a window disagrees`() {
        var state = "en" to emptyMap<String, Int>()
        repeat(NeuroLanguages.PROFILE_WINDOW - 1) { state = NeuroLanguages.vote(state.first, state.second, "es") }
        assertThat(state.first).isEqualTo("en")

        state = NeuroLanguages.vote(state.first, state.second, "es")
        assertThat(state).isEqualTo("es" to emptyMap<String, Int>())

        var mixed = "en" to emptyMap<String, Int>()
        repeat(NeuroLanguages.PROFILE_WINDOW) { i ->
            mixed =
                NeuroLanguages.vote(
                    mixed.first,
                    mixed.second,
                    if (i % 2 ==
                        0
                    ) {
                        "es"
                    } else {
                        "en"
                    },
                )
        }
        assertThat(mixed.first).isEqualTo("en")
        assertThat(NeuroLanguages.vote("en", emptyMap(), null)).isEqualTo("en" to emptyMap<String, Int>())
    }

    @Test
    fun `language codes read as the stop lists name them`() {
        assertThat(NeuroLanguages.normalize("in-ID")).isEqualTo("id")
        assertThat(NeuroLanguages.normalize("pt_BR")).isEqualTo("pt")
        assertThat(NeuroLanguages.normalize("zh-Hant-TW")).isEqualTo("zh")
    }
}
