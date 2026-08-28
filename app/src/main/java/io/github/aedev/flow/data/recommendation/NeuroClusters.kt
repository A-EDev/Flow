/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.pow

/**
 * Interest clustering + rotation scheduling — SimClusters miniaturized.
 *
 * Groups the user's learned topics into interest communities using ONLY
 * user-grounded evidence (co-watch affinities, channel topic profiles, and
 * catalog co-membership of topics the user already has — the catalog links
 * existing topics, it never injects new ones). A weighted round-robin
 * scheduler then guarantees every community cycles into discovery over
 * successive refreshes instead of the top interests monopolizing every slot.
 *
 * Everything here is pure and deterministic — no clock reads, no state.
 */
internal object NeuroClusters {
    /**
     * Mass exponent < 1 flattens dominance so big clusters can't monopolize.
     * sqrt is deliberate: with max staleness 4x, even a mass-0.15 tail cluster
     * outranks a mass-1.5 giant that was just served (0.39*4 > 1.22*1).
     */
    private const val MASS_EXPONENT = 0.5

    /** Staleness doubles priority every this-many hours since last served. */
    private const val STALENESS_HALF_LIFE_HOURS = 12.0

    /** Never-served and long-unserved clusters get this max staleness boost. */
    private const val STALENESS_MAX = 4.0

    /** Weakest link admitted into the topic graph. */
    private const val MIN_EDGE_WEIGHT = 0.02

    private const val CHANNEL_PROFILE_TOP_TOPICS = 6
    private const val CHANNEL_EDGE_SCALE = 0.5
    private const val CATALOG_EDGE_WEIGHT = 0.3
    private const val PROPAGATION_ITERATIONS = 5
    private const val SELF_WEIGHT_SCALE = 0.5

    data class TopicCluster(
        val representative: String,
        val topics: List<String>,
        val mass: Double,
    )

    /**
     * Builds interest clusters from the brain's own graph. Topic node ids are
     * domain-stripped ("metal:music" → "metal") so evidence sources agree.
     */
    fun buildClusters(
        topicScores: Map<String, Double>,
        affinities: Map<String, Double>,
        channelTopicProfiles: Map<String, Map<String, Double>>,
        categories: List<TopicCategory>,
        normalizeLemma: (String) -> String,
    ): List<TopicCluster> {
        // Nodes: strip domain tags, keep the strongest score per base word.
        val scores = HashMap<String, Double>()
        topicScores.forEach { (topic, score) ->
            val base = NeuroScoring.stripDomainTag(topic)
            if (base.length >= 3) scores.merge(base, score, ::maxOf)
        }
        if (scores.isEmpty()) return emptyList()

        val adjacency = HashMap<String, HashMap<String, Double>>()

        fun addEdge(
            a: String,
            b: String,
            weight: Double,
        ) {
            if (a == b || weight < MIN_EDGE_WEIGHT) return
            if (a !in scores || b !in scores) return
            adjacency.getOrPut(a) { HashMap() }.merge(b, weight, Double::plus)
            adjacency.getOrPut(b) { HashMap() }.merge(a, weight, Double::plus)
        }

        // (a) Co-watch affinities — the strongest user evidence.
        affinities.forEach { (key, weight) ->
            val parts = key.split("|")
            if (parts.size == 2) addEdge(parts[0], parts[1], weight)
        }

        // (b) Topics taught by the same channel belong together.
        channelTopicProfiles.values.forEach { profile ->
            val top =
                profile.entries
                    .sortedByDescending { it.value }
                    .take(CHANNEL_PROFILE_TOP_TOPICS)
                    .map { NeuroScoring.stripDomainTag(it.key) to it.value }
            for (i in top.indices) {
                for (j in i + 1 until top.size) {
                    addEdge(top[i].first, top[j].first, minOf(top[i].second, top[j].second) * CHANNEL_EDGE_SCALE)
                }
            }
        }

        // (c) Catalog co-membership — links only topics the user already has.
        categories.forEach { category ->
            val present = category.topics.map(normalizeLemma).filter { it in scores }
            for (i in present.indices) {
                for (j in i + 1 until present.size) {
                    addEdge(present[i], present[j], CATALOG_EDGE_WEIGHT)
                }
            }
        }

        // Deterministic label propagation: visit strong topics first; ties break
        // lexicographically so the outcome is stable across runs.
        val nodes = scores.keys.sortedWith(compareByDescending<String> { scores.getValue(it) }.thenBy { it })
        val labels = HashMap<String, String>(nodes.size)
        nodes.forEach { labels[it] = it }

        var iteration = 0
        while (iteration < PROPAGATION_ITERATIONS) {
            var changed = false
            for (node in nodes) {
                val weightByLabel = HashMap<String, Double>()
                weightByLabel[labels.getValue(node)] = scores.getValue(node) * SELF_WEIGHT_SCALE
                adjacency[node]?.forEach { (neighbor, weight) ->
                    val label = labels.getValue(neighbor)
                    weightByLabel.merge(label, weight, Double::plus)
                }
                val best =
                    weightByLabel.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
                        .first()
                        .key
                if (best != labels[node]) {
                    labels[node] = best
                    changed = true
                }
            }
            if (!changed) break
            iteration++
        }

        return labels.entries
            .groupBy({ it.value }, { it.key })
            .values
            .map { members ->
                val sorted = members.sortedWith(compareByDescending<String> { scores.getValue(it) }.thenBy { it })
                TopicCluster(
                    representative = sorted.first(),
                    topics = sorted,
                    mass = members.sumOf { scores.getValue(it) },
                )
            }.sortedWith(compareByDescending<TopicCluster> { it.mass }.thenBy { it.representative })
    }

    /**
     * Orders clusters for serving: weight = mass^0.7 × staleness. Flattened mass
     * stops the top cluster monopolizing; staleness guarantees the tail cycles in.
     * A cluster never served (or long unserved) gets the max boost, so every
     * saved interest reaches the feed within a bounded number of refreshes.
     */
    fun schedule(
        clusters: List<TopicCluster>,
        rotation: Map<String, Long>,
        now: Long,
    ): List<TopicCluster> =
        clusters
            .map { cluster ->
                val lastServed = rotation[cluster.representative]
                val staleness =
                    if (lastServed == null || lastServed <= 0L) {
                        STALENESS_MAX
                    } else {
                        val hoursSince = (now - lastServed) / 3_600_000.0
                        (1.0 + hoursSince / STALENESS_HALF_LIFE_HOURS).coerceAtMost(STALENESS_MAX)
                    }
                cluster to cluster.mass.pow(MASS_EXPONENT) * staleness
            }.sortedWith(
                compareByDescending<Pair<TopicCluster, Double>> { it.second }
                    .thenBy { it.first.representative },
            ).map { it.first }
}
