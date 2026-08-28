/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeuroChannelKnowledgeTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun `creator tags become weighted profile topics`() {
        val profile =
            NeuroChannelKnowledge.profileFromChannelTags(
                tags = listOf("claude", "coding agents", "ai tools"),
                description = "Deep dives into claude and coding agents.",
                tokenizer = tokenizer,
            )
        assertThat(profile).containsKey("claude")
        assertThat(profile.keys.any { it.contains("agent") }).isTrue()
        // Repeated across tags+description → weighted above single mentions.
        assertThat(profile.getValue("claude")).isAtLeast(profile.values.min())
        assertThat(profile.values.max()).isAtMost(NeuroChannelKnowledge.TAG_SEED_WEIGHT)
    }

    @Test
    fun `noise tags are filtered`() {
        val profile =
            NeuroChannelKnowledge.profileFromChannelTags(
                tags = listOf("2026", "use", "right", "mma"),
                description = null,
                tokenizer = tokenizer,
            )
        assertThat(profile.keys).containsExactly("mma")
    }

    @Test
    fun `upload titles need repetition to count as channel identity`() {
        val profile =
            NeuroChannelKnowledge.profileFromUploadTitles(
                titles =
                    listOf(
                        "McLaren 720S engine swap part 1",
                        "McLaren P1 vs Ferrari drag race",
                        "Why the McLaren F1 is special",
                        "My dog ate my homework",
                    ),
                tokenizer = tokenizer,
            )
        assertThat(profile).containsKey("mclaren")
        // One-off words from a single title are not channel identity.
        assertThat(profile).doesNotContainKey("dog")
        assertThat(profile.values.max()).isAtMost(NeuroChannelKnowledge.PASSIVE_MAX_WEIGHT)
    }

    @Test
    fun `merge never lowers learned weights and respects the topic cap`() {
        val existing = mapOf("claude" to 0.6, "coding" to 0.2)
        val merged =
            NeuroChannelKnowledge.mergeProfile(
                existing,
                mapOf("claude" to 0.35, "cursor" to 0.3),
            )
        assertThat(merged.getValue("claude")).isEqualTo(0.6)
        assertThat(merged.getValue("cursor")).isEqualTo(0.3)
        assertThat(merged.size).isAtMost(NeuroScoring.CHANNEL_PROFILE_MAX_TOPICS)
    }

    @Test
    fun `progressive seed pick spreads one per community before doubling up`() {
        val seeds =
            listOf(
                SeedRank("dev1", "android", 10.0),
                SeedRank("dev2", "android", 9.0),
                SeedRank("dev3", "android", 8.0),
                SeedRank("mma1", "mma", 5.0),
                SeedRank("anime1", "anime", 3.0),
            )
        val picked = NeuroScoring.pickDiverseSeeds(seeds, maxSeeds = 4, maxPerCluster = 2)
        // Pass 1 covers every community; only then does android get its second seed.
        assertThat(picked).containsExactly("dev1", "mma1", "anime1", "dev2").inOrder()
    }
}
