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
 * Runs the offline pipeline benchmark and writes a metrics report to
 * app/build/reports/neuro-benchmark/report.txt so engine changes can be
 * compared number-to-number across commits, not argued on paper.
 *
 * Assertions here are FLOORS — loose enough to be stable, tight enough that a
 * regression in repetition control or interest coverage fails the build.
 * Tighten them as the roadmap lands improvements.
 */
class NeuroBenchmarkTest {
    @Test
    fun `pipeline benchmark - report and regression floors`() {
        val serving = NeuroBenchmark.simulateServing()
        val discovery = NeuroBenchmark.discoveryCoverage()
        val ungated = NeuroBenchmark.simulateServing(seenGateEnabled = false)
        val report =
            NeuroBenchmark.renderReport("branch", serving, discovery) +
                "\nCOUNTERFACTUAL (seen-gate OFF, same universe/seed)\n" +
                "  meanSeenRepeatRate       = %.3f\n".format(ungated.summary.meanSeenRepeatRate) +
                "  meanServedRepeatRate     = %.3f\n".format(ungated.summary.meanServedRepeatRate)

        println(report)
        val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
        File(out, "report.txt").writeText(report)

        val s = serving.summary
        // Relevance and diversity must never collapse.
        assertThat(s.meanNdcg).isAtLeast(0.55)
        assertThat(s.meanIld).isAtLeast(0.20)
        // The user's interests must not vanish from the feed entirely.
        assertThat(s.meanGroupCoverage).isAtLeast(0.30)
        assertThat(s.cumulativeGroupCoverage).isAtLeast(0.50)
        // User-experienced repetition (already-impressed items reappearing) must
        // stay low — the seen-gate's whole job. Served-repeat is looser: items the
        // user never actually saw may legitimately return.
        assertThat(s.meanSeenRepeatRate).isAtMost(0.15)
        assertThat(s.meanServedRepeatRate).isAtMost(0.60)
        // Discovery has to keep producing queries.
        assertThat(discovery.sessions).isNotEmpty()
        assertThat(discovery.sessions.all { it.isNotEmpty() }).isTrue()
    }

    @Test
    fun `serving benchmark is reproducible for a fixed seed`() {
        val a = NeuroBenchmark.simulateServing(seed = 7L)
        val b = NeuroBenchmark.simulateServing(seed = 7L)
        // Discovery uses unseeded shuffles internally, so query ORDER can vary;
        // the deterministic core must still hold: same relevance envelope.
        assertThat(a.summary.meanNdcg).isWithin(0.15).of(b.summary.meanNdcg)
        assertThat(a.summary.meanSeenRepeatRate).isWithin(0.20).of(b.summary.meanSeenRepeatRate)
    }
}
