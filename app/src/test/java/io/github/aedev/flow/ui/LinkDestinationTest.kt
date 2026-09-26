package io.github.aedev.flow.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.utils.parseYouTubeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class LinkDestinationTest {
    private val video = "dQw4w9WgXcQ"
    private val channel = "UCXuqSBlHAE6Xw-yeJA0Tunw"

    private fun destinationOf(link: String): LinkDestination? = parseYouTubeLink(link)?.let(::linkDestination)

    private fun page(route: String) = LinkDestination.Page(route)

    @Test
    fun `videos play in the video player and shorts in the shorts player`() {
        assertEquals(LinkDestination.Video(video), destinationOf("https://youtu.be/$video?si=x"))
        assertEquals(LinkDestination.Video(video), destinationOf("https://www.youtube.com/watch?v=$video&list=RDabc"))
        assertEquals(LinkDestination.Short(video), destinationOf("https://youtube.com/shorts/$video"))
    }

    @Test
    fun `youtube music songs play in the music player`() {
        assertEquals(page("musicPlayer/$video"), destinationOf("https://music.youtube.com/watch?v=$video&si=x"))
    }

    @Test
    fun `playlists open their page`() {
        assertEquals(page("playlist/PLabcdef"), destinationOf("https://www.youtube.com/playlist?list=PLabcdef"))
        assertEquals(page("musicPlaylist/PLabcdef"), destinationOf("https://music.youtube.com/playlist?list=PLabcdef"))
        assertEquals(page("musicPlaylist/OLAK5uy_abc"), destinationOf("https://www.youtube.com/playlist?list=OLAK5uy_abc"))
        assertEquals(page("musicPlaylist/RDCLAK5uy_abc"), destinationOf("https://music.youtube.com/playlist?list=RDCLAK5uy_abc"))
        assertEquals(page("musicPlaylist/MPREb_abc"), destinationOf("https://music.youtube.com/browse/MPREb_abc"))
    }

    @Test
    fun `the account's own lists open the library copies`() {
        assertEquals(page("playlist/liked_videos"), destinationOf("https://www.youtube.com/playlist?list=LL"))
        assertEquals(page("playlist/watch_later"), destinationOf("https://www.youtube.com/playlist?list=WL"))
        assertEquals(page("musicPlaylist/liked_music"), destinationOf("https://music.youtube.com/playlist?list=LM"))
    }

    @Test
    fun `a mix on its own and a search have no page`() {
        assertNull(destinationOf("https://www.youtube.com/playlist?list=RDdQw4w9WgXcQ"))
        assertNull(destinationOf("https://www.youtube.com/watch?list=RDMMabc"))
        assertNull(destinationOf("https://www.youtube.com/results?search_query=cats"))
    }

    @Test
    fun `channels open the channel page and music channels the artist page`() {
        assertEquals(
            page("channel?url=https%3A%2F%2Fwww.youtube.com%2Fchannel%2F$channel"),
            destinationOf("https://m.youtube.com/channel/$channel?si=abc"),
        )
        assertEquals(page("artist/$channel"), destinationOf("https://music.youtube.com/channel/$channel"))
        assertEquals(
            page("channel?url=https%3A%2F%2Fwww.youtube.com%2F%40LinusTechTips"),
            destinationOf("https://www.youtube.com/@LinusTechTips/videos"),
        )
        assertEquals(
            page("channel?url=https%3A%2F%2Fwww.youtube.com%2Fc%2FLinusTechTips"),
            destinationOf("https://www.youtube.com/c/LinusTechTips"),
        )
        assertEquals(
            page("channel?url=https%3A%2F%2Fwww.youtube.com%2Fuser%2FLinusTechTips"),
            destinationOf("https://www.youtube.com/user/LinusTechTips"),
        )
    }

    @Test
    fun `link text comes from a view intent or shared plain text only`() {
        val url = "https://youtu.be/$video"
        assertEquals(url, linkTextOf(Intent(Intent.ACTION_VIEW, Uri.parse(url))))
        assertEquals(
            "Look $url",
            linkTextOf(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Look $url")),
        )
        assertNull(linkTextOf(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_TEXT, url)))
        assertNull(linkTextOf(Intent("io.github.aedev.flow.widget.OPEN", Uri.parse("flow://widget/video/$video"))))
    }
}
