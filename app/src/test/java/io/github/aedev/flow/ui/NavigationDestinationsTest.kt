package io.github.aedev.flow.ui

import io.github.aedev.flow.ui.components.layout.navigation.FlowTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationDestinationsTest {
    @Test
    fun tabRootsMapToTheirTab() {
        FlowTab.entries.filter { it != FlowTab.Shorts }.forEach { tab ->
            assertEquals(tab, flowTabForDestination(tab.route, shortsSourceArg = null))
        }
    }

    @Test
    fun onlyTheShortsFeedIsTheShortsTab() {
        assertEquals(FlowTab.Shorts, flowTabForDestination(SHORTS_ROUTE_PATTERN, shortsSourceArg = null))
        assertEquals(FlowTab.Shorts, flowTabForDestination(SHORTS_ROUTE_PATTERN, shortsSourceArg = "feed"))
        assertNull(flowTabForDestination(SHORTS_ROUTE_PATTERN, shortsSourceArg = "feed:dQw4w9WgXcQ"))
        assertNull(flowTabForDestination(SHORTS_ROUTE_PATTERN, shortsSourceArg = "saved:"))
    }

    @Test
    fun detailScreensAreNotTabRoots() {
        listOf("settings", "settings/content", "playlists", "playlist/{playlistId}", "onboarding", null).forEach { route ->
            assertNull(flowTabForDestination(route, shortsSourceArg = null))
        }
    }

    @Test
    fun searchIsTheOnlyTabWithoutTheBar() {
        FlowTab.entries.forEach { tab -> assertEquals(tab != FlowTab.Search, tab.showsNavigationBar()) }
        assertFalse((null as FlowTab?).showsNavigationBar())
        assertTrue(FlowTab.Home.showsNavigationBar())
    }

    @Test
    fun channelLinksOpenTheChannelRoute() {
        assertEquals(
            "channel?url=https%3A%2F%2Fwww.youtube.com%2Fchannel%2FUCXuqSBlHAE6Xw-yeJA0Tunw",
            youtubeChannelDeepLinkRoute("https://www.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw"),
        )
        assertEquals(
            "channel?url=https%3A%2F%2Fwww.youtube.com%2Fchannel%2FUCXuqSBlHAE6Xw-yeJA0Tunw",
            youtubeChannelDeepLinkRoute("https://m.youtube.com/channel/UCXuqSBlHAE6Xw-yeJA0Tunw?si=abc"),
        )
    }

    @Test
    fun linksBrowseCannotOpenAreNotChannelRoutes() {
        assertEquals(null, youtubeChannelDeepLinkRoute("https://www.youtube.com/@LinusTechTips"))
        assertEquals(null, youtubeChannelDeepLinkRoute("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, youtubeChannelDeepLinkRoute("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(null, youtubeChannelDeepLinkRoute("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(null, youtubeChannelDeepLinkRoute("https://www.youtube.com/c/LinusTechTips"))
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
}
