/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Random

/** The per-rank indexes must score exactly as the per-candidate code did. */
class NeuroScoringParityTest {
    private val words =
        listOf("guitar", "metal", "rock", "comedy", "recipe", "pixel", "review", "drift", "phonk", "chess", "anime", "linux")
    private val domains = listOf("", ":music", ":craft", ":nature")

    private fun vector(
        random: Random,
        size: Int,
    ): ContentVector {
        val topics = LinkedHashMap<String, Double>()
        while (topics.size < size) {
            val word = words[random.nextInt(words.size)]
            val key =
                if (random.nextInt(4) ==
                    0
                ) {
                    "$word ${words[random.nextInt(words.size)]}"
                } else {
                    word + domains[random.nextInt(domains.size)]
                }
            topics[key] = random.nextDouble()
        }
        return ContentVector(topics, random.nextDouble(), random.nextDouble(), random.nextDouble())
    }

    @Test
    fun `prepared cosine equals the plain cosine`() {
        val random = Random(7)
        repeat(500) {
            val user = vector(random, 1 + random.nextInt(30))
            val content = vector(random, random.nextInt(25))
            val prepared = NeuroVectorMath.PreparedVector(user)

            assertThat(NeuroVectorMath.calculateCosineSimilarity(prepared, content))
                .isEqualTo(NeuroVectorMath.calculateCosineSimilarity(user, content))
        }
    }

    @Test
    fun `affinity boost matches the pairwise lookup`() {
        val random = Random(11)
        repeat(200) {
            val affinities = HashMap<String, Double>()
            repeat(40) {
                affinities[NeuroScoring.makeAffinityKey(words[random.nextInt(words.size)], words[random.nextInt(words.size)])] =
                    random.nextDouble()
            }
            val brain = UserBrain(totalInteractions = 500, globalVector = vector(random, 20), topicAffinities = affinities)
            val videoVector = vector(random, 8)
            val topics =
                videoVector.topics.keys
                    .map(NeuroScoring::stripDomainTag)
                    .distinct()
            var expected = 0.0
            for (i in topics.indices) {
                for (j in i + 1 until topics.size) {
                    expected +=
                        (affinities[NeuroScoring.makeAffinityKey(topics[i], topics[j])] ?: 0.0) * NeuroScoring.AFFINITY_BOOST_PER_PAIR
                }
            }

            assertThat(NeuroScoring.affinityBoost(videoVector, params(brain)))
                .isEqualTo(expected.coerceAtMost(NeuroScoring.AFFINITY_MAX_BOOST_PER_VIDEO))
        }
    }

    private fun params(brain: UserBrain) =
        ScoringParams(
            brain = brain,
            userSubs = emptySet(),
            timeContextVector = brain.globalVector,
            wPersonality = 0.4,
            wContext = 0.4,
            wNovelty = 0.2,
            isColdStart = false,
            isOnboarding = false,
            onboardingWarmup = 0.5,
            lemmatizedPreferred = emptySet(),
            sessionTopics = emptyList(),
            sessionVideoCount = 0,
            impressions = emptyMap(),
            watchHistory = emptyMap(),
            recentInteractions = emptyList(),
            candidatePoolSize = 100,
            now = 1_800_000_000_000L,
        )
}
