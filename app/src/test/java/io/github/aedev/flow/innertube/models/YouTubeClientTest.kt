package io.github.aedev.flow.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeClientTest {
    @Test
    fun webContextMatchesDesktopClientShape() {
        val context = YouTubeClient.WEB.toContext(
            locale = YouTubeLocale(gl = "LB", hl = "en-US"),
            visitorData = null,
            dataSyncId = null,
        )

        assertEquals("https://www.youtube.com", context.client.originalUrl)
        assertEquals("DESKTOP", context.client.platform)
        assertEquals(0, context.client.utcOffsetMinutes)
        assertEquals("LB", context.client.gl)
        assertEquals("en-US", context.client.hl)
    }
}
