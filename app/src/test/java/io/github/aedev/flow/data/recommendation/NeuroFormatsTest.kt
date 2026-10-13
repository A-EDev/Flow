/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** A format says what kind of video it is, never what it is about (#907). */
class NeuroFormatsTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun `a format word is a format, never a key`() {
        val video = Video("v", "Google Pixel 10 Pro Review", "", "", "", 900, 1L, uploadDate = "", tags = listOf("guitar playalong"))

        val vector = tokenizer.extractFeatures(video, IdfSnapshot(emptyMap(), 0))

        assertThat(vector.formats).containsExactly("review", "playalong")
        assertThat(vector.topics.keys).containsNoneOf("review", "playalong", "guitar playalong")
    }

    @Test
    fun `watching a review teaches the format, and other formats fade`() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine(UserBrain(formatAffinity = mapOf("tutorial" to 0.5)))
            engine.onVideoInteraction(
                Video("r", "Google Pixel 10 Pro Review", "MKBHD", "ch", "", 900, 1L, uploadDate = ""),
                InteractionType.WATCHED,
                0.9f,
            )
            val affinity = engine.getBrainSnapshot().formatAffinity
            engine.shutdown()

            assertThat(affinity.getValue("review")).isGreaterThan(0.0)
            assertThat(affinity.getValue("tutorial")).isLessThan(0.5)
        }

    @Test
    fun `a format the viewer takes to lifts a video in that format`() =
        runBlocking<Unit> {
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = mapOf("guitar" to 0.5)),
                    formatAffinity = mapOf("tutorial" to 0.9),
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER)
            val tutorial = Video("t", "Guitar tutorial", "Guitar Lessons", "ch-a", "", 600, 1L, uploadDate = "3 days ago")
            val other = Video("o", "Guitar", "Guitar Lessons B", "ch-b", "", 600, 1L, uploadDate = "3 days ago")

            val ranked = engine.rank(listOf(other, tutorial), emptySet())
            engine.shutdown()

            assertThat(ranked.first().id).isEqualTo("t")
        }

    @Test
    fun `a liked format only nudges a score`() {
        val affinity = NeuroFormats.learn(emptyMap(), setOf("review"))
        val saturated = (1..100).fold(affinity) { current, _ -> NeuroFormats.learn(current, setOf("review")) }

        assertThat(NeuroFormats.scoreTerm(saturated, setOf("review"))).isAtMost(NeuroFormats.MAX_SCORE_TERM)
        assertThat(NeuroFormats.scoreTerm(saturated, setOf("vlog"))).isEqualTo(0.0)
    }
}
