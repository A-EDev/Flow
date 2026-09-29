/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music.eval

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Regression floors for the music engine, written BEFORE the surfaces ship so
 * every later change lands with a measured delta. Music-native semantics:
 * relistening is a HIT here, the inverse of the video engine's repeat metrics.
 */
class MusicBenchmarkTest {
    @Test
    fun `music pipeline benchmark - report and regression floors`() {
        val now = 1_700_000_000_000L
        val result = MusicBenchmark.run(now)
        val report = MusicBenchmark.renderReport(result)

        println(report)
        val out = File("build/reports/music-benchmark").apply { mkdirs() }
        File(out, "report.txt").writeText(report)

        // Comfort surfaces must keep surfacing the loved tracks — repeat is reward.
        assertThat(result.quickPicks.hitRate).isAtLeast(0.99)
        // Discovery must actually be more novel than comfort on the same pool.
        assertThat(result.discover.discoveryRate).isGreaterThan(result.quickPicks.discoveryRate)
        // Sequencing: short artist runs are fine, long ones are broken.
        assertThat(result.quickPicks.maxArtistRun).isAtMost(2)
        assertThat(result.discover.maxArtistRun).isAtMost(2)
        // A cold brain must be a byte-identical pass-through.
        assertThat(result.coldPassthroughHolds).isTrue()
        // A block is a removal, never a demotion.
        assertThat(result.blockedLeaks).isEqualTo(0)
    }

    @Test
    fun `benchmark is deterministic for a fixed clock`() {
        val now = 1_700_000_000_000L
        val a = MusicBenchmark.run(now)
        val b = MusicBenchmark.run(now)
        assertThat(a.quickPicks).isEqualTo(b.quickPicks)
        assertThat(a.discover).isEqualTo(b.discover)
    }
}
