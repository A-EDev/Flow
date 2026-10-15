/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** What discovery sends to YouTube (#907). */
class NeuroDiscoveryQueryTest {
    private val discovery = NeuroDiscovery(NeuroTopicCatalog.TOPIC_CATEGORIES, NeuroTokenizer())

    @Test
    fun `a domain tag never reaches the search text`() {
        assertThat(discovery.sanitizeQuery("metal:music live set")).isEqualTo("metal live set")
    }

    @Test
    fun `exploration searches a subject, never a verb or a word that needs a qualifier`() {
        val tokenizer = NeuroTokenizer()
        val endgame = tokenizer.keysOf("endgame").single()
        val claudeCode = tokenizer.keysOf("claude code").single()
        val brain =
            UserBrain(
                globalVector = ContentVector(topics = mapOf("ai" to 0.4)),
                topicAffinities =
                    mapOf(
                        "ai|build" to 0.3,
                        "ai|test" to 0.3,
                        NeuroScoring.makeAffinityKey("ai", claudeCode) to 0.3,
                        NeuroScoring.makeAffinityKey("ai", endgame) to 0.3,
                    ),
            )

        val queries = discovery.explorationQueries(brain, 6).map { it.lowercase() }

        assertThat(queries.none { it == "build" || it.startsWith("build ") || it == "test" || it.startsWith("test ") }).isTrue()
        assertThat(queries.any { claudeCode in it }).isTrue()
        assertThat(queries.any { endgame in it }).isTrue()
    }

    @Test
    fun `every confidence falls in a tier`() {
        val tiers = listOf(0.0, 0.39, 0.4, 0.55 + 0.09 + 0.05, 0.69, 0.7, 1.0).map(discovery::confidenceTier)

        assertThat(tiers).containsExactly(2, 2, 1, 1, 1, 0, 0).inOrder()
    }

    @Test
    fun `a word met mostly inside a phrase is searched as the phrase`() {
        val brain =
            UserBrain(
                globalVector =
                    ContentVector(
                        topics =
                            mapOf(
                                "elit" to 0.084,
                                "elit gen" to 0.086,
                                "pixel" to 0.2,
                                "pixel camera" to 0.05,
                            ),
                    ),
            )

        assertThat(discovery.enclosingPhrase("elit", brain)).isEqualTo("elit gen")
        assertThat(discovery.enclosingPhrase("pixel", brain)).isNull()
    }
}
