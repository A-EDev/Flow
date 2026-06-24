/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeuroVectorMathTest {

    private fun vec(vararg topics: Pair<String, Double>) =
        ContentVector(topics = topics.toMap())

    @Test
    fun `identical topic vectors score near 1`() {
        val sim = NeuroVectorMath.calculateCosineSimilarity(
            vec("python" to 1.0), vec("python" to 1.0)
        )
        assertThat(sim).isWithin(1e-9).of(1.0)
    }

    @Test
    fun `no topic intersection yields a damped scalar-only score`() {
        // Scalars are equal (0.5) so the raw scalar score is 0.30; with no topic
        // overlap it is damped (I-11a) so off-topic content can be floored.
        val sim = NeuroVectorMath.calculateCosineSimilarity(
            vec("python" to 1.0), vec("cooking" to 1.0)
        )
        assertThat(sim).isWithin(1e-9).of(0.30 * NeuroVectorMath.SCALAR_ONLY_DAMP)
    }

    @Test
    fun `off-topic similarity now falls below the moderate relevance floor`() {
        val sim = NeuroVectorMath.calculateCosineSimilarity(
            vec("python" to 1.0), vec("cooking" to 1.0)
        )
        assertThat(sim).isLessThan(NeuroScoring.RELEVANCE_FLOOR_MODERATE_THRESHOLD)
    }

    @Test
    fun `tagged and untagged topics partial-match below an exact match`() {
        val partial = NeuroVectorMath.calculateCosineSimilarity(
            vec("metal" to 1.0), vec("metal:music" to 1.0)
        )
        assertThat(partial).isGreaterThan(0.30)   // beats scalar-only
        assertThat(partial).isLessThan(1.0)        // but below an exact match
    }

    @Test
    fun `established topics decay slower than emerging ones when off-target`() {
        val v = ContentVector(topics = mapOf("established" to 0.5, "emerging" to 0.05))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("z" to 1.0)), 0.1)
        val establishedRatio = out.topics.getValue("established") / 0.5
        val emergingRatio = out.topics.getValue("emerging") / 0.05
        assertThat(establishedRatio).isGreaterThan(emergingRatio)
    }

    @Test
    fun `positive learning moves an absent topic toward the target`() {
        val out = NeuroVectorMath.adjustVector(
            ContentVector(), ContentVector(topics = mapOf("newtopic" to 1.0)), 0.2
        )
        assertThat(out.topics["newtopic"]).isNotNull()
        assertThat(out.topics.getValue("newtopic")).isGreaterThan(0.0)
    }

    @Test
    fun `tiny off-target topics are pruned below threshold`() {
        val v = ContentVector(topics = mapOf("keep" to 0.5, "drop" to 0.030))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("z" to 1.0)), 0.1)
        assertThat(out.topics).containsKey("keep")
        assertThat(out.topics).doesNotContainKey("drop")
    }

    @Test
    fun `negative learning never increases a target topic`() {
        val v = ContentVector(topics = mapOf("dislike" to 0.6))
        val out = NeuroVectorMath.adjustVector(v, ContentVector(topics = mapOf("dislike" to 1.0)), -0.4)
        assertThat(out.topics.getValue("dislike")).isLessThan(0.6)
    }

    @Test
    fun `title similarity is jaccard over token sets`() {
        val sim = NeuroVectorMath.calculateTitleSimilarity(
            setOf("a", "b", "c"), setOf("b", "c", "d")
        )
        assertThat(sim).isWithin(1e-9).of(0.5)
    }
}
