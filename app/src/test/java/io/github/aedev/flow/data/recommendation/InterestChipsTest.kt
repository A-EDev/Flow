/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InterestChipsTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun cluster(
        rep: String,
        mass: Double,
        topics: List<String> = listOf(rep, "$rep extra"),
    ) = NeuroClusters.TopicCluster(rep, topics, mass)

    private val clusters =
        listOf(cluster("claude", 1.6), cluster("android", 0.7), cluster("gym", 0.5), cluster("horror", 0.2), cluster("learn", 0.2))

    private fun groups(
        clusters: List<NeuroClusters.TopicCluster>,
        categories: Map<String, String> = emptyMap(),
    ) = InterestChips.groups(clusters, { true }) { categories[it.representative] }

    @Test
    fun `chips are the heaviest clusters with a real share of the profile`() {
        val chosen = InterestChips.stable(InterestChipSet(), groups(clusters), now)

        assertThat(chosen.masses.keys).containsExactly("claude", "android", "gym").inOrder()
    }

    @Test
    fun `the chip set holds for a day while masses stay within thirty percent`() {
        val first = InterestChips.stable(InterestChipSet(), groups(clusters), now)
        val drifted = clusters.map { if (it.representative == "android") it.copy(mass = 0.85) else it } + cluster("new", 3.0)

        assertThat(InterestChips.stable(first, groups(drifted), now + 5 * hour)).isEqualTo(first)
    }

    @Test
    fun `the chip set is recomputed after a day or a large mass change`() {
        val first = InterestChips.stable(InterestChipSet(), groups(clusters), now)
        val shifted = clusters.map { if (it.representative == "gym") it.copy(mass = 1.2) else it }

        assertThat(InterestChips.stable(first, groups(shifted), now + hour).computedAt).isEqualTo(now + hour)
        assertThat(InterestChips.stable(first, groups(clusters), now + 25 * hour).computedAt).isEqualTo(now + 25 * hour)
    }

    @Test
    fun `clusters in one category become a theme, which frees a place in the row`() {
        val gamer =
            listOf(
                cluster("minecraft", 1.0),
                cluster("call of duty", 0.9),
                cluster("fortnite", 0.8),
                cluster("lofi", 0.7),
                cluster("cook", 0.6),
                cluster("vlog", 0.5),
            )
        val categories =
            mapOf("minecraft" to "gaming", "call of duty" to "gaming", "fortnite" to "gaming", "lofi" to "music", "vlog" to "people")

        val set = InterestChips.stable(InterestChipSet(), groups(gamer, categories), now)
        val chips = InterestChips.chips(set, groups(gamer, categories))

        assertThat(set.masses.keys).containsExactly("theme:gaming", "lofi", "cook", "vlog").inOrder()
        val theme = chips.first()
        assertThat(theme.category).isEqualTo("gaming")
        assertThat(theme.parts.map { it.representative }).containsExactly("minecraft", "call of duty", "fortnite").inOrder()
        assertThat(chips.drop(1).map { it.category }).containsExactly(null, null, null)
    }

    @Test
    fun `one cluster of distinct interests reads as its theme, one of related phrases as its topic`() {
        val games = cluster("minecraft", 1.0, listOf("minecraft", "minecraft redstone", "call of duty", "fortnite"))
        val anime = cluster("anime", 0.8, listOf("anime", "anime openings", "anime edit"))
        val categories = mapOf("minecraft" to "gaming", "anime" to "film")

        val keys = groups(listOf(games, anime), categories).map { it.key }

        assertThat(keys).containsExactly("theme:gaming", "anime").inOrder()
        assertThat(
            InterestChips.distinctInterests(games),
        ).containsExactly(listOf("minecraft", "minecraft redstone"), listOf("call of duty"), listOf("fortnite"))
    }

    @Test
    fun `a chip reads as the topic of its cluster the viewer watched most`() {
        val tech = cluster("smartphones", 2.0, listOf("smartphones", "laptops", "ai"))
        val set = InterestChips.stable(InterestChipSet(), groups(listOf(tech, cluster("gym", 0.5))), now)

        val chips = InterestChips.chips(set, groups(listOf(tech, cluster("gym", 0.5)))) { if (it == "ai") 12 else 1 }

        assertThat(chips.first().representative).isEqualTo("smartphones")
        assertThat(chips.first().label).isEqualTo("Ai")
        assertThat(InterestChips.chips(set, groups(listOf(tech, cluster("gym", 0.5)))).first().label).isEqualTo("Smartphones")
    }

    @Test
    fun `a chip is never named after a word every subject uses`() {
        val tech = cluster("buy", 2.0, listOf("buy", "laptop", "android"))
        val set = InterestChips.stable(InterestChipSet(), groups(listOf(tech, cluster("gym", 0.5))), now)

        val chips =
            InterestChips.chips(set, groups(listOf(tech, cluster("gym", 0.5))), specificity = { if (it == "buy") 0.1 else 1.0 }) {
                if (it == "buy") {
                    12
                } else if (it == "laptop") {
                    3
                } else {
                    1
                }
            }

        assertThat(chips.first().label).isEqualTo("Laptop")
    }

    @Test
    fun `a default category and a lone cluster stay plain interests`() {
        val categories = mapOf("claude" to "people", "android" to "people", "gym" to "sports")

        assertThat(groups(clusters, categories).map { it.key }).containsExactly("claude", "android", "gym", "horror", "learn").inOrder()
    }

    @Test
    fun `a cluster's category is the clear majority of the channels that teach its topics`() {
        val profiles =
            mapOf(
                "ch-a" to mapOf("minecraft" to 0.6),
                "ch-b" to mapOf("minecraft" to 0.4, "redstone" to 0.3),
                "ch-c" to mapOf("minecraft" to 0.1),
                "ch-d" to mapOf("cooking" to 0.9),
            )
        val known = mapOf("ch-a" to "gaming", "ch-b" to "gaming", "ch-c" to "education", "ch-d" to "howto")
        val categoryOf = { id: String -> known[id]?.let { ChannelCategory(it, 0.8) } }

        assertThat(NeuroCategories.clusterCategory(listOf("minecraft", "redstone"), profiles, categoryOf)).isEqualTo("gaming")
        assertThat(NeuroCategories.clusterCategory(listOf("cooking", "minecraft"), profiles, categoryOf)).isNull()
        assertThat(NeuroCategories.clusterCategory(listOf("knitting"), profiles, categoryOf)).isNull()
    }

    @Test
    fun `labels are written as titles write them, else title-cased`() {
        assertThat(NeuroDisplayForms.label(emptyMap(), "claude 5.5")).isEqualTo("Claude 5.5")
        assertThat(NeuroDisplayForms.label(emptyMap(), "metal:music")).isEqualTo("Metal")
        assertThat(NeuroDisplayForms.label(mapOf("mma" to DisplayForm("MMA")), "mma")).isEqualTo("MMA")
    }
}
