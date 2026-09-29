package io.github.aedev.flow.data.video.downloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeStreamUrlsTest {
    private val url = "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&sig=A%2BB%3D&range=0-99&clen=5000&n=xyz"

    @Test
    fun `googlevideo and youtube playback urls are recognised`() {
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl(url)).isTrue()
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl("https://www.youtube.com/videoplayback?id=1")).isTrue()
        assertThat(YouTubeStreamUrls.isYouTubeStreamUrl("https://example.com/googlevideo.com")).isFalse()
    }

    @Test
    fun `the full size comes from clen`() {
        assertThat(YouTubeStreamUrls.extractClenFromUrl(url)).isEqualTo(5000L)
        assertThat(YouTubeStreamUrls.extractClenFromUrl("https://x.googlevideo.com/videoplayback?itag=1")).isEqualTo(-1L)
    }

    @Test
    fun `a block url replaces the extractor's range and leaves every other byte of the query alone`() {
        val block = YouTubeStreamUrls.buildYouTubeBlockUrl(url, 100, 199)

        assertThat(
            block,
        ).isEqualTo("https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1&sig=A%2BB%3D&clen=5000&n=xyz&range=100-199")
        assertThat(YouTubeStreamUrls.buildYouTubeBlockUrl("https://x.googlevideo.com/videoplayback", 0, 9))
            .isEqualTo("https://x.googlevideo.com/videoplayback?range=0-9")
    }
}
