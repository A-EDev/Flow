/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.music.eval

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicBrain
import io.github.aedev.flow.data.recommendation.music.MusicBrainRanker
import io.github.aedev.flow.data.recommendation.music.MusicBrainStorage
import io.github.aedev.flow.data.recommendation.music.toMusicBrain
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Offline diagnostic over a REAL exported music brain. Pull it from a debug
 * device with:
 *
 *   adb shell run-as io.github.aedev.flow.debug cat files/datastore/flow_music_brain_v1.json > diagnostics/music-brain.json
 *
 * then run this test (or pass -Dflow.musicBrainPath=...). Skips when no file
 * exists so CI stays green. Reports go to build/reports/music-brain-diagnostic/.
 */
class MusicBrainDiagnosticTest {
    private fun locateBrainFile(): File? {
        System.getProperty("flow.musicBrainPath")?.let { p ->
            File(p).takeIf { it.isFile }?.let { return it }
        }
        // Unit tests run with the app module as the working directory.
        return listOf(File("../diagnostics/music-brain.json"), File("diagnostics/music-brain.json"))
            .firstOrNull { it.isFile }
    }

    private fun parseBrain(file: File): MusicBrain =
        Json { ignoreUnknownKeys = true }
            .decodeFromString<MusicBrainStorage.SerializableMusicBrain>(file.readText())
            .toMusicBrain()

    @Test
    fun `diagnose real music brain`() {
        val file = locateBrainFile()
        assumeTrue("no exported music brain found — skipping", file != null)
        val brain = parseBrain(file!!)
        val now = System.currentTimeMillis()

        val report =
            buildString {
                appendLine("═══ Music Brain Diagnostic ═══")
                appendLine("source: ${file.absolutePath}")
                appendLine("backfilled=${brain.backfilled}  totalPlays=${brain.totalPlays}")
                appendLine(
                    "appetite=%.3f  artists=%d  cooc=%d  seen=%d".format(
                        brain.discoveryAppetite,
                        brain.artistAffinity.size,
                        brain.artistCooc.size,
                        brain.seenArtists.size,
                    ),
                )
                appendLine("trackPlays=${brain.trackPlays.size}  trackMeta=${brain.trackMeta.size}  rotation=${brain.recentRotation.size}")
                appendLine("disliked=${brain.dislikedArtists.size}  blocked=${brain.blockedArtists.size}")

                val ringHistogram =
                    brain.trackPlays.values
                        .groupingBy { it.size }
                        .eachCount()
                        .toSortedMap()
                appendLine("ring-size histogram: $ringHistogram")

                val newest = brain.trackPlays.values.maxOfOrNull { it.max() } ?: 0L
                appendLine("newest play: ${if (newest > 0) "%.1f h ago".format((now - newest) / 3_600_000.0) else "none"}")

                val hot =
                    brain.trackPlays.keys
                        .map { it to MusicBrainRanker.baseLevelActivation(brain, it, now) }
                        .filter { it.second > 0.0 }
                        .sortedByDescending { it.second }
                appendLine()
                appendLine("ON REPEAT CANDIDATES (activation > 0): ${hot.size}")
                hot.take(16).forEach { (id, act) ->
                    val meta = brain.trackMeta[id]
                    appendLine("  %.2f  n=%d  %s — %s".format(act, brain.trackPlays[id]?.size ?: 0, meta?.title ?: id, meta?.artist ?: "?"))
                }

                appendLine()
                appendLine("TOP 20 ARTISTS:")
                brain.artistAffinity.entries
                    .sortedByDescending { it.value.score }
                    .take(20)
                    .forEach { (k, v) ->
                        appendLine("  %-34s score=%.3f plays=%-3d liked=%s".format(k.take(34), v.score, v.plays, v.liked))
                    }
            }

        println(report)
        val out = File("build/reports/music-brain-diagnostic").apply { mkdirs() }
        File(out, "report.txt").writeText(report)

        // Sanity, not floors: a parsed brain must be structurally coherent.
        assertThat(brain.trackPlays.keys).containsAtLeastElementsIn(brain.trackMeta.keys)
        assertThat(brain.discoveryAppetite).isAtLeast(0.05)
        assertThat(brain.discoveryAppetite).isAtMost(0.95)
    }
}
