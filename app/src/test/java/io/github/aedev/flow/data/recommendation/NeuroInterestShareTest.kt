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

/** #907: a page shared between the viewer's interests, not filled by the strongest one. */
class NeuroInterestShareTest {
    private val weights = mapOf("tech" to 3.0, "anime" to 1.0)

    @Test
    fun `each group gets places in proportion to its weight and keeps its own order`() {
        val ranked = (1..12).map { "tech$it" } + (1..4).map { "anime$it" }

        val shared = NeuroInterestShare.interleave(ranked, weights) { it.removeSuffix(it.takeLastWhile(Char::isDigit)) }

        assertThat(shared.take(4).count { it.startsWith("anime") }).isEqualTo(1)
        assertThat(shared.take(8).count { it.startsWith("anime") }).isEqualTo(2)
        assertThat(shared.filter { it.startsWith("anime") }).containsExactly("anime1", "anime2", "anime3", "anime4").inOrder()
        assertThat(shared.filter { it.startsWith("tech") }).isEqualTo((1..12).map { "tech$it" })
    }

    @Test
    fun `videos of no interest keep their share of the page`() {
        val ranked = (1..6).map { "tech$it" } + (1..2).map { "anime$it" } + (1..8).map { "other$it" }

        val shared =
            NeuroInterestShare.interleave(ranked, weights) {
                it.removeSuffix(it.takeLastWhile(Char::isDigit)).takeIf { g ->
                    g !=
                        "other"
                }
            }

        assertThat(shared.take(8).count { it.startsWith("other") }).isIn(3..5)
        assertThat(shared).containsExactlyElementsIn(ranked)
    }

    @Test
    fun `one interest leaves the ranking alone`() {
        val ranked = listOf("a1", "b1", "a2")

        assertThat(NeuroInterestShare.interleave(ranked, mapOf("a" to 1.0)) { it.take(1) }).isEqualTo(ranked)
        assertThat(NeuroInterestShare.interleave(ranked, weights) { "tech" }).isEqualTo(ranked)
    }

    @Test
    fun `a video belongs to the group most of its topics come from`() {
        val groups = mapOf("anime" to "anime", "horror" to "anime", "code" to "tech")

        assertThat(NeuroInterestShare.groupOf(mapOf("anime" to 0.3, "horror" to 0.3, "code" to 0.4), groups)).isEqualTo("anime")
        assertThat(NeuroInterestShare.groupOf(mapOf("knife" to 0.9), groups)).isNull()
    }

    @Test
    fun `small groups are not interests yet, and weights flatten`() {
        val clusters =
            listOf(
                NeuroClusters.TopicCluster("tech", listOf("tech"), 3.6),
                NeuroClusters.TopicCluster("anime", listOf("anime"), 0.9),
                NeuroClusters.TopicCluster("italian", listOf("italian"), 0.06),
            )

        val w = NeuroInterestShare.weights(clusters)

        assertThat(w.keys).containsExactly("tech", "anime")
        assertThat(w.getValue("tech") / w.getValue("anime")).isWithin(1e-9).of(2.0)
    }

    @Test
    fun `a viewer of nine tech picks and one anime pick still sees anime near the top`() =
        runBlocking<Unit> {
            val tokenizer = NeuroTokenizer()
            val tech =
                listOf(
                    "programming",
                    "android",
                    "smartphones",
                    "laptops",
                    "web development",
                    "app development",
                    "machine learning",
                    "coding",
                    "ai",
                ).flatMap(tokenizer::keysOf)
            val brain =
                UserBrain(
                    globalVector = ContentVector(topics = tech.associateWith { 0.45 } + mapOf("anime" to 0.45, "horror" to 0.04)),
                    preferredTopics = setOf("Programming", "Android", "Anime"),
                    // Watched together, as on a real profile, the tech picks form one group.
                    topicAffinities = tech.zipWithNext { a, b -> NeuroScoring.makeAffinityKey(a, b) to 0.5 }.toMap(),
                    totalInteractions = 4000,
                )
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER)

            fun video(
                id: String,
                title: String,
            ) = Video(id, title, "Channel $id", "ch-$id", "", 900, 100_000, uploadDate = "3 days ago")
            val techTitles =
                listOf(
                    "Android programming for beginners",
                    "Kotlin coroutines explained",
                    "Best laptops for coding",
                    "Web development roadmap",
                    "Machine learning crash course",
                    "App development with Compose",
                    "Smartphones compared this year",
                    "AI coding agents tested",
                    "Python machine learning project",
                    "Android app architecture",
                    "Laptops for programming students",
                    "Build a web app with AI",
                    "Smartphones camera shootout",
                    "Coding interview tips",
                    "Machine learning math basics",
                    "Android Studio tricks",
                )
            val animeTitles =
                listOf("Top horror anime of all time", "Junji Ito anime review", "Scariest anime creatures", "Horror anime you missed")
            val pool = techTitles.mapIndexed { i, t -> video("t$i", t) } + animeTitles.mapIndexed { i, t -> video("a$i", t) }

            val ranked = engine.rank(pool, emptySet())
            val shared = engine.shareByInterest(ranked)
            engine.shutdown()

            // Ranked alone, the first anime video sits tenth; shared, it is in the first four.
            assertThat(shared.indexOfFirst { it.id.startsWith("a") }).isLessThan(4)
            assertThat(shared.take(12).count { it.id.startsWith("a") }).isAtLeast(3)
        }
}
