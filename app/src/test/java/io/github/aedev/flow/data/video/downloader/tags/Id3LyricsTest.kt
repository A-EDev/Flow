package io.github.aedev.flow.data.video.downloader.tags

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class Id3LyricsTest {
    private fun frame(
        encoding: Int,
        descriptor: ByteArray,
        text: ByteArray,
    ) = byteArrayOf(encoding.toByte()) + "eng".toByteArray() + descriptor + text

    @Test
    fun `utf-8 lyrics follow an empty descriptor`() {
        val bytes = frame(3, byteArrayOf(0), "[00:01.00]Snälla".toByteArray())

        assertThat(Id3Lyrics.decode(bytes)).isEqualTo("[00:01.00]Snälla")
    }

    @Test
    fun `utf-16 lyrics skip a two byte terminated descriptor`() {
        val descriptor = "d".toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0)
        val bytes = frame(1, descriptor, "Stöd".toByteArray(Charsets.UTF_16))

        assertThat(Id3Lyrics.decode(bytes)).isEqualTo("Stöd")
    }

    @Test
    fun `latin-1 lyrics are decoded and an unknown encoding is refused`() {
        assertThat(Id3Lyrics.decode(frame(0, "desc".toByteArray() + 0, "för".toByteArray(Charsets.ISO_8859_1)))).isEqualTo("för")
        assertThat(Id3Lyrics.decode(frame(9, byteArrayOf(0), "x".toByteArray()))).isNull()
    }
}
