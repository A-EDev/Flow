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
    private val tokenizer = NeuroTokenizer()
    private val idf = IdfSnapshot(emptyMap(), 0)

    private val reviews =
        listOf(
            "Google Pixel 10 Pro Review",
            "Google Pixel 10 Pro camera test",
            "Google Pixel 10 Pro battery life",
            "Samsung Galaxy S26 Ultra review",
            "Samsung Galaxy S26 Ultra vs Pixel",
            "Google Pixel 10 Pro one month later",
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
            val features = tokenizer.extractFeatures(video, idf)
            val target = if (phraseFirst) NeuroVectorMath.phraseFirst(features) else features
            global = NeuroVectorMath.adjustVector(global, target, 0.15)
        }
        return global.topics
    }

    @Test
    fun `a watched review teaches the product phrase over its single words`() {
        val before = learn(reviews, phraseFirst = false)
        val after = learn(reviews, phraseFirst = true)
        println("PHRASE before: google=${before["google"]} pixel=${before["pixel"]} google pixel=${before["google pixel"]}")
        println("PHRASE after : google=${after["google"]} pixel=${after["pixel"]} google pixel=${after["google pixel"]}")

        assertThat(after.getValue("google pixel")).isGreaterThan(after.getValue("google"))
        assertThat(after.getValue("google pixel")).isGreaterThan(after.getValue("pixel"))
        assertThat(after.getValue("google") / after.getValue("google pixel"))
            .isLessThan(before.getValue("google") / before.getValue("google pixel"))
    }

    @Test
    fun `a word that recurs across many phrases still becomes the strongest interest`() {
        val after = learn(guitar, phraseFirst = true)
        println("PHRASE guitar top: ${after.entries.sortedByDescending { it.value }.take(5)}")

        assertThat(after.entries.maxBy { it.value }.key).isEqualTo("guitar")
    }
}
