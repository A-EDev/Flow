package io.github.aedev.flow.data.lyrics

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.lyrics.LyricsMatching.Song
import org.junit.Test

class LrcRedLyricsProviderTest {
    private fun hit(
        title: String = "Song",
        artist: String = "Band",
        duration: Int = 200,
        timing: String = "word",
        url: String = "https://lrc.red/s/XX0000000001.ttml",
    ) =
        """{"track_name":"$title","artist_name":"$artist","duration":$duration,"timing_type":"$timing","lyricsUrl":"$url","isrc":"XX0000000001"}"""

    private fun hits(vararg items: String) = LrcRedLyricsProvider.parseSearch("""{"results":[${items.joinToString(",")}],"extra":1}""")

    @Test
    fun `search results parse and ignore unknown fields`() {
        val parsed = hits(hit())

        assertThat(parsed.single().trackName).isEqualTo("Song")
        assertThat(parsed.single().lyricsUrl).isEqualTo("https://lrc.red/s/XX0000000001.ttml")
    }

    @Test
    fun `a word-timed file wins over a line-timed one of the same recording`() {
        val picked = LrcRedLyricsProvider.pick(hits(hit(timing = "line"), hit(timing = "word", duration = 201)), Song("Song", "Band", 200))

        assertThat(picked?.timingType).isEqualTo("word")
    }

    @Test
    fun `the studio cut is not taken for a live request`() {
        assertThat(LrcRedLyricsProvider.pick(hits(hit()), Song("Song (Live)", "Band", 200))).isNull()
    }

    @Test
    fun `a document that is not served over https is never fetched`() {
        assertThat(LrcRedLyricsProvider.pick(hits(hit(url = "http://lrc.red/s/a.ttml")), Song("Song", "Band", 200))).isNull()
    }
}
