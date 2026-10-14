/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** #907: YouTube's category lowers a foreign video a little, never more. */
class NeuroCategoriesTest {
    private val musicViewer = mapOf("music" to 40.0, "comedy" to 10.0)

    private fun video(
        id: String,
        channelId: String,
        category: String? = null,
    ) = Video(id, "Lofi beats to relax", "Channel $channelId", channelId, "", 1800, 400_000, uploadDate = "3 days ago", category = category)

    @Test
    fun `categories are read as the microformat writes them`() {
        assertThat(NeuroCategories.idOf("Science & Technology")).isEqualTo("science")
        assertThat(NeuroCategories.idOf(" music ")).isEqualTo("music")
        assertThat(NeuroCategories.idOf("Shows")).isNull()
        assertThat(NeuroCategories.idOf(null)).isNull()
    }

    @Test
    fun `a foreign category takes at most a third off, a familiar one nothing`() {
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 1.0))).isWithin(1e-9).of(1 - NeuroCategories.MAX_PENALTY)
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("comedy", 1.0))).isEqualTo(1.0)
        assertThat(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 0.5))).isGreaterThan(1 - NeuroCategories.MAX_PENALTY)
    }

    @Test
    fun `a default category says less than a chosen one`() {
        val entertainment = NeuroCategories.factor(musicViewer, ChannelCategory("entertainment", 1.0))

        assertThat(entertainment).isGreaterThan(NeuroCategories.factor(musicViewer, ChannelCategory("gaming", 1.0)))
    }

    @Test
    fun `nothing changes before twenty categorized watches`() {
        val few = (1..19).fold(emptyMap<String, Double>()) { mix, _ -> NeuroCategories.learn(mix, "music") }

        assertThat(NeuroCategories.factor(few, ChannelCategory("gaming", 1.0))).isEqualTo(1.0)
        assertThat(NeuroCategories.factor(NeuroCategories.learn(few, "music"), ChannelCategory("gaming", 1.0))).isLessThan(1.0)
    }

    @Test
    fun `the mix follows what the viewer watches now`() {
        val old = (1..400).fold(emptyMap<String, Double>()) { mix, _ -> NeuroCategories.learn(mix, "gaming") }
        val now = (1..400).fold(old) { mix, _ -> NeuroCategories.learn(mix, "music") }

        assertThat(now.getValue("music")).isGreaterThan(now.getValue("gaming") * 2)
        assertThat(now.values.sum()).isAtMost(400.0)
    }

    @Test
    fun `a channel's category firms up with every video seen in it`() {
        val store = NeuroCategoryStore(mockk(relaxed = true))
        store.record("ch", "gaming")
        val once = store.categoryOf("ch")!!.confidence
        repeat(4) { store.record("ch", "gaming") }

        assertThat(store.categoryOf("ch")!!.confidence).isGreaterThan(once)
        assertThat(store.categoryOf("unknown")).isNull()
    }

    @Test
    fun `a watched video teaches its channel's category and the viewer's mix`() =
        runBlocking {
            val store = NeuroCategoryStore(mockk(relaxed = true))
            val engine = NeuroLearningBenchmark.engine(categoryStore = store)

            engine.onVideoInteraction(video("v", "ch-music", category = "Music"), InteractionType.WATCHED, 0.9f)
            engine.onVideoInteraction(video("s", "ch-shorts", category = "Comedy").copy(isShort = true), InteractionType.WATCHED, 0.9f)
            engine.onVideoInteraction(video("k", "ch-news", category = "News & Politics"), InteractionType.SKIPPED)

            assertThat(store.categoryOf("ch-music")?.id).isEqualTo("music")
            assertThat(store.categoryOf("ch-news")?.id).isEqualTo("news")
            assertThat(engine.getBrainSnapshot().categoryAffinity).containsExactly("music", 1.0)
            engine.shutdown()
        }

    @Test
    fun `three games read as one Gaming chip that searches each game`() =
        runBlocking<Unit> {
            val store = NeuroCategoryStore(mockk(relaxed = true))
            listOf("ch-mc", "ch-cod", "ch-fn").forEach { channel -> repeat(3) { store.record(channel, "gaming") } }
            repeat(3) { store.record("ch-lofi", "music") }
            val evidence = TopicEvidence(watchSignals = 3, positiveSignals = 3, positiveScore = 2.0)
            val topics = mapOf("minecraft" to 0.6, "call of duty" to 0.55, "fortnite" to 0.5, "lofi" to 0.45)
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = topics),
                    channelTopicProfiles =
                        mapOf(
                            "ch-mc" to mapOf("minecraft" to 0.6),
                            "ch-cod" to mapOf("call of duty" to 0.6),
                            "ch-fn" to mapOf("fortnite" to 0.6),
                            "ch-lofi" to mapOf("lofi" to 0.6),
                        ),
                    topicEvidence = topics.mapValues { evidence },
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, categoryStore = store)

            val chips = engine.interestChips(1_800_000_000_000L)
            engine.shutdown()

            assertThat(chips.map { it.category }).containsExactly("gaming", null).inOrder()
            assertThat(chips.first().parts.map { it.representative }).containsExactly("minecraft", "call of duty", "fortnite")
            assertThat(chips.last().label).isEqualTo("Lofi")
        }

    @Test
    fun `a video from a channel in a foreign category ranks below an equal one, unless subscribed`() =
        runBlocking {
            val store = NeuroCategoryStore(mockk(relaxed = true))
            repeat(3) { store.record("ch-game", "gaming") }
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = mapOf("lofi" to 0.5, "beat" to 0.4)),
                    categoryAffinity = musicViewer,
                    totalInteractions = 100,
                )
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER, store)
            val gaming = video("game", "ch-game")
            val unknown = video("other", "ch-new")

            assertThat(engine.rank(listOf(gaming, unknown), emptySet()).first().id).isEqualTo("other")
            assertThat(engine.rank(listOf(gaming, unknown), setOf("ch-game")).first().id).isEqualTo("game")
            engine.shutdown()
        }
}
