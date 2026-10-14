package io.github.aedev.flow.ui

import androidx.compose.ui.platform.UriHandler
import io.github.aedev.flow.ui.components.layout.navigation.MediaNavigator
import io.github.aedev.flow.utils.YouTubeLink
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowUriHandlerTest {
    private val video = "dQw4w9WgXcQ"

    private class FakeNavigator : MediaNavigator {
        val opened = mutableListOf<YouTubeLink>()

        override fun openChannel(channelId: String) = Unit

        override fun openArtist(artistId: String) = Unit

        override fun openAlbum(albumId: String) = Unit

        override fun openMusicPlaylist(playlistId: String) = Unit

        override fun openEqualizer() = Unit

        override fun openLink(link: YouTubeLink): Boolean {
            if (link is YouTubeLink.Search) return false
            opened += link
            return true
        }
    }

    private class FakePlatform : UriHandler {
        val opened = mutableListOf<String>()

        override fun openUri(uri: String) {
            opened += uri
        }
    }

    private val navigator = FakeNavigator()
    private val platform = FakePlatform()
    private val handler = FlowUriHandler(navigator, platform)

    @Test
    fun `a youtube link opens in the app`() {
        handler.openUri("https://youtu.be/$video")
        handler.openUri("https://www.youtube.com/watch?v=$video&list=PLabcdef")

        assertEquals(
            listOf(YouTubeLink.Video(video, isMusic = false), YouTubeLink.Video(video, isMusic = false, playlistId = "PLabcdef")),
            navigator.opened,
        )
        assertEquals(emptyList<String>(), platform.opened)
    }

    @Test
    fun `any other link goes to the platform unchanged`() {
        handler.openUri("https://example.com/page?v=$video")

        assertEquals(emptyList<YouTubeLink>(), navigator.opened)
        assertEquals(listOf("https://example.com/page?v=$video"), platform.opened)
    }

    @Test
    fun `a link the app has no page for goes to the platform`() {
        handler.openUri("https://www.youtube.com/results?search_query=cats")

        assertEquals(listOf("https://www.youtube.com/results?search_query=cats"), platform.opened)
    }

    @Test
    fun `a youtube redirect opens its target`() {
        handler.openUri("https://www.youtube.com/redirect?event=video_description&q=https%3A%2F%2Fexample.com%2Fshop&v=$video")
        handler.openUri("https://www.youtube.com/redirect?q=https%3A%2F%2Fyoutu.be%2F$video")

        assertEquals(listOf("https://example.com/shop"), platform.opened)
        assertEquals(listOf(YouTubeLink.Video(video, isMusic = false)), navigator.opened)
    }

    @Test
    fun `only youtube's own redirect is unwrapped`() {
        assertEquals("https://example.com/redirect?q=x", unwrapYouTubeRedirect("https://example.com/redirect?q=x"))
    }

    @Test
    fun `a bare web address opens with https`() {
        handler.openUri("betterstack.com")
        handler.openUri(" example.com/shop?id=1 ")

        assertEquals(listOf("https://betterstack.com", "https://example.com/shop?id=1"), platform.opened)
    }

    @Test
    fun `links that carry a scheme or are not addresses are left alone`() {
        assertEquals("mailto:team@example.com", withWebScheme("mailto:team@example.com"))
        assertEquals("http://example.com", withWebScheme("http://example.com"))
        assertEquals("https://example.com:8080/x", withWebScheme("example.com:8080/x"))
        assertEquals("not a link", withWebScheme("not a link"))
    }

    @Test
    fun `a link no app can open is reported instead of crashing`() {
        val refused = mutableListOf<String>()
        val throwing =
            object : UriHandler {
                override fun openUri(uri: String): Unit = throw IllegalArgumentException("Can't open $uri.")
            }
        FlowUriHandler(navigator, throwing) { refused += it }.openUri("betterstack.com")

        assertEquals(listOf("https://betterstack.com"), refused)
    }
}
