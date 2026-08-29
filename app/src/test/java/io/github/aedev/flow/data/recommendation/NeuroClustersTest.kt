/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeuroClustersTest {
    private val now = 1_700_000_000_000L
    private val noLemma: (String) -> String = { it.lowercase() }

    private fun build(
        scores: Map<String, Double>,
        affinities: Map<String, Double> = emptyMap(),
        profiles: Map<String, Map<String, Double>> = emptyMap(),
        categories: List<TopicCategory> = emptyList(),
    ) = NeuroClusters.buildClusters(scores, affinities, profiles, categories, noLemma)

    @Test
    fun `affinity edges group co-watched topics into one cluster`() {
        val clusters =
            build(
                scores = mapOf("minecraft" to 0.6, "speedrun" to 0.4, "cooking" to 0.5, "recipe" to 0.3),
                affinities =
                    mapOf(
                        NeuroScoring.makeAffinityKey("minecraft", "speedrun") to 0.3,
                        NeuroScoring.makeAffinityKey("cooking", "recipe") to 0.3,
                    ),
            )
        assertThat(clusters).hasSize(2)
        val gaming = clusters.first { it.representative == "minecraft" }
        assertThat(gaming.topics).containsExactly("minecraft", "speedrun")
        val cookingCluster = clusters.first { it.representative == "cooking" }
        assertThat(cookingCluster.topics).containsExactly("cooking", "recipe")
    }

    @Test
    fun `channel profiles link topics taught by the same channel`() {
        val clusters =
            build(
                scores = mapOf("guitar" to 0.5, "chord" to 0.2, "physics" to 0.3),
                profiles = mapOf("chMusic" to mapOf("guitar" to 0.6, "chord" to 0.5)),
            )
        assertThat(clusters.first { it.representative == "guitar" }.topics).contains("chord")
        assertThat(clusters.first { it.representative == "physics" }.topics).containsExactly("physics")
    }

    @Test
    fun `unconnected topics stay singleton clusters`() {
        val clusters = build(scores = mapOf("chess" to 0.2, "woodworking" to 0.15))
        assertThat(clusters).hasSize(2)
    }

    @Test
    fun `clustering is deterministic`() {
        val scores = mapOf("a1" to 0.5, "b2" to 0.5, "c3" to 0.3, "d4" to 0.3)
        val affinities = mapOf("a1|b2" to 0.2, "c3|d4" to 0.2)
        val first = build(scores, affinities)
        val second = build(scores, affinities)
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `schedule boosts never-served clusters over a just-served giant`() {
        val big = NeuroClusters.TopicCluster("gaming", listOf("gaming"), mass = 1.5)
        val small = NeuroClusters.TopicCluster("chess", listOf("chess"), mass = 0.2)
        val justServed = mapOf("gaming" to now - 60_000L)

        val order = NeuroClusters.schedule(listOf(big, small), justServed, now)
        assertThat(order.first().representative).isEqualTo("chess")
    }

    @Test
    fun `schedule lets a big cluster reclaim priority as staleness accrues`() {
        val big = NeuroClusters.TopicCluster("gaming", listOf("gaming"), mass = 1.5)
        val small = NeuroClusters.TopicCluster("chess", listOf("chess"), mass = 0.2)
        val bothServedLongAgo =
            mapOf(
                "gaming" to now - 48L * 3_600_000L,
                "chess" to now - 48L * 3_600_000L,
            )
        val order = NeuroClusters.schedule(listOf(big, small), bothServedLongAgo, now)
        assertThat(order.first().representative).isEqualTo("gaming")
    }

    @Test
    fun `every cluster is served within a bounded number of sessions`() {
        val clusters =
            listOf(
                NeuroClusters.TopicCluster("gaming", listOf("gaming"), 1.5),
                NeuroClusters.TopicCluster("cooking", listOf("cooking"), 0.9),
                NeuroClusters.TopicCluster("guitar", listOf("guitar"), 0.8),
                NeuroClusters.TopicCluster("physics", listOf("physics"), 0.4),
                NeuroClusters.TopicCluster("chess", listOf("chess"), 0.25),
                NeuroClusters.TopicCluster("woodworking", listOf("woodworking"), 0.15),
            )
        var rotation = mapOf<String, Long>()
        var clock = now
        val servedEver = mutableSetOf<String>()
        val slotsPerSession = 3

        repeat(4) {
            val served = NeuroClusters.schedule(clusters, rotation, clock).take(slotsPerSession)
            servedEver += served.map { it.representative }
            rotation = rotation + served.associate { it.representative to clock }
            clock += 4L * 3_600_000L
        }

        // 6 clusters, 3 slots, 4 sessions: rotation must have cycled through all.
        assertThat(servedEver).hasSize(clusters.size)
    }
}
