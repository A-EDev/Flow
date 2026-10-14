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

/** The category a channel posts in, and how sure [NeuroCategoryStore] is of it, from 0 to 1. */
internal data class ChannelCategory(
    val id: String,
    val confidence: Double,
)

/**
 * YouTube's video categories as a soft signal (#907). The player reads a category in English from
 * the video's microformat; the engine keeps a stable id for it, learns which categories each channel
 * posts in ([NeuroCategoryStore]) and how the viewer's watching splits across them
 * ([UserBrain.categoryAffinity]), and lowers a candidate a little when its category is foreign to
 * that mix.
 */
internal object NeuroCategories {
    private val IDS =
        mapOf(
            "film & animation" to "film",
            "autos & vehicles" to "autos",
            "music" to "music",
            "pets & animals" to "pets",
            "sports" to "sports",
            "travel & events" to "travel",
            "gaming" to "gaming",
            "people & blogs" to "people",
            "comedy" to "comedy",
            "entertainment" to "entertainment",
            "news & politics" to "news",
            "howto & style" to "howto",
            "education" to "education",
            "science & technology" to "science",
            "nonprofits & activism" to "nonprofits",
        )

    /** Uploads land in these by default, so they say less about a video than the others. */
    private val SPECIFICITY = mapOf("people" to 0.3, "entertainment" to 0.5)

    /** The most a category mismatch takes off a score. */
    const val MAX_PENALTY = 0.35

    /** Categorized strong signals before the viewer's mix is trusted. */
    const val MIN_SIGNALS = 20.0

    /** A category this share of the viewer's watching is familiar, however small beside the rest. */
    const val FAMILIAR_SHARE = 0.10

    /** Past this many signals every count halves, so the mix follows what the viewer watches now. */
    private const val MAX_SIGNALS = 400.0

    /** A category that names a theme of interests; the default ones say too little to group by. */
    fun isTheme(id: String): Boolean = id in IDS.values && id !in SPECIFICITY

    /** Share of the channel votes a category needs before a cluster counts as in it. */
    private const val CLUSTER_MAJORITY = 0.6

    /**
     * The category a cluster of [topics] is in: each channel whose topic profile overlaps them votes
     * for its own category ([categoryOf]), weighted by the overlap and its confidence. Null without a
     * clear majority.
     */
    fun clusterCategory(
        topics: Collection<String>,
        channelTopicProfiles: Map<String, Map<String, Double>>,
        categoryOf: (channelId: String) -> ChannelCategory?,
    ): String? {
        val votes = HashMap<String, Double>()
        channelTopicProfiles.forEach { (channelId, profile) ->
            val overlap = topics.sumOf { profile[it] ?: 0.0 }
            if (overlap <= 0.0) return@forEach
            val category = categoryOf(channelId) ?: return@forEach
            votes.merge(category.id, overlap * category.confidence, Double::plus)
        }
        val total = votes.values.sum()
        val top = votes.maxByOrNull { it.value } ?: return null
        return top.key.takeIf { top.value / total >= CLUSTER_MAJORITY }
    }

    /** The stable id of a category [name] as the microformat writes it; null for anything else. */
    fun idOf(name: String?): String? = name?.trim()?.lowercase()?.let(IDS::get)

    /** [affinity] after one strong signal on a video in [category]. */
    fun learn(
        affinity: Map<String, Double>,
        category: String,
    ): Map<String, Double> {
        val counted = affinity + (category to (affinity[category] ?: 0.0) + 1.0)
        return if (counted.values.sum() > MAX_SIGNALS) counted.mapValues { it.value / 2 } else counted
    }

    /** The score factor for a candidate in [category], from [MAX_PENALTY] off up to none. */
    fun factor(
        affinity: Map<String, Double>,
        category: ChannelCategory,
    ): Double {
        val total = affinity.values.sum()
        if (total < MIN_SIGNALS) return 1.0
        val share = (affinity[category.id] ?: 0.0) / total
        val mismatch = 1.0 - (share / FAMILIAR_SHARE).coerceAtMost(1.0)
        return 1.0 - MAX_PENALTY * category.confidence.coerceIn(0.0, 1.0) * mismatch * (SPECIFICITY[category.id] ?: 1.0)
    }
}
