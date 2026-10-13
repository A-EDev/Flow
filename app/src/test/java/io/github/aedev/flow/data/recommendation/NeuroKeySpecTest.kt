/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.data.model.Video
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/** Key spec 1 ([NeuroKeySpec]): the shared golden cases, and the rules for runs. */
class NeuroKeySpecTest {
    private val tokenizer = NeuroTokenizer()

    private fun JsonArray.strings() = map { it.jsonPrimitive.content }

    @Test
    fun `every golden case reads as the spec says`() {
        val golden = Json.parseToJsonElement(File("src/test/resources/neuro/key_spec_golden.json").readText()).jsonObject
        assertThat(golden.getValue("keySpec").jsonPrimitive.content).isEqualTo(NeuroKeySpec.VERSION.toString())
        val cases = golden.getValue("cases").jsonArray.map { it.jsonObject }
        assertThat(cases.size).isAtLeast(60)

        cases.forEach { case ->
            val input = case.getValue("input").jsonPrimitive.content
            val chunks = tokenizer.chunk(input)
            assertWithMessage(input).that(chunks.chunks).isEqualTo(case.getValue("chunks").jsonArray.map { it.jsonArray.strings() })
            assertWithMessage(input).that(chunks.modelPairs).isEqualTo(case.getValue("modelPairs").jsonArray.strings())
            assertWithMessage(input).that(chunks.formats.toList()).isEqualTo(case.getValue("formats").jsonArray.strings())
        }
    }

    @Test
    fun `runs stay inside a chunk and hold at most three words`() {
        val runs = tokenizer.chunk("Lo-Fi Hip Hop Radio: chill study beats").runs().map { it.key }

        assertThat(runs).containsExactly("lofi hip hop", "hip hop radio", "chill study", "chill study beats", "study beats")
    }

    @Test
    fun `every spelling of an apostrophe reads the same`() {
        val spellings = listOf("Tech's", "Tech’s", "Tech‘s", "Techʼs", "Tech`s", "Tech´s").map { tokenizer.tokenize(it) }

        assertThat(spellings.distinct()).containsExactly(listOf("tech"))
    }

    @Test
    fun `a pair of words only becomes a key once it is known to be a phrase`() {
        val idf = IdfSnapshot(emptyMap(), 0)
        val video = Video("v", "Why Big Tech's Privacy Promises Are Lies", "", "", "", 600, 1L, uploadDate = "")

        val keys = tokenizer.extractFeatures(video, idf).topics.keys

        assertThat(keys).containsExactly("big", "tech", "privacy", "promises", "lies")
        assertThat(tokenizer.documentTerms(video, tokenizer.extractFeatures(video, idf)))
            .containsAtLeast("big tech", "tech privacy", "privacy promises", "big tech privacy", "tech privacy promises")
    }
}
