/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.abs

/** The interest clusters Home last showed as chips, kept so the row does not reshuffle every load. */
data class InterestChipSet(
    /** Cluster representative to its mass when chosen, in chip order. */
    val masses: Map<String, Double> = emptyMap(),
    val computedAt: Long = 0L,
)

/** One interest chip: the cluster it opens and the topics that define it. */
data class InterestChip(
    val representative: String,
    val label: String,
    val topics: List<String>,
)

internal object InterestChips {
    const val MAX_CHIPS = 4
    const val MIN_MASS_SHARE = 0.08
    const val RECOMPUTE_AFTER_MS = 24L * 60L * 60L * 1000L
    const val MASS_CHANGE = 0.30

    /** The heaviest clusters with enough of the viewer's mass and some evidence behind them. */
    fun choose(
        clusters: List<NeuroClusters.TopicCluster>,
        hasEvidence: (NeuroClusters.TopicCluster) -> Boolean,
    ): List<NeuroClusters.TopicCluster> {
        val total = clusters.sumOf { it.mass }.takeIf { it > 0.0 } ?: return emptyList()
        return clusters
            .filter { it.mass / total >= MIN_MASS_SHARE && hasEvidence(it) }
            .sortedByDescending { it.mass }
            .take(MAX_CHIPS)
    }

    /**
     * The chip set to show: [previous] while it is under a day old and none of its clusters moved
     * more than 30 % in mass or disappeared; otherwise a fresh [choose].
     */
    fun stable(
        previous: InterestChipSet,
        clusters: List<NeuroClusters.TopicCluster>,
        hasEvidence: (NeuroClusters.TopicCluster) -> Boolean,
        now: Long,
    ): InterestChipSet {
        val byRep = clusters.associateBy { it.representative }
        val fresh = now - previous.computedAt in 0 until RECOMPUTE_AFTER_MS
        val steady =
            previous.masses.isNotEmpty() &&
                previous.masses.all { (rep, mass) ->
                    val current = byRep[rep]?.mass ?: return@all false
                    mass > 0.0 && abs(current - mass) / mass <= MASS_CHANGE
                }
        if (fresh && steady) return previous
        val chosen = choose(clusters, hasEvidence)
        return InterestChipSet(chosen.associate { it.representative to it.mass }, now)
    }

    fun chips(
        set: InterestChipSet,
        clusters: List<NeuroClusters.TopicCluster>,
    ): List<InterestChip> {
        val byRep = clusters.associateBy { it.representative }
        return set.masses.keys.mapNotNull { rep ->
            byRep[rep]?.let { InterestChip(rep, label(rep), it.topics) }
        }
    }

    /** "claude 5.5" reads "Claude 5.5" on the chip. */
    fun label(representative: String): String =
        NeuroScoring
            .stripDomainTag(representative)
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
}
