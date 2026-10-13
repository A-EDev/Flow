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
    fun `every confidence falls in a tier`() {
        val tiers = listOf(0.0, 0.39, 0.4, 0.55 + 0.09 + 0.05, 0.69, 0.7, 1.0).map(discovery::confidenceTier)

        assertThat(tiers).containsExactly(2, 2, 1, 1, 1, 0, 0).inOrder()
    }
}
