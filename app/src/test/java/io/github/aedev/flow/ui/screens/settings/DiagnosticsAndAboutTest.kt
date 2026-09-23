package io.github.aedev.flow.ui.screens.settings

import io.github.aedev.flow.ui.screens.settings.about.latestChangelog
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
    fun `the newest changelog is picked by version, not by spelling`() {
        assertEquals("v2.10.0.txt", latestChangelog(listOf("v2.9.5.txt", "v2.10.0.txt", "v2.2.5.txt", "notes.md")))
        assertEquals("v2.2.10.txt", latestChangelog(listOf("v2.2.9.txt", "v2.2.10.txt")))
        assertNull(latestChangelog(listOf("readme.md")))
    }
}
