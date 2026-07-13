package io.github.aedev.flow.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigationDestinationsTest {
    @Test
    fun hiddenHomeFallsBackToFirstVisibleDestination() {
        val visibility = NavigationVisibility(home = false, shorts = false, music = false)

        val resolved = resolveDefaultNavTabIndex(
            preferredIndex = 0,
            order = listOf(0, 4, 3, 1, 2, 5, 6),
            visibility = visibility
        )

        assertEquals(4, resolved)
        assertFalse(visibleNavTabIndices(listOf(0, 4, 3), visibility).contains(0))
    }

    @Test
    fun reEnabledHomeRestoresAHomeDefault() {
        val resolved = resolveDefaultNavTabIndex(
            preferredIndex = 0,
            order = listOf(3, 0, 4),
            visibility = NavigationVisibility(home = true)
        )

        assertEquals(0, resolved)
    }

    @Test
    fun channelHandlesUseHandleUrls() {
        assertEquals("https://www.youtube.com/@flow", youtubeChannelUrl("@flow"))
        assertEquals("https://www.youtube.com/@flow", youtubeChannelUrl("flow"))
        assertEquals(
            "https://www.youtube.com/channel/UC123",
            youtubeChannelUrl("UC123")
        )
    }
}
