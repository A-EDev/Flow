/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** #907: what the viewer rejects stays rejected, and rejecting does not make the feed random. */
class NeuroRejectionTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L
    private val phonk = ContentVector(topics = mapOf("phonk" to 0.8, "drift phonk" to 0.5))

    @Test
    fun `a rejection halves every month instead of expiring after two weeks`() {
        val signal = RejectionSignal(count = 3, lastRejectedAt = now - 30 * day)
        val pair = NeuroScoring.makeAffinityKey("phonk", "drift phonk")

        assertThat(NeuroScoring.effectiveRejections(signal, now)).isWithin(1e-9).of(1.5)
        assertThat(NeuroScoring.calculateRejectionPatternPenalty(phonk, mapOf(pair to signal), now))
            .isEqualTo(NeuroScoring.REJECTION_PENALTY_2)
        assertThat(
            NeuroScoring.calculateRejectionPatternPenalty(phonk, mapOf(pair to signal.copy(lastRejectedAt = now - 60 * day)), now),
        ).isEqualTo(NeuroScoring.REJECTION_PENALTY_1)
    }

    @Test
    fun `a new rejection adds to what is left of the old ones`() {
        val patterns = mapOf("~phonk" to RejectionSignal(count = 2, lastRejectedAt = now - 30 * day))

        val updated = NeuroScoring.recordRejection(patterns, phonk, now)

        assertThat(updated.getValue("~phonk")).isEqualTo(RejectionSignal(count = 2, lastRejectedAt = now))
    }

    @Test
    fun `a word two rejections share catches the phrases it appears in`() {
        val once = NeuroScoring.recordRejection(emptyMap(), ContentVector(topics = mapOf("phonk" to 0.9, "night drive" to 0.5)), now)
        val twice = NeuroScoring.recordRejection(once, ContentVector(topics = mapOf("gym" to 0.9, "phonk" to 0.8)), now)
        val styled = ContentVector(topics = mapOf("phonk mix" to 0.7, "mix aggressive" to 0.6, "aggressive" to 0.3))

        assertThat(NeuroScoring.calculateRejectionPatternPenalty(styled, once, now)).isEqualTo(1.0)
        assertThat(NeuroScoring.calculateRejectionPatternPenalty(styled, twice, now)).isEqualTo(NeuroScoring.REJECTION_PENALTY_2)
    }

    @Test
    fun `one rejected phrase does not penalize its generic words until a second rejection repeats them`() {
        val rejected = ContentVector(topics = mapOf("phonk driving" to 0.8, "driving night" to 0.6))
        val roadTrip = ContentVector(topics = mapOf("driving" to 0.8, "road trip" to 0.6))
        val once = NeuroScoring.recordRejection(emptyMap(), rejected, now)
        val twice = NeuroScoring.recordRejection(once, rejected, now)

        assertThat(NeuroScoring.calculateRejectionPatternPenalty(roadTrip, once, now)).isEqualTo(1.0)
        assertThat(NeuroScoring.calculateRejectionPatternPenalty(roadTrip, twice, now)).isEqualTo(NeuroScoring.REJECTION_PENALTY_2)
    }

    @Test
    fun `one rejection of a video about a real interest does not penalize that interest`() {
        val giveaway = ContentVector(topics = mapOf("code months" to 0.8, "opus 5.5" to 0.7))
        val opusReview = ContentVector(topics = mapOf("opus 5.5" to 0.9, "benchmark" to 0.4))
        val once = NeuroScoring.recordRejection(emptyMap(), giveaway, now)
        val twice = NeuroScoring.recordRejection(once, giveaway, now)

        assertThat(NeuroScoring.calculateRejectionPatternPenalty(opusReview, once, now)).isEqualTo(1.0)
        assertThat(NeuroScoring.calculateRejectionPatternPenalty(opusReview, twice, now)).isEqualTo(NeuroScoring.REJECTION_PENALTY_2)
    }

    @Test
    fun `faded rejections are forgotten`() {
        val patterns = mapOf("gaming" to RejectionSignal(count = 1, lastRejectedAt = now - 45 * day))

        assertThat(NeuroScoring.recordRejection(patterns, phonk, now)).doesNotContainKey("gaming")
    }

    @Test
    fun `a thumbs-down is remembered and does not raise boredom`() =
        runTest {
            val engine = NeuroLearningBenchmark.engine()
            engine.onVideoInteraction(NeuroLearningBenchmark.phonk(5), InteractionType.DISLIKED)
            engine.onVideoInteraction(NeuroLearningBenchmark.phonk(7), InteractionType.DISLIKED)

            val brain = engine.getBrainSnapshot()
            assertThat(brain.rejectionPatterns.getValue("~phonk").count).isEqualTo(2)
            assertThat(brain.consecutiveSkips).isEqualTo(0)
            assertThat(brain.channelScores.getValue(NeuroLearningBenchmark.phonk(5).channelId)).isWithin(1e-9).of(0.25)
            engine.shutdown()
        }

    @Test
    fun `a thumbs-down on a styled title is remembered as its topic`() =
        runTest {
            val engine = NeuroLearningBenchmark.engine()
            engine.onVideoInteraction(NeuroLearningBenchmark.phonk(0), InteractionType.DISLIKED)

            assertThat(engine.getBrainSnapshot().rejectionPatterns).containsKey("~phonk")
            engine.shutdown()
        }

    @Test
    fun `not interested does not raise boredom`() =
        runTest {
            val engine = NeuroLearningBenchmark.engine()
            repeat(4) { engine.markNotInterested(NeuroLearningBenchmark.phonk(it)) }

            assertThat(engine.getBrainSnapshot().consecutiveSkips).isEqualTo(0)
            engine.shutdown()
        }

    @Test
    fun `a hidden video never opens a related lane`() =
        runTest {
            val engine = NeuroLearningBenchmark.engine()
            val hidden = NeuroLearningBenchmark.comedy(0)
            engine.markNotInterested(hidden)
            val seeds =
                listOf(hidden, NeuroLearningBenchmark.comedy(1)).map {
                    GraphSeedInput(
                        id = it.id,
                        title = it.title,
                        channelId = "ch-${it.id}",
                        source = GraphSeedSource.LIKED,
                        engagementWeight = 1.0,
                        timestamp = now,
                        durationSec = 600,
                        percentWatched = 0.0,
                    )
                }

            assertThat(engine.selectRelatedSeeds(seeds, maxSeeds = 4)).containsExactly(NeuroLearningBenchmark.comedy(1).id)
            engine.shutdown()
        }
}
