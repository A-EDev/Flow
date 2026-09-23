package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.ui.screens.settings.about.parseChangelog
import io.github.aedev.flow.ui.screens.settings.about.sectionTitle
import io.github.aedev.flow.ui.screens.settings.about.sortedChangelogs
import io.github.aedev.flow.ui.screens.settings.diagnostics.LOG_CHUNK_LINES
import io.github.aedev.flow.ui.screens.settings.diagnostics.LogLevel
import io.github.aedev.flow.ui.screens.settings.diagnostics.LogState
import io.github.aedev.flow.ui.screens.settings.diagnostics.crashLevel
import io.github.aedev.flow.ui.screens.settings.diagnostics.logcatLevel
import io.github.aedev.flow.ui.screens.settings.diagnostics.parseLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiagnosticsAndAboutTest {
    @Test
    fun `logcat lines take their level from the level column`() {
        assertEquals(LogLevel.ERROR, logcatLevel("09-23 10:00:00.000  123  456 E Player: failed"))
        assertEquals(LogLevel.ERROR, logcatLevel("09-23 10:00:00.000  123  456 F libc: abort"))
        assertEquals(LogLevel.WARN, logcatLevel("09-23 10:00:00.000  123  456 W Cache: slow"))
        assertEquals(LogLevel.INFO, logcatLevel("09-23 10:00:00.000  123  456 I Flow: ready"))
        assertEquals(LogLevel.QUIET, logcatLevel("--------- beginning of main"))
    }

    @Test
    fun `crash reports mark rules and exceptions`() {
        assertEquals(LogLevel.HEADING, crashLevel("====="))
        assertEquals(LogLevel.ERROR, crashLevel("java.lang.IllegalStateException: boom"))
        assertEquals(LogLevel.INFO, crashLevel("    at io.github.aedev.flow.Main.run(Main.kt:1)"))
    }

    @Test
    fun `blank logs are empty and long ones are chunked`() {
        assertEquals(LogState.Empty, parseLog(null, ::logcatLevel))
        assertEquals(LogState.Empty, parseLog("\n  \n", ::logcatLevel))
        val state = parseLog((1..LOG_CHUNK_LINES + 1).joinToString("\n") { "line $it" } + "\n", ::crashLevel)
        assertTrue(state is LogState.Lines)
        val chunks = (state as LogState.Lines).chunks
        assertEquals(listOf(LOG_CHUNK_LINES, 1), chunks.map { it.size })
    }

    @Test
    fun `changelogs sort newest first by version, not by spelling`() {
        assertEquals(
            listOf("v2.10.0.txt", "v2.9.5.txt", "v2.2.10.txt", "v2.2.9.txt"),
            sortedChangelogs(listOf("v2.2.9.txt", "v2.9.5.txt", "notes.md", "v2.10.0.txt", "v2.2.10.txt")),
        )
    }

    @Test
    fun `a release note splits into its header, sections and changes`() {
        val release =
            parseChangelog(
                """
                FLOW CHANGE LOG
                VERSION: 2.2.5
                DATE: 2026-09-15
                STATUS: PRE-RELEASE

                NEW FEATURES
                - Search runs on InnerTube
                - Notes on any video

                UI AND STYLE ENHANCEMENTS
                - Expressive settings
                CORE:
                - Faster start
                """.trimIndent(),
                fallbackVersion = "0",
            )
        assertEquals("2.2.5", release.version)
        assertEquals(LocalDate.of(2026, 9, 15), release.date)
        assertTrue(release.preRelease)
        assertEquals(listOf("New features", "UI and style enhancements", "Core"), release.sections.map { it.title })
        assertEquals(listOf("Search runs on InnerTube", "Notes on any video"), release.sections.first().items)
    }

    @Test
    fun `a note without a header keeps the fallback version and no date`() {
        val release = parseChangelog("FIXES\n- One", fallbackVersion = "1.4.0")
        assertEquals("1.4.0", release.version)
        assertNull(release.date)
        assertEquals("Fixes", release.sections.single().title)
    }

    @Test
    fun `section titles keep acronyms and mixed case`() {
        assertEquals("Build and CI", sectionTitle("BUILD AND CI"))
        assertEquals("Important", sectionTitle("!IMPORTANT!"))
        assertEquals("Recommendation Engine (V9.1)", sectionTitle("Recommendation Engine (V9.1)"))
    }
}
