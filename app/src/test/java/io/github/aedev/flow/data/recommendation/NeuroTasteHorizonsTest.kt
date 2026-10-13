/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark.watch
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.math.ln

/** #907: taste is evidence on two horizons, long-term and recent. */
class NeuroTasteHorizonsTest {
    private val longTerm = NeuroVectorMath.LONG_TERM

    private fun learn(
        vector: ContentVector,
        topics: Map<String, Double>,
        strength: Double = 1.0,
        horizon: NeuroVectorMath.Horizon = longTerm,
        protected: Set<String> = emptySet(),
    ) = NeuroVectorMath.learn(vector, ContentVector(topics = topics), strength, horizon, scalarRate = 0.0, protected = protected)

    private fun evidence(weight: Double) = -longTerm.saturation * ln(1.0 - weight)

    private fun ContentVector.keysWith(word: String) = topics.keys.filter { word in it }

    @Test
    fun `a topic in many videos outweighs a stronger one in few`() {
        var vector = ContentVector()
        repeat(10) { vector = learn(vector, mapOf("chess" to 0.2)) }
        repeat(2) { vector = learn(vector, mapOf("poker" to 0.6)) }

        assertThat(vector.topics.getValue("chess")).isGreaterThan(vector.topics.getValue("poker"))
    }

    @Test
    fun `evidence halves over the horizon's half-life of full watches`() {
        val start = learn(ContentVector(), mapOf("chess" to 1.0), strength = 5.0)
        var vector = start
        repeat(longTerm.halfLife.toInt()) { vector = learn(vector, mapOf("cooking" to 0.01)) }

        assertThat(evidence(vector.topics.getValue("chess"))).isWithin(1e-6).of(evidence(start.topics.getValue("chess")) / 2)
    }

    @Test
    fun `a Short teaches and erases in proportion to its strength`() {
        val start = learn(ContentVector(), mapOf("chess" to 1.0), strength = 5.0)
        var vector = start
        repeat(100) { vector = learn(vector, mapOf("prank" to 1.0), strength = 0.01) }

        assertThat(evidence(vector.topics.getValue("chess"))).isGreaterThan(evidence(start.topics.getValue("chess")) * 0.998)
        assertThat(evidence(vector.topics.getValue("prank"))).isWithin(0.01).of(1.0)
    }

    @Test
    fun `a word its phrase outweighs ten times is the phrase, unless it was picked`() {
        val vector = ContentVector(topics = mapOf("camera test" to 0.6, "camera" to 0.04, "test" to 0.04, "pixel" to 0.3))

        val learned = learn(vector, mapOf("pixel" to 0.5), protected = setOf("test"))

        assertThat(learned.topics.keys).containsExactly("camera test", "test", "pixel")
    }

    @Test
    fun `each horizon keeps its own number of topics`() {
        var vector = ContentVector()
        repeat(80) { vector = learn(vector, mapOf("topic$it" to 1.0), horizon = NeuroVectorMath.RECENT) }

        assertThat(vector.topics).hasSize(NeuroVectorMath.RECENT.maxTopics)
        assertThat(vector.topics).containsKey("topic79")
    }

    @Test
    fun `ranking taste gives recent a quarter and is long-term alone without it`() {
        val long = ContentVector(topics = mapOf("comedy" to 0.6, "cooking" to 0.8), duration = 0.9)
        val recent = ContentVector(topics = mapOf("guitar" to 0.4))

        assertThat(NeuroVectorMath.blend(long, ContentVector())).isEqualTo(long)
        val blended = NeuroVectorMath.blend(long, recent)
        assertThat(blended.topics.getValue("guitar")).isWithin(1e-9).of(NeuroVectorMath.RECENT_SHARE)
        assertThat(blended.topics.getValue("cooking")).isWithin(1e-9).of(0.8 * (1 - NeuroVectorMath.RECENT_SHARE))
        assertThat(blended.duration).isEqualTo(0.9)
    }

    @Test
    fun `the recent vector is saved with the brain`() {
        val storage = NeuroStorage(mockk(relaxed = true))
        val brain = UserBrain(recentVector = ContentVector(topics = mapOf("guitar" to 0.4)))

        val restored = with(storage) { brain.toSerializable() }.toUserBrain()

        assertThat(restored.recentVector.topics).containsExactly("guitar", 0.4)
    }

    @Test
    fun `traces of a half-watched video are never searched for or listed`() {
        val trace = TopicEvidence(positiveSignals = 2, positiveScore = 0.6, firstSeenAt = 1L, lastSeenAt = 1L)
        val brain =
            UserBrain(
                globalVector = ContentVector(topics = mapOf("call of duty" to 0.51, "lofi" to 0.5, "ops" to 0.0115, "modern" to 0.008)),
                preferredTopics = setOf("Call of Duty", "Lo-Fi"),
                topicEvidence = mapOf("ops" to trace, "modern" to trace),
            )
        val discovery = NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, NeuroTokenizer())

        val queries = (0..2).flatMap { depth -> discovery.generateQueries(brain, depth = depth) { FlowPersona.INITIATE } }

        assertThat(queries.map { it.query.lowercase() }).containsNoneOf("ops", "modern")
        assertThat(brain.interests.topics.keys).containsExactly("call of duty", "lofi")
    }

    @Test
    fun `a watch reaches both horizons, and a blocked topic leaves both`() =
        runBlocking {
            val engine = NeuroLearningBenchmark.engine()
            engine.watch(NeuroLearningBenchmark.guitar(0), 1f)
            val guitar = NeuroTokenizer().keysOf("guitar").single()
            val learned = engine.getBrainSnapshot()
            assertThat(learned.globalVector.topics).containsKey(guitar)
            assertThat(learned.recentVector.topics).containsKey(guitar)

            engine.addBlockedTopic("guitar")

            val blocked = engine.getBrainSnapshot()
            assertThat(blocked.globalVector.keysWith(guitar)).isEmpty()
            assertThat(blocked.recentVector.keysWith(guitar)).isEmpty()
            engine.shutdown()
        }

    @Test
    fun `not interested lowers the video's topics and leaves weak unrelated ones`() =
        runBlocking {
            val weak = ContentVector(topics = mapOf("chess" to 0.01, "comedy" to 0.5))
            val engine = NeuroLearningBenchmark.engine(UserBrain(globalVector = weak, recentVector = weak))

            engine.markNotInterested(NeuroLearningBenchmark.phonk(0))

            val brain = engine.getBrainSnapshot()
            assertThat(brain.globalVector.topics).containsKey("chess")
            assertThat(brain.recentVector.topics).containsKey("chess")
            engine.shutdown()
        }
}
