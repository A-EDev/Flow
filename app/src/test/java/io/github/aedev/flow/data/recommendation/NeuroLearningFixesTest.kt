/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroBenchmark
import io.github.aedev.flow.data.recommendation.eval.NeuroEval
import org.junit.Test

class NeuroLearningFixesTest {
    private val tokenizer = NeuroTokenizer()

    // ── F2: topic acquisition floor ──

    @Test
    fun `plantTopics lifts new topics to a survivable weight`() {
        val current = ContentVector(topics = mapOf("android" to 0.4))
        val source = ContentVector(topics = mapOf("claude" to 0.8, "cursor" to 0.5, "android" to 0.3))
        val planted = NeuroVectorMath.plantTopics(current, source, floor = 0.05, topK = 3)
        assertThat(planted.topics["claude"]).isEqualTo(0.05)
        assertThat(planted.topics["cursor"]).isEqualTo(0.05)
        // Established scores are never lowered.
        assertThat(planted.topics["android"]).isEqualTo(0.4)
    }

    @Test
    fun `planted topics survive the prune threshold`() {
        val planted =
            NeuroVectorMath.plantTopics(
                ContentVector(),
                ContentVector(topics = mapOf("woodworking" to 0.9)),
                floor = NeuroScoring.TOPIC_ACQUISITION_FLOOR,
                topK = 3,
            )
        assertThat(planted.topics.getValue("woodworking"))
            .isGreaterThan(NeuroVectorMath.TOPIC_PRUNE_THRESHOLD)
    }

    // ── F4: noise-topic detection ──

    @Test
    fun `noise topics are recognized`() {
        assertThat(tokenizer.isNoiseTopic("right")).isTrue()
        assertThat(tokenizer.isNoiseTopic("laptops right")).isTrue()
        assertThat(tokenizer.isNoiseTopic("2026")).isTrue()
        assertThat(tokenizer.isNoiseTopic("ok")).isTrue()
        assertThat(tokenizer.isNoiseTopic("machine learning")).isFalse()
        assertThat(tokenizer.isNoiseTopic("claude")).isFalse()
        assertThat(tokenizer.isNoiseTopic("metal:music")).isFalse()
    }

    // ── F3: V15 maintenance ──

    private fun fossilBrain() =
        UserBrain(
            totalInteractions = 3000,
            schemaVersion = 14,
            globalVector =
                ContentVector(
                    topics =
                        mapOf(
                            "android" to 0.39,
                            "laptops right" to 0.001,
                            "right" to 0.0,
                        ),
                ),
            channelScores = mapOf("chClaude" to 0.7, "chCars" to 0.6, "chBad" to 0.2),
            channelTopicProfiles =
                mapOf(
                    "chClaude" to mapOf("claude" to 0.6, "coding agents" to 0.4, "ai" to 0.3),
                    "chCars" to mapOf("cars" to 0.5, "engine swap" to 0.3),
                    "chBad" to mapOf("spam" to 0.9),
                ),
        )

    @Test
    fun `v15 maintenance scrubs junk and rehydrates from channel profiles`() {
        val updated = NeuroMaintenance.runV15IfNeeded(fossilBrain(), tokenizer)

        assertThat(updated.schemaVersion).isEqualTo(NeuroMaintenance.TARGET_SCHEMA_VERSION)
        // Junk scrubbed.
        assertThat(updated.globalVector.topics).doesNotContainKey("right")
        assertThat(updated.globalVector.topics).doesNotContainKey("laptops right")
        // Real interests recovered from profiles, capped below established.
        assertThat(updated.globalVector.topics).containsKey("claude")
        assertThat(updated.globalVector.topics).containsKey("cars")
        assertThat(updated.globalVector.topics.getValue("claude")).isAtMost(0.30)
        // Low-quality channels contribute nothing.
        assertThat(updated.globalVector.topics).doesNotContainKey("spam")
        // Affinity structure seeded from co-taught topics.
        assertThat(updated.topicAffinities).isNotEmpty()
        assertThat(updated.topicAffinities.keys.any { it.contains("claude") }).isTrue()
        // Existing scores never lowered.
        assertThat(updated.globalVector.topics.getValue("android")).isEqualTo(0.39)
    }

    @Test
    fun `v15 maintenance is a no-op on current brains`() {
        val brain = fossilBrain().copy(schemaVersion = 15)
        assertThat(NeuroMaintenance.runV15IfNeeded(brain, tokenizer)).isSameInstanceAs(brain)
    }

    // ── F5: every cluster in every feed + tree-depth descent ──

    @Test
    fun `depth zero queries cover every interest cluster in one feed`() {
        val universe = NeuroBenchmark.multiInterestUniverse()
        val brain = NeuroBenchmark.brainFor(universe)
        val discovery = NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, tokenizer)

        val queries =
            discovery.generateQueries(brain, NeuroEval.FIXED_NOW, depth = 0) { FlowPersona.EXPLORER }
        val servedClusters = queries.mapNotNull { it.clusterKey }.toSet()

        // All six groups (gaming, cooking, guitar, physics, chess, woodworking)
        // must contribute to the SAME feed — coverage is no longer rotated away.
        assertThat(servedClusters).hasSize(universe.groups.size)
    }

    @Test
    fun `deeper rounds dig different branches of the same clusters`() {
        val universe = NeuroBenchmark.multiInterestUniverse()
        val brain = NeuroBenchmark.brainFor(universe)
        val discovery = NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, tokenizer)

        fun deepDives(depth: Int) =
            discovery
                .generateQueries(brain, NeuroEval.FIXED_NOW, depth) { FlowPersona.EXPLORER }
                .filter { it.strategy == QueryStrategy.DEEP_DIVE }
                .map { it.query }
                .toSet()

        val surface = deepDives(0)
        val page2 = deepDives(1)
        val page3 = deepDives(2)

        // Tree descent: page 2 combines anchors with branch members, so its
        // query set differs from the surface pass and from page 3.
        assertThat(page2).isNotEqualTo(surface)
        assertThat(page2.any { it.contains(' ') }).isTrue()
        assertThat(page3).isNotEqualTo(page2)
    }
}
