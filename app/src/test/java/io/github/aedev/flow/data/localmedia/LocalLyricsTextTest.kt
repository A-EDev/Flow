package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.Charset
import java.util.Locale

class LocalLyricsTextTest {
    private val latin1: Charset = Charsets.ISO_8859_1

    @Test
    fun `a byte order mark decides the encoding`() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "[00:01.00]Blåbær".toByteArray()
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "[00:01.00]Stöd".toByteArray(Charsets.UTF_16LE)

        assertThat(decodeTextFile(utf8Bom) { null }).isEqualTo("[00:01.00]Blåbær")
        assertThat(decodeTextFile(utf16) { null }).isEqualTo("[00:01.00]Stöd")
    }

    @Test
    fun `plain utf-8 is read as is and anything else goes to the detector`() {
        assertThat(decodeTextFile("Snälla".toByteArray()) { error("not needed") }).isEqualTo("Snälla")
        assertThat(decodeTextFile("Snälla".toByteArray(latin1)) { latin1 }).isEqualTo("Snälla")
        assertThat(decodeTextFile("Snälla".toByteArray(latin1)) { null }).isEqualTo("Snälla")
    }

    @Test
    fun `the device language picks the legacy code page`() {
        val cyrillic = "Привет".toByteArray(Charset.forName("windows-1251"))

        assertThat(decodeTextFile(cyrillic) { legacyTextCharset(Locale("ru")) }).isEqualTo("Привет")
        assertThat(legacyTextCharset(Locale.ENGLISH)).isNull()
    }

    @Test
    fun `the offset tag is read in milliseconds`() {
        assertThat(lrcOffsetMs("[ti:Song]\n[offset:+350]\n[00:01.00]Line")).isEqualTo(350L)
        assertThat(lrcOffsetMs("[offset: -120]")).isEqualTo(-120L)
        assertThat(lrcOffsetMs("[00:01.00]Line")).isEqualTo(0L)
    }

    @Test
    fun `plain lyrics lose their header tags`() {
        assertThat(plainLyricsText("[ar:Artist]\n[ti:Song]\nFirst line\nSecond line\n")).isEqualTo("First line\nSecond line")
    }

    @Test
    fun `the sidecar file matches by name in any case`() {
        val siblings = listOf("Song.mp3", "song.LRC", "Other.lrc")

        assertThat(matchingLyricsFile("Song.mp3", siblings)).isEqualTo("song.LRC")
        assertThat(matchingLyricsFile("Track.flac", listOf("Track.flac.lrc"))).isEqualTo("Track.flac.lrc")
        assertThat(matchingLyricsFile("Missing.mp3", siblings)).isNull()
    }
}
