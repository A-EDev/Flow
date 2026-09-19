package io.github.aedev.flow.ui.components.shorts

import io.github.aedev.flow.data.local.ShortsPlayerUiMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsOverlayStyleTest {
    @Test
    fun `the simple layout drops labels and confirms saves with a toast`() {
        val style = ShortsOverlayStyle.from(ShortsPlayerUiMode.SIMPLE)

        assertFalse(style.showRailLabels)
        assertEquals(ShortsSubscribeControl.IconToggle, style.subscribeControl)
        assertFalse(style.controlsOnDemand)
        assertTrue(style.toastOnSave)
    }

    @Test
    fun `the impressive layout is the default with chrome on demand`() {
        val default = ShortsOverlayStyle.from(ShortsPlayerUiMode.DEFAULT)
        val impressive = ShortsOverlayStyle.from(ShortsPlayerUiMode.IMPRESSIVE)

        assertFalse(default.controlsOnDemand)
        assertTrue(impressive.controlsOnDemand)
        assertEquals(default.copy(controlsOnDemand = true), impressive)
    }
}
