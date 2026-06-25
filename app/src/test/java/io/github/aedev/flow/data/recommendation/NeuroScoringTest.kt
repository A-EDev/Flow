/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroEval.vec
import io.github.aedev.flow.data.recommendation.eval.NeuroEval.video
import org.junit.Test

class NeuroScoringTest {

    private val now = 1_700_000_000_000L

    // ── Relevance floor ──

    @Test
    fun `relevance floor exempts subscriptions and cold brains`() {
        assertThat(NeuroScoring.calculateRelevanceFloor(0.0, 200, isSubscription = true)).isEqualTo(1.0)
        assertThat(NeuroScoring.calculateRelevanceFloor(0.0, 50, isSubscription = false)).isEqualTo(1.0)
    }

    @Test
    fun `relevance floor penalizes off-topic content on mature brains`() {
        assertThat(NeuroScoring.calculateRelevanceFloor(0.03, 200, false))
            .isEqualTo(NeuroScoring.RELEVANCE_FLOOR_SEVERE_PENALTY)
        assertThat(NeuroScoring.calculateRelevanceFloor(0.08, 200, false))
            .isEqualTo(NeuroScoring.RELEVANCE_FLOOR_MODERATE_PENALTY)
        assertThat(NeuroScoring.calculateRelevanceFloor(0.5, 200, false)).isEqualTo(1.0)
    }

    // ── Already-watched penalty ──

    @Test
    fun `watched penalty is neutral without history and steep on full watches`() {
        val v = video("a", title = "python tutorial", duration = 600)
        assertThat(NeuroScoring.calculateWatchedPenalty(v, null)).isEqualTo(1.0)
        assertThat(NeuroScoring.calculateWatchedPenalty(v, WatchEntry(0.9f, now)))
            .isEqualTo(NeuroScoring.WATCHED_PENALTY_FULL)
    }

    @Test
    fun `music tracks are exempt from the rewatch penalty`() {
        val music = video("m", title = "lofi remix", duration = 200)
        assertThat(NeuroScoring.calculateWatchedPenalty(music, WatchEntry(0.6f, now))).isEqualTo(1.0)
    }

    // ── Channel signal ──

    @Test
    fun `fresh subscribed uploads get a positive channel signal`() {
        val v = video("s", channelId = "ch_s", uploadDate = "2 hours ago")
        val signal = NeuroScoring.calculateChannelSignal(v, UserBrain(), setOf("ch_s"))
        assertThat(signal).isGreaterThan(0.0)
    }

    @Test
    fun `non-subscribed unknown channels are neutral`() {
        val v = video("n", channelId = "ch_n")
        assertThat(NeuroScoring.calculateChannelSignal(v, UserBrain(), emptySet())).isEqualTo(0.0)
    }

    // ── Adaptive jitter ──

    @Test
    fun `jitter is high at cold start and rises with feed staleness`() {
        assertThat(NeuroScoring.calculateAdaptiveJitter(10, 0.0)).isEqualTo(NeuroScoring.JITTER_COLD_START)
        assertThat(NeuroScoring.calculateAdaptiveJitter(100, 0.6)).isEqualTo(0.12)
        assertThat(NeuroScoring.calculateAdaptiveJitter(100, 0.0)).isEqualTo(NeuroScoring.JITTER_NORMAL)
    }

    // ── Topic affinity key ──

    @Test
    fun `affinity key is order-independent`() {
        assertThat(NeuroScoring.makeAffinityKey("b", "a"))
            .isEqualTo(NeuroScoring.makeAffinityKey("a", "b"))
    }

    // ── Topic probation ──

    @Test
    fun `probation damps unconfirmed mid-weight topics on mature brains`() {
        val brain = UserBrain(
            totalInteractions = 200,
            globalVector = ContentVector(topics = mapOf("foo" to 0.1))
        )
        val penalty = NeuroScoring.calculateTopicProbationPenalty(
            vec("foo" to 0.9), brain, emptySet()
        )
        assertThat(penalty).isLessThan(1.0)
    }

    @Test
    fun `confirmed topic evidence lifts probation`() {
        val brain = UserBrain(
            totalInteractions = 200,
            globalVector = ContentVector(topics = mapOf("foo" to 0.1)),
            topicEvidence = mapOf("foo" to TopicEvidence(explicitSignals = 1))
        )
        val penalty = NeuroScoring.calculateTopicProbationPenalty(
            vec("foo" to 0.9), brain, emptySet()
        )
        assertThat(penalty).isEqualTo(1.0)
    }

    @Test
    fun `probation is inactive during cold start`() {
        val brain = UserBrain(
            totalInteractions = 10,
            globalVector = ContentVector(topics = mapOf("foo" to 0.1))
        )
        assertThat(NeuroScoring.calculateTopicProbationPenalty(vec("foo" to 0.9), brain, emptySet()))
            .isEqualTo(1.0)
    }

    // ── Exploration (UCB) ──

    @Test
    fun `exploration bonus is gated by weight and bounded`() {
        val brain = UserBrain(totalInteractions = 200)
        val v = vec("newtopic" to 0.9)
        assertThat(NeuroScoring.explorationBonus(v, brain, exploreWeight = 0.0)).isEqualTo(0.0)
        val bonus = NeuroScoring.explorationBonus(v, brain, exploreWeight = 1.0)
        assertThat(bonus).isGreaterThan(0.0)
        assertThat(bonus).isAtMost(NeuroScoring.EXPLORE_MAX_BONUS)
    }

    @Test
    fun `novel topics get a larger exploration bonus than well-explored ones`() {
        val brain = UserBrain(
            totalInteractions = 200,
            topicEvidence = mapOf("known" to TopicEvidence(positiveSignals = 50))
        )
        val known = NeuroScoring.explorationBonus(vec("known" to 0.9), brain, 1.0)
        val novel = NeuroScoring.explorationBonus(vec("novel" to 0.9), brain, 1.0)
        assertThat(novel).isGreaterThan(known)
    }

    @Test
    fun `exploration is inactive at cold start`() {
        val cold = UserBrain(totalInteractions = 10)
        assertThat(NeuroScoring.explorationBonus(vec("x" to 0.9), cold, 1.0)).isEqualTo(0.0)
    }

    @Test
    fun `repeatedly disliked topics are not explored`() {
        val brain = UserBrain(
            totalInteractions = 200,
            topicEvidence = mapOf("hated" to TopicEvidence(negativeSignals = 40))
        )
        val hated = NeuroScoring.explorationBonus(vec("hated" to 0.9), brain, 1.0)
        val fresh = NeuroScoring.explorationBonus(vec("brandnew" to 0.9), brain, 1.0)
        assertThat(hated).isLessThan(fresh)
    }

    // ── IDF vocabulary cap (I-13 slice) ──

    @Test
    fun `idf cap keeps the most frequent keys and drops the rest`() {
        val freq = HashMap<String, Int>()
        repeat(NeuroScoring.IDF_MAX_KEYS + 500) { freq["w$it"] = it }
        NeuroScoring.capIdfVocabulary(freq)
        assertThat(freq).hasSize(NeuroScoring.IDF_KEEP_KEYS)
        assertThat(freq).containsKey("w${NeuroScoring.IDF_MAX_KEYS + 499}")
        assertThat(freq).doesNotContainKey("w0")
    }

    @Test
    fun `idf cap is a no-op below the limit`() {
        val freq = hashMapOf("a" to 1, "b" to 2)
        NeuroScoring.capIdfVocabulary(freq)
        assertThat(freq).hasSize(2)
    }
}
