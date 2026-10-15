/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation.eval

import com.google.common.truth.Truth.assertThat
import org.junit.AfterClass
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Home replayed from snapshots (#907). Each line records today's numbers; the change that improves
 * one replaces its logging with an assertion of the target. Writes
 * app/build/reports/neuro-benchmark/home-replay.txt.
 */
class HomeReplayTest {
    @Test
    fun `synthetic tech viewer`() {
        val fixture = HomeReplayFixtures.techViewer()
        val metrics = HomeReplay.replay(fixture.snapshot, fixture.labels)
        log(metrics.render(fixture.name))
        assertThat(metrics.cards).isAtLeast(10)
        // #907 target: at most one off-topic video among the first ten (0.8 on main at 9ed431dfa).
        assertThat(metrics.onTopicTop10).isAtLeast(0.9)
    }

    @Test
    fun `recorded snapshots`() {
        val fixtures = HomeReplayFixtures.recorded()
        assumeTrue("No recorded snapshots in ${HomeReplayFixtures.replayDirectory}", fixtures.isNotEmpty())
        fixtures.forEach { fixture ->
            val metrics = HomeReplay.replay(fixture.snapshot, fixture.labels)
            log(
                metrics.render(fixture.name) +
                    " | as recorded, top 20 ${"%.3f".format(HomeReplay.recordedOnTopicTop20(fixture.snapshot, fixture.labels))}",
            )
            HomeReplay.renderShown(fixture.snapshot, fixture.labels)?.let { log("${fixture.name} $it") }
        }
    }

    companion object {
        private val lines = mutableListOf<String>()

        @Synchronized
        private fun log(line: String) {
            println(line)
            lines += line
        }

        @JvmStatic
        @AfterClass
        fun writeReport() {
            val out = File("build/reports/neuro-benchmark").apply { mkdirs() }
            File(out, "home-replay.txt").writeText(lines.sorted().joinToString("\n", postfix = "\n"))
        }
    }
}
