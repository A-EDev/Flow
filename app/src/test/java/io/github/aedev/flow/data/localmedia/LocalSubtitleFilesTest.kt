package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.stream.CaptionFormat
import org.junit.Test
import java.nio.charset.Charset

class LocalSubtitleFilesTest {
    @Test
    fun `files named like the video are found, the exact name first`() {
        val matches =
            matchingSubtitleFiles(
                videoFileName = "Movie [abc].mkv",
                siblings =
                    listOf(
                        "Movie [abc].en.srt",
                        "Movie [abc].mkv",
                        "Movie [abc].ass",
                        "Movie [abc] 2.srt",
                        "Other.srt",
                        "Movie [abc].nfo",
                    ),
            )

        assertThat(matches.map { it.name }).containsExactly("Movie [abc].ass", "Movie [abc].en.srt").inOrder()
        assertThat(matches.map { it.format }).containsExactly(CaptionFormat.SSA, CaptionFormat.SRT).inOrder()
        assertThat(matches.map { it.languageTag }).containsExactly("", "en").inOrder()
    }

    @Test
    fun `the language after the name becomes a tag, markers are skipped`() {
        assertThat(subtitleLanguageOf(listOf("", "pt_BR"))).isEqualTo("pt-BR")
        assertThat(subtitleLanguageOf(listOf("", "forced", "eng"))).isEqualTo("en")
        assertThat(subtitleLanguageOf(listOf("", "SDH"))).isEmpty()
        assertThat(subtitleLanguageOf(listOf("", "English"))).isEmpty()
    }

    @Test
    fun `only text that is not unicode is copied for the player`() {
        assertThat(isUnicodeText("Blåbær".toByteArray())).isTrue()
        assertThat(isUnicodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0))).isTrue()
        assertThat(isUnicodeText("مرحبا".toByteArray(Charset.forName("windows-1256")))).isFalse()
    }
}
