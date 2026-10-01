/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/**
 * Title learning end to end: real tokenizer features, the engine's learning step, with and without
 * [NeuroVectorMath.phraseFirst]. Prints the learnt weights so a change reads as numbers (#907).
 */
class NeuroPhraseLearningTest {
    @Test
    fun `a pair that keeps appearing together becomes a phrase and a chance pair does not`() {
        val idf =
            IdfSnapshot(
                wordFrequency =
                    mapOf(
                        "guitar playalong" to 6,
                        "guitar" to 8,
                        "playalong" to 6,
                        "review after" to 3,
                        "review" to 30,
                        "after" to 25,
                    ),
                totalDocs = 100,
            )

        assertThat(NeuroPhrases.isPhrase("guitar playalong", idf)).isTrue()
        assertThat(NeuroPhrases.isPhrase("review after", idf)).isFalse()
        assertThat(NeuroPhrases.isPhrase("guitar playalong", idf.copy(totalDocs = 10))).isFalse()
    }

    @Test
    fun `a multi-word tag stays a phrase`() {
        val video =
            Video(
                id = "t",
                title = "Wonderwall with tabs",
                channelName = "",
                channelId = "",
                thumbnailUrl = "",
                duration = 300,
                viewCount = 0,
                uploadDate = "",
                tags = listOf("guitar playalong", "rock"),
                description = "Play along with me #guitar_tabs",
            )

        val topics = tokenizer.extractFeatures(video, IdfSnapshot(emptyMap(), 0)).topics
        assertThat(topics.keys).containsAtLeast("guitar playalong", "guitar tabs")
        assertThat(topics.getValue("guitar playalong")).isGreaterThan(topics.getValue("playalong"))
    }

    private val tokenizer = NeuroTokenizer()

    private val reviews =
        listOf(
            "Google Pixel 10 Pro Review",
            "Google Pixel 10 Pro camera test",
            "Google Pixel 10 Pro battery life",
            "Samsung Galaxy S26 Ultra review",
            "Samsung Galaxy S26 Ultra vs Pixel",
            "Google Pixel 10 Pro one month later",
            "Google Pixel 10 Pro gaming test",
            "Google Pixel 10 Pro vs iPhone 17",
            "Google Pixel Watch 4 review",
            "Google Pixel 10 Pro charging speed",
            "Google Pixel Buds Pro 3 review",
        )
    private val guitar =
        listOf(
            "Easy guitar chords for beginners",
            "Fingerstyle guitar lesson",
            "Acoustic guitar cover of a classic",
            "Guitar scales every player should know",
            "Electric guitar tone tips",
            "Blues guitar solo lesson",
        )

    private fun learn(
        titles: List<String>,
        phraseFirst: Boolean,
    ): Map<String, Double> {
        var global = ContentVector()
        // Document counts grow with every learned video, as the engine's IDF table does, on top of
        // a history of other viewing.
        val counts = HashMap<String, Int>()
        var documents = 40
        titles.forEachIndexed { index, title ->
            val video =
                Video(
                    id = "v$index",
                    title = title,
                    channelName = "",
                    channelId = "",
                    thumbnailUrl = "",
                    duration = 900,
                    viewCount = 0,
                    uploadDate = "",
                )
            val features = tokenizer.extractFeatures(video, IdfSnapshot(counts.toMap(), documents))
            val target = if (phraseFirst) NeuroVectorMath.phraseFirst(features) else features
            global = NeuroVectorMath.adjustVector(global, target, 0.15)
            features.topics.keys.forEach { counts.merge(it, 1, Int::plus) }
            documents++
        }
        return global.topics
    }

    @Test
    fun `a watched review teaches the product phrase over its single words`() {
        val after = learn(reviews, phraseFirst = true)
        println("PHRASE after : google=${after["google"]} pixel=${after["pixel"]} google pixel=${after["google pixel"]}")

        assertThat(after.getValue("google pixel")).isGreaterThan(after.getValue("google"))
        assertThat(after.getValue("google pixel")).isGreaterThan(after.getValue("pixel"))
    }

    @Test
    fun `a word that recurs across many phrases still becomes the strongest interest`() {
        val after = learn(guitar, phraseFirst = true)
        println("PHRASE guitar top: ${after.entries.sortedByDescending { it.value }.take(5)}")

        assertThat(after.entries.maxBy { it.value }.key).isEqualTo("guitar")
    }
}
