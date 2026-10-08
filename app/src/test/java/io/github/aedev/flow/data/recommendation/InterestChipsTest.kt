/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
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

    @Test
    fun `chips are the heaviest clusters with a real share of the profile`() {
        val chosen = InterestChips.choose(clusters) { true }

        assertThat(chosen.map { it.representative }).containsExactly("claude", "android", "gym").inOrder()
    }

    @Test
    fun `the chip set holds for a day while masses stay within thirty percent`() {
        val first = InterestChips.stable(InterestChipSet(), clusters, { true }, now)
        val drifted = clusters.map { if (it.representative == "android") it.copy(mass = 0.85) else it } + cluster("new", 3.0)

        assertThat(InterestChips.stable(first, drifted, { true }, now + 5 * hour)).isEqualTo(first)
    }

    @Test
    fun `the chip set is recomputed after a day or a large mass change`() {
        val first = InterestChips.stable(InterestChipSet(), clusters, { true }, now)
        val shifted = clusters.map { if (it.representative == "gym") it.copy(mass = 1.2) else it }

        assertThat(InterestChips.stable(first, shifted, { true }, now + hour).computedAt).isEqualTo(now + hour)
        assertThat(InterestChips.stable(first, clusters, { true }, now + 25 * hour).computedAt).isEqualTo(now + 25 * hour)
    }

    @Test
    fun `labels are title-cased phrases`() {
        assertThat(InterestChips.label("claude 5.5")).isEqualTo("Claude 5.5")
        assertThat(InterestChips.label("metal:music")).isEqualTo("Metal")
    }
}
