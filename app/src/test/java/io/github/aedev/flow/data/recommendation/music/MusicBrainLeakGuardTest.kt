/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation.music

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * The engine-separation guard, ported from the desktop where music leaking into
 * the video brain regressed three times. Scans every source file in the music
 * brain package and fails the build if any non-comment line references the video
 * engine. Music must never learn through — or rank with — the video path.
 */
class MusicBrainLeakGuardTest {
    private val forbidden =
        listOf(
            "FlowNeuroEngine",
            "NeuroScoring",
            "NeuroDiscovery",
            "NeuroStorage",
            "NeuroClusters",
            "NeuroContentStore",
            "UserBrain",
            "applySeenGate",
            "onVideoInteraction",
            "ShortsDiscoveryEngine",
        )

    @Test
    fun `music brain sources never reference the video engine`() {
        // Unit tests run with the app module as the working directory.
        val dir = File("src/main/java/io/github/aedev/flow/data/recommendation/music")
        assertWithMessage("music brain package missing at ${dir.absolutePath}").that(dir.isDirectory).isTrue()

        val violations = mutableListOf<String>()
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                val isComment = line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")
                if (!isComment) {
                    forbidden.forEach { symbol ->
                        if (line.contains(symbol)) violations.add("${file.name}:${index + 1} references $symbol")
                    }
                }
            }
        }
        assertWithMessage("video-engine symbols leaked into the music brain:\n${violations.joinToString("\n")}")
            .that(violations)
            .isEmpty()
    }
}
