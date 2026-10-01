/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import org.junit.Test
import java.io.File

/**
 * Writes app/build/reports/neuro-benchmark/learning.txt. Compare old and new code back to back:
 * ranking jitter is unseeded, so shares move by a few points between runs.
 */
class NeuroLearningBenchmarkTest {
    @Test
    fun `learning benchmark - report`() {
        val report =
            NeuroLearningBenchmark.report(
                NeuroLearningBenchmark.retention(),
                NeuroLearningBenchmark.rejection(),
                NeuroLearningBenchmark.phrases(),
                NeuroLearningBenchmark.thinBucket(),
            )
        println(report)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "learning.txt").writeText(report)
    }
}
