/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Writes app/build/reports/neuro-benchmark/learning.txt. Compare old and new code back to back:
 * ranking jitter is unseeded, so shares move by a few points between runs. The floors hold the #907
 * fixes with margin; the numbers before them are in the PR that added them.
 */
class NeuroLearningBenchmarkTest {
    @Test
    fun `learning benchmark - report and regression floors`() {
        val retention = NeuroLearningBenchmark.retention()
        val rejection = NeuroLearningBenchmark.rejection()
        val phrases = NeuroLearningBenchmark.phrases()
        val thinBucket = NeuroLearningBenchmark.thinBucket()
        val report = NeuroLearningBenchmark.report(retention, rejection, phrases, thinBucket)
        println(report)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "learning.txt").writeText(report)

        // A Shorts binge and two new videos must not wipe months of taste.
        assertThat(retention.comedyTopicsAfter).isAtLeast(retention.comedyTopicsBefore)
        assertThat(retention.comedyKnownAfter).isAtLeast(retention.comedyKnownBefore * 0.9)
        assertThat(retention.profileAfter).isAtLeast(0.6)
        assertThat(retention.guitarAfter).isAtMost(0.4)
        // Rejections remove the topic without making the feed random.
        assertThat(rejection.phonkAfterDislikes).isAtMost(0.1)
        assertThat(rejection.phonkAfterNotInterested).isAtMost(0.1)
        assertThat(rejection.noveltyAfterDislikes).isAtMost(0.21)
        assertThat(rejection.noveltyAfterNotInterested).isAtMost(0.21)
        // Phrases become the interest, and refresh queries are specific.
        assertThat(phrases.phraseWeight).isGreaterThan(phrases.wordWeight)
        assertThat(phrases.queries).containsNoneOf("guitar", "comedy")
        // A thin time bucket does not outweigh the profile.
        assertThat(thinBucket.guitarShare).isAtMost(0.15)
    }
}
