/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 *
 * This recommendation algorithm (FlowNeuroEngine) is the intellectual property
 * of the Flow project. Any use of this code in other projects must
 * explicitly credit "Flow Android Client" and link back to the original repository.
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.sqrt

/**
 * Shares a ranked list between the viewer's lasting interest groups (#907). Ranking by one taste
 * fills every page with the strongest group: a viewer with nine tech picks and one anime pick saw
 * anime fall from about ten cards in sixty to one. Each group instead gets places in proportion to
 * the square root of its weight, the flattening discovery already uses, and keeps its own ranked
 * order; videos of no group keep the share they had.
 */
internal object NeuroInterestShare {
    /** A group under this share of the viewer's taste is not one of their interests yet. */
    const val MIN_GROUP_SHARE = 0.08

    /** Each group's weight from [clusters]: the square root of its mass, for groups above [MIN_GROUP_SHARE]. */
    fun weights(clusters: List<NeuroClusters.TopicCluster>): Map<String, Double> {
        val total = clusters.sumOf { it.mass }.takeIf { it > 0.0 } ?: return emptyMap()
        return clusters.filter { it.mass / total >= MIN_GROUP_SHARE }.associate { it.representative to sqrt(it.mass) }
    }

    /** The group of [topics] holds the most of: summed weight per group, none when nothing matches. */
    fun groupOf(
        topics: Map<String, Double>,
        groupByTopic: Map<String, String>,
    ): String? {
        val byGroup = HashMap<String, Double>()
        topics.forEach { (key, weight) -> groupByTopic[NeuroScoring.stripDomainTag(key)]?.let { byGroup.merge(it, weight, Double::plus) } }
        return byGroup.maxByOrNull { it.value }?.key
    }

    /**
     * [ranked] reordered by weighted fair queueing over [weights]: the next place goes to the group
     * furthest below its share, and within a group the ranked order stands. Items of no group
     * ([groupOf] null or unweighted) form one more group sized to how many of them there are.
     */
    fun <T> interleave(
        ranked: List<T>,
        weights: Map<String, Double>,
        groupOf: (T) -> String?,
    ): List<T> {
        if (ranked.size < 2 || weights.size < 2) return ranked
        val queues = LinkedHashMap<String?, ArrayDeque<T>>()
        ranked.forEach { item -> queues.getOrPut(groupOf(item)?.takeIf { it in weights }) { ArrayDeque() }.addLast(item) }
        if (queues.keys.count { it != null } < 2) return ranked
        val grouped = ranked.size - (queues[null]?.size ?: 0)
        val known = queues.keys.filterNotNull().sumOf { weights.getValue(it) }
        val weightOf = { group: String? -> group?.let(weights::getValue) ?: (known * (ranked.size - grouped) / grouped) }
        val served = HashMap<String?, Int>()
        val out = ArrayList<T>(ranked.size)
        while (out.size < ranked.size) {
            val next = queues.filterValues { it.isNotEmpty() }.keys.minBy { group -> ((served[group] ?: 0) + 1) / weightOf(group) }
            out += queues.getValue(next).removeFirst()
            served.merge(next, 1, Int::plus)
        }
        return out
    }
}
