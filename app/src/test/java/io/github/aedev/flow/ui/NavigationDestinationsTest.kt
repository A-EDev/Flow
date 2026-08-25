package io.github.aedev.flow.ui

import io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDestinationsTest {
    @Test
    fun hiddenHomeFallsBackToFirstVisibleDestination() {
        val visibility = NavigationVisibility(home = false, shorts = false, music = false)

        val resolved =
            resolveDefaultNavTabIndex(
                preferredIndex = 0,
                order = listOf(0, 4, 3, 1, 2, 5, 6),
                visibility = visibility,
            )

        assertEquals(4, resolved)
        assertFalse(visibleNavTabIndices(listOf(0, 4, 3), visibility).contains(0))
    }

    @Test
    fun reEnabledHomeRestoresAHomeDefault() {
        val resolved =
            resolveDefaultNavTabIndex(
                preferredIndex = 0,
                order = listOf(3, 0, 4),
                visibility = NavigationVisibility(home = true),
            )

        assertEquals(0, resolved)
    }

    @Test
    fun channelHandlesUseHandleUrls() {
        assertEquals("https://www.youtube.com/@flow", youtubeChannelUrl("@flow"))
        assertEquals("https://www.youtube.com/@flow", youtubeChannelUrl("flow"))
        assertEquals(
            "https://www.youtube.com/channel/UC123",
            youtubeChannelUrl("UC123"),
        )
    }

    @Test
    fun malformedHandleChannelUrlsAreRepaired() {
        assertEquals(
            "https://www.youtube.com/@flow",
            youtubeChannelUrl("https://youtube.com/channel/@flow"),
        )
        assertEquals(
            "https://www.youtube.com/@flow",
            youtubeChannelUrl("https://m.youtube.com/@flow/videos"),
        )
    }

    @Test
    fun channelRoutesEncodeCanonicalUrls() {
        assertEquals(
            "channel?url=https%3A%2F%2Fwww.youtube.com%2F%40flow",
            youtubeChannelRoute("@flow"),
        )
    }

    /**
     * Settings and Notifications live in the top bar of every root destination, which only works if
     * a root destination always exists. Subscriptions (3) and Library (4) are unconditional in
     * [visibleNavTabIndices] — this pins that down so hiding tabs can never orphan those screens.
     */
    @Test
    fun everyVisibilityCombinationKeepsAnUnhideableRootDestination() {
        val orders =
            listOf(
                DEFAULT_NAV_TAB_ORDER,
                listOf(6, 5, 4, 3, 2, 1, 0),
                listOf(4, 3),
                emptyList(),
            )

        for (bits in 0 until 32) {
            val visibility =
                NavigationVisibility(
                    home = bits and 1 != 0,
                    shorts = bits and 2 != 0,
                    music = bits and 4 != 0,
                    search = bits and 8 != 0,
                    categories = bits and 16 != 0,
                )

            for (order in orders) {
                val visible = visibleNavTabIndices(order, visibility)
                assertTrue(
                    "no visible tab for $visibility / $order",
                    visible.isNotEmpty(),
                )
                assertTrue(
                    "no unhideable root destination for $visibility / $order",
                    visible.contains(3) || visible.contains(4),
                )

                val resolved = resolveDefaultNavTabIndex(0, order, visibility)
                assertTrue(
                    "resolved default $resolved is not visible for $visibility / $order",
                    visible.contains(resolved),
                )
            }
        }
    }
}
