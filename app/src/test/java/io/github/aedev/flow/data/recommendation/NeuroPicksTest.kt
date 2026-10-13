/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.eval.NeuroLearningBenchmark
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** Picks are keyed the way titles key them, so Your taste can show and undo them (#907). */
class NeuroPicksTest {
    @Test
    fun `onboarding picks are keys titles produce, spelled as chosen, and not linked to each other`() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine()
            engine.completeOnboarding(setOf("Machine Learning", "Lo-Fi", "Cooking"))
            val brain = engine.getBrainSnapshot()
            engine.shutdown()

            assertThat(brain.globalVector.topics.keys).containsAtLeast("machine learn", "lofi")
            assertThat(NeuroDisplayForms.label(brain.displayForms, "lofi")).isEqualTo("Lo-Fi")
            assertThat(brain.topicAffinities).isEmpty()
        }

    @Test
    fun `stop boosting removes a pick by the key Your taste shows`() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine()
            engine.completeOnboarding(setOf("Web Development", "Anime"))
            engine.removePreferredTopic("web development")
            val brain = engine.getBrainSnapshot()
            engine.shutdown()

            assertThat(brain.preferredTopics).containsExactly("Anime")
        }

    @Test
    fun `boosting never lowers a weight`() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine(UserBrain(globalVector = ContentVector(topics = mapOf("chess" to 0.8))))
            engine.addPreferredTopic("chess")
            val weight =
                engine
                    .getBrainSnapshot()
                    .globalVector.topics
                    .getValue("chess")
            engine.shutdown()

            assertThat(weight).isEqualTo(0.8)
        }

    @Test
    fun `picks made before spellings were kept read as chosen`() =
        runBlocking<Unit> {
            val engine = NeuroLearningBenchmark.engine(UserBrain(preferredTopics = setOf("R&B", "K-Pop", "Guitar", "Call of Duty")))
            val forms = engine.getBrainSnapshot().displayForms
            engine.shutdown()

            assertThat(listOf("rnb", "kpop", "guitar", "call of duty").map { NeuroDisplayForms.label(forms, it) })
                .containsExactly("R&B", "K-Pop", "Guitar", "Call of Duty")
        }
}
