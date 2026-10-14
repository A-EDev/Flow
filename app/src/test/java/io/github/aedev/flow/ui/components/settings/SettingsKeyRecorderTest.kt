package io.github.aedev.flow.ui.components.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsKeyRecorderTest {
    @Test
    fun `a key used by a group header and its first row gets distinct lazy keys`() {
        val recorder = SettingsKeyRecorder()
        val header = recorder.add("quality.video.codec")
        val row = recorder.add("quality.video.codec")
        val third = recorder.add("quality.video.codec")

        assertEquals(setOf("quality.video.codec", "quality.video.codec#1", "quality.video.codec#2"), setOf(header, row, third))
        assertEquals(0, recorder.indexOf("quality.video.codec"))
    }

    @Test
    fun `reset starts a fresh page`() {
        val recorder = SettingsKeyRecorder()
        recorder.add("a")
        recorder.reset()
        assertEquals("a", recorder.add("a"))
    }
}
