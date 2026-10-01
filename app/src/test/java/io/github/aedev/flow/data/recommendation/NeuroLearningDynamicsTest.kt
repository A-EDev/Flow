/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** #907: a weak event must not erase what strong events taught. */
class NeuroLearningDynamicsTest {
    private val tokenizer = NeuroTokenizer()
    private val comedy = ContentVector(topics = mapOf("comedy" to 0.08, "stand-up comedy" to 0.25, "cooking" to 0.4))
    private val short = ContentVector(topics = mapOf("prank" to 1.0))

    @Test
    fun `a Short decays the vector a hundred times less than a full watch`() {
        val shortRate = 0.12 * NeuroScoring.SHORTS_LEARNING_PENALTY
        var vector = comedy
        repeat(100) { vector = NeuroVectorMath.adjustVector(vector, short, shortRate, NeuroVectorMath.decayStrength(shortRate)) }

        assertThat(vector.topics.getValue("comedy")).isGreaterThan(0.075)
        assertThat(vector.topics.getValue("cooking")).isGreaterThan(0.39)
    }

    @Test
    fun `full-strength events keep the tiered decay`() {
        val decayed = NeuroVectorMath.adjustVector(comedy, short, 0.15, NeuroVectorMath.decayStrength(0.15))

        assertThat(decayed.topics.getValue("comedy")).isWithin(1e-9).of(0.08 * NeuroVectorMath.EMERGING_DECAY_RATE)
        assertThat(decayed.topics.getValue("cooking")).isWithin(1e-9).of(0.4 * NeuroVectorMath.ESTABLISHED_DECAY_RATE)
    }

    @Test
    fun `decay strength follows the event rate`() {
        assertThat(NeuroVectorMath.decayStrength(0.03)).isWithin(1e-9).of(0.2)
        assertThat(NeuroVectorMath.decayStrength(0.30)).isEqualTo(1.0)
        assertThat(NeuroVectorMath.decayStrength(-0.4)).isEqualTo(1.0)
    }

    @Test
    fun `the global vector keeps its strongest topics and every chosen interest`() {
        val topics = (0 until 10).associate { "t$it" to it / 10.0 }
        val capped = NeuroVectorMath.capTopics(ContentVector(topics = topics), max = 3, protected = setOf("t0"))

        assertThat(capped.topics.keys).containsExactly("t9", "t8", "t7", "t0")
    }

    @Test
    fun `a time bucket earns its weight with events`() {
        val brain = UserBrain(timeBucketCounts = mapOf(TimeBucket.WEEKDAY_NIGHT to 3, TimeBucket.WEEKEND_NIGHT to 90))

        assertThat(NeuroScoring.timeBucketConfidence(brain, TimeBucket.WEEKDAY_NIGHT)).isWithin(1e-9).of(0.1)
        assertThat(NeuroScoring.timeBucketConfidence(brain, TimeBucket.WEEKEND_NIGHT)).isEqualTo(1.0)
        assertThat(NeuroScoring.timeBucketConfidence(brain, TimeBucket.WEEKDAY_MORNING)).isEqualTo(0.0)
    }

    @Test
    fun `v17 rebuilds a vector decay emptied, without blocked topics or filler`() {
        val brain =
            UserBrain(
                schemaVersion = 16,
                globalVector = ContentVector(topics = mapOf("zoo" to 0.06, "these" to 0.05, "invention heated" to 0.03)),
                blockedTopics = setOf("zoo", "resident evil"),
                channelScores = mapOf("chCode" to 0.8, "chGym" to 0.7, "chGames" to 0.7),
                channelTopicProfiles =
                    mapOf(
                        "chCode" to mapOf("kotlin" to 0.6, "android" to 0.5),
                        "chGym" to mapOf("gym" to 0.6, "chest" to 0.4),
                        "chGames" to mapOf("resident" to 0.5, "evil" to 0.5, "horror" to 0.4),
                    ),
                topicAffinities = mapOf("android|these" to 0.2, "android|kotlin" to 0.3),
            )

        val updated = NeuroMaintenance.runIfNeeded(brain, tokenizer)

        assertThat(updated.globalVector.topics.keys).containsAtLeast("kotlin", "android", "gym", "horror")
        assertThat(updated.globalVector.topics.keys).containsNoneOf("zoo", "these", "resident", "evil")
        assertThat(updated.topicAffinities.keys).doesNotContain("android|these")
    }

    @Test
    fun `v17 leaves a healthy vector to what it learned`() {
        val healthy = (0 until 8).associate { "topic$it" to 0.3 }
        val brain =
            UserBrain(
                schemaVersion = 16,
                globalVector = ContentVector(topics = healthy),
                channelTopicProfiles = mapOf("ch" to mapOf("kotlin" to 0.6)),
            )

        assertThat(NeuroMaintenance.runIfNeeded(brain, tokenizer).globalVector.topics).isEqualTo(healthy)
    }

    @Test
    fun `v17 estimates bucket counts from the topics a bucket holds`() {
        val brain =
            UserBrain(
                schemaVersion = 16,
                timeVectors =
                    TimeBucket.entries.associateWith { ContentVector() } +
                        mapOf(
                            TimeBucket.WEEKDAY_NIGHT to ContentVector(topics = mapOf("guitar" to 0.2, "guitar playalong" to 0.1)),
                            TimeBucket.WEEKEND_EVENING to ContentVector(topics = (0 until 80).associate { "t$it" to 0.1 }),
                        ),
            )

        val updated = NeuroMaintenance.runIfNeeded(brain, tokenizer)

        assertThat(updated.timeBucketCounts)
            .containsExactly(TimeBucket.WEEKDAY_NIGHT, 2, TimeBucket.WEEKEND_EVENING, NeuroScoring.TIME_BUCKET_CONFIDENT_EVENTS)
    }
}
