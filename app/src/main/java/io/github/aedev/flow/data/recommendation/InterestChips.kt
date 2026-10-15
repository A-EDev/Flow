/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import kotlin.math.abs

/** The interest clusters Home last showed as chips, kept so the row does not reshuffle every load. */
data class InterestChipSet(
    /** Cluster representative to its mass when chosen, in chip order. */
    val masses: Map<String, Double> = emptyMap(),
    val computedAt: Long = 0L,
)

/**
 * One interest chip: the cluster it opens and the topics that define it. A theme chip has a YouTube
 * [category], opens several [clusters] and searches each of its [parts] in turn.
 */
data class InterestChip(
    val representative: String,
    val label: String,
    val topics: List<String>,
    val category: String? = null,
    val parts: List<InterestChip> = emptyList(),
    val clusters: List<String> = listOf(representative),
)

internal object InterestChips {
    const val MAX_CHIPS = 4

    // Below this a word every subject uses names no chip ([NeuroSpecificity]).
    const val LABEL_MIN_SPECIFICITY = 0.5
    const val MIN_MASS_SHARE = 0.08
    const val RECOMPUTE_AFTER_MS = 24L * 60L * 60L * 1000L
    const val MASS_CHANGE = 0.30
    const val THEME_PREFIX = "theme:"

    /** Distinct interests one cluster needs to read as a theme rather than by its strongest topic. */
    const val THEME_MIN_INTERESTS = 3

    /** A chip candidate: one cluster, or a theme of clusters in the same [category]. Only [eligible] ones are chosen anew. */
    data class Group(
        val key: String,
        val members: List<NeuroClusters.TopicCluster>,
        val category: String? = null,
        val eligible: Boolean = true,
    ) {
        val mass: Double get() = members.sumOf { it.mass }
    }

    /**
     * Every cluster as a chip candidate, heaviest first; eligible are those with enough of the
     * viewer's mass and some evidence behind them. Eligible clusters in one theme category
     * ([categoryOf]) become a single theme when there are two of them, or one holding
     * [THEME_MIN_INTERESTS] distinct interests: "Gaming" then reads truer than "Minecraft" for a
     * cluster of three games, and one broad taste leaves room in the row for the others.
     */
    fun groups(
        clusters: List<NeuroClusters.TopicCluster>,
        hasEvidence: (NeuroClusters.TopicCluster) -> Boolean,
        categoryOf: (NeuroClusters.TopicCluster) -> String? = { null },
    ): List<Group> {
        val total = clusters.sumOf { it.mass }.takeIf { it > 0.0 } ?: return emptyList()
        val eligible = clusters.filter { it.mass / total >= MIN_MASS_SHARE && hasEvidence(it) }
        val themes =
            eligible
                .groupBy { cluster -> categoryOf(cluster)?.takeIf(NeuroCategories::isTheme) }
                .filter { (category, members) ->
                    category != null && (members.size >= 2 || members.sumOf { distinctInterests(it).size } >= THEME_MIN_INTERESTS)
                }.map { (category, members) -> Group(THEME_PREFIX + category, members, category) }
        val themed = themes.flatMapTo(HashSet()) { it.members }
        val singles = clusters.filter { it !in themed }.map { Group(it.representative, listOf(it), eligible = it in eligible) }
        return (themes + singles).sortedByDescending { it.mass }
    }

    /**
     * The chip set to show: [previous] while it is under a day old and none of its groups moved
     * more than 30 % in mass or disappeared; otherwise the heaviest [MAX_CHIPS] eligible [groups].
     */
    fun stable(
        previous: InterestChipSet,
        groups: List<Group>,
        now: Long,
    ): InterestChipSet {
        val byKey = groups.associateBy { it.key }
        val fresh = now - previous.computedAt in 0 until RECOMPUTE_AFTER_MS
        val steady =
            previous.masses.isNotEmpty() &&
                previous.masses.all { (key, mass) ->
                    val current = byKey[key]?.mass ?: return@all false
                    mass > 0.0 && abs(current - mass) / mass <= MASS_CHANGE
                }
        if (fresh && steady) return previous
        return InterestChipSet(groups.filter { it.eligible }.take(MAX_CHIPS).associate { it.key to it.mass }, now)
    }

    /** A cluster's topics split into interests that share no word: "minecraft redstone" joins "minecraft", "fortnite" stands apart. */
    fun distinctInterests(cluster: NeuroClusters.TopicCluster): List<List<String>> {
        fun words(topic: String) = NeuroScoring.stripDomainTag(topic).split(' ')
        val interests = mutableListOf<MutableList<String>>()
        cluster.topics.forEach { topic ->
            val own = words(topic)
            interests.firstOrNull { interest -> interest.any { other -> words(other).any(own::contains) } }?.add(topic)
                ?: interests.add(mutableListOf(topic))
        }
        return interests
    }

    /**
     * The chips of [set]. A chip reads as the topic of its cluster the viewer has watched most
     * ([evidenceOf]): nine picks of equal weight would otherwise name a tech cluster after whichever
     * sorts first ("Smartphones" for someone who watches AI).
     */
    fun chips(
        set: InterestChipSet,
        groups: List<Group>,
        forms: Map<String, DisplayForm> = emptyMap(),
        specificity: (String) -> Double = { 1.0 },
        evidenceOf: (String) -> Int = { 0 },
    ): List<InterestChip> {
        val byKey = groups.associateBy { it.key }

        // A chip is named after a word of one subject: "Buy" or "Top" names no subject (#907).
        fun nameable(topics: List<String>) = topics.filter { specificity(it) >= LABEL_MIN_SPECIFICITY }.ifEmpty { topics }

        fun chipOf(cluster: NeuroClusters.TopicCluster): InterestChip {
            val candidates = nameable(cluster.topics)
            val named =
                candidates.maxByOrNull(evidenceOf)?.takeIf { evidenceOf(it) > 0 }
                    ?: cluster.representative.takeIf { it in candidates }
                    ?: candidates.first()
            return InterestChip(cluster.representative, NeuroDisplayForms.label(forms, named), cluster.topics)
        }
        return set.masses.keys.mapNotNull { key ->
            val group = byKey[key] ?: return@mapNotNull null
            if (group.category == null) {
                chipOf(group.members.single())
            } else {
                val parts =
                    group.members.flatMap(::distinctInterests).map { topics ->
                        InterestChip(topics.first(), NeuroDisplayForms.label(forms, nameable(topics).first()), topics)
                    }
                val topics = group.members.flatMap { it.topics }
                InterestChip(key, parts.first().label, topics, group.category, parts, group.members.map { it.representative })
            }
        }
    }
}
