/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.ContentVector
import io.github.aedev.flow.data.recommendation.FeedEntry
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.NeuroTokenizer
import io.github.aedev.flow.data.recommendation.ScoredVideo
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.eval.NeuroEval.vec
import io.github.aedev.flow.data.recommendation.eval.NeuroEval.video
import org.junit.Test


class NeuroEvalTest {

    private fun matureBrain() = UserBrain(
        totalInteractions = 200,
        globalVector = ContentVector(
            topics = mapOf("python" to 0.6, "machine" to 0.45, "learning" to 0.4, "data" to 0.2)
        )
    )

    /** Fixed labelled candidate pool spanning relevant → adjacent → off-topic. */
    private fun pool() = listOf(
        NeuroEval.Labeled(video("p1", title = "python tutorial"), vec("python" to 0.9, "machine" to 0.5), 1.0),
        NeuroEval.Labeled(video("p2", title = "machine learning"), vec("machine" to 0.8, "learning" to 0.7), 1.0),
        NeuroEval.Labeled(video("d1", title = "data science"), vec("data" to 0.8, "python" to 0.3), 0.6),
        NeuroEval.Labeled(video("c1", title = "pasta recipe"), vec("cooking" to 0.9), 0.0),
        NeuroEval.Labeled(video("m1", title = "pop hits"), vec("music" to 0.9), 0.0)
    )

    @Test
    fun `scoreCandidate is deterministic`() {
        val p = NeuroEval.params(matureBrain())
        val item = pool().first()
        val a = NeuroScoring.scoreCandidate(item.video, item.vector, p)
        val b = NeuroScoring.scoreCandidate(item.video, item.vector, p)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `relevant content outranks off-topic for a mature brain`() {
        val p = NeuroEval.params(matureBrain())
        val relevant = pool().first { it.video.id == "p1" }
        val offTopic = pool().first { it.video.id == "c1" }
        val relScore = NeuroScoring.scoreCandidate(relevant.video, relevant.vector, p)
        val offScore = NeuroScoring.scoreCandidate(offTopic.video, offTopic.vector, p)
        assertThat(relScore).isGreaterThan(offScore)
    }

    @Test
    fun `golden ranking order is stable`() {
        val ranked = NeuroEval.rankByScore(pool(), NeuroEval.params(matureBrain()))
        assertThat(ranked.map { it.video.id })
            .containsExactly("p1", "p2", "d1", "c1", "m1").inOrder()
    }

    @Test
    fun `baseline ranking metrics are sane`() {
        val m = NeuroEval.evaluate(pool(), NeuroEval.params(matureBrain()), k = 5)
        println("BASELINE metrics: $m")
        assertThat(m.ndcg).isAtLeast(0.9)
        assertThat(m.ild).isGreaterThan(0.0)
        assertThat(m.coverage).isGreaterThan(0.0)
        assertThat(m.coverage).isAtMost(1.0)
        assertThat(m.selfSuppression).isWithin(1e-9).of(0.0)
    }

    @Test
    fun `inflated feed history suppresses never-watched content (I-1 pathology)`() {
        val now = NeuroEval.FIXED_NOW
        val unseen = pool()
        val feedHistory = unseen.associate {
            it.video.id to FeedEntry(lastShown = now - 3_600_000L, showCount = 5)
        }
        val brain = matureBrain().copy(feedHistory = feedHistory)
        val suppression = NeuroEval.selfSuppression(brain, unseen, now)
        assertThat(suppression).isGreaterThan(0.5)
    }

    @Test
    fun `diversity reranker interleaves channels at the top`() {
        val tokenizer = NeuroTokenizer()
        val titles = listOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot")
        val scored = mutableListOf(
            ScoredVideo(video("vA1", title = titles[0], channelId = "A"), 1.00, vec("python" to 0.9)),
            ScoredVideo(video("vB1", title = titles[1], channelId = "B"), 0.95, vec("cooking" to 0.9)),
            ScoredVideo(video("vA2", title = titles[2], channelId = "A"), 0.90, vec("python" to 0.9)),
            ScoredVideo(video("vB2", title = titles[3], channelId = "B"), 0.85, vec("cooking" to 0.9)),
            ScoredVideo(video("vA3", title = titles[4], channelId = "A"), 0.80, vec("python" to 0.9)),
            ScoredVideo(video("vB3", title = titles[5], channelId = "B"), 0.75, vec("cooking" to 0.9))
        )
        val result = NeuroScoring.applySmartDiversity(scored, tokenizer)
        assertThat(result.first().id).isEqualTo("vA1")
        assertThat(result[1].channelId).isNotEqualTo(result.first().channelId)
    }

    @Test
    fun `viewport impressions keep unseen content rankable (I-1)`() {
        val pool = pool()
        val now = NeuroEval.FIXED_NOW
        fun feedHistoryFor(ids: List<String>) = ids.associateWith {
            FeedEntry(lastShown = now - 3_600_000L, showCount = 3)
        }
        // Old behaviour recorded the whole ranked pool; I-1 records only dwelt items.
        val poolBrain = matureBrain().copy(feedHistory = feedHistoryFor(pool.map { it.video.id }))
        val viewportBrain = matureBrain().copy(
            feedHistory = feedHistoryFor(pool.take(2).map { it.video.id })
        )
        // d1 is relevant but was never scrolled into view this session.
        val unseen = pool.first { it.video.id == "d1" }
        val poolScore = NeuroScoring.scoreCandidate(
            unseen.video, unseen.vector, NeuroEval.params(poolBrain, poolSize = 30, now = now)
        )
        val viewportScore = NeuroScoring.scoreCandidate(
            unseen.video, unseen.vector, NeuroEval.params(viewportBrain, poolSize = 30, now = now)
        )
        println("I-1 unseen-content score — pool=$poolScore viewport=$viewportScore")
        assertThat(viewportScore).isGreaterThan(poolScore)
    }
}
