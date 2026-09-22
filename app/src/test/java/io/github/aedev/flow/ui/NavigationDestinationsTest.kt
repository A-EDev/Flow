package io.github.aedev.flow.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationDestinationsTest {
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
