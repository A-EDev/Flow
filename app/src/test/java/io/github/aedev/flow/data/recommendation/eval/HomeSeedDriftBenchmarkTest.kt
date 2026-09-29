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
 * Writes app/build/reports/neuro-benchmark/seed-drift.txt. The floors hold the #907 fixes: the
 * related lane must not walk off the profile page after page, and a lasting interest must reach it
 * even when recent watches are all about something else.
 */
class HomeSeedDriftBenchmarkTest {
    @Test
    fun `related lane stays on profile and serves lasting interests`() {
        val legacy = HomeSeedDriftBenchmark.run(HomeSeedDriftBenchmark.legacy)
        val current = HomeSeedDriftBenchmark.run(HomeSeedDriftBenchmark.current)
        val report = HomeSeedDriftBenchmark.report(listOf(legacy, current))

        println(report)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "seed-drift.txt").writeText(report)

        assertThat(current.offProfileShare).isLessThan(legacy.offProfileShare)
        assertThat(current.longTermCoverage).isAtLeast(0.9)
    }
}
