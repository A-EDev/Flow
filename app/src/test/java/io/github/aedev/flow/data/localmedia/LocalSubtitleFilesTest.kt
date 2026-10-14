package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.stream.CaptionFormat
import org.junit.Test
import java.nio.charset.Charset

class LocalSubtitleFilesTest {
    private fun matches(
        video: String,
        vararg paths: String,
        videosInFolder: Int = 2,
        ownFolder: Set<String> = emptySet(),
    ) = matchingSubtitleFiles(video, paths.map { SubtitleFileCandidate(it, inVideoSubfolder = it in ownFolder) }, videosInFolder)

    @Test
    fun `files named like the video are found, the exact name first`() {
        val found =
            matches(
                "Movie [abc].mkv",
                "Movie [abc].en.srt",
                "Movie [abc].mkv",
                "Movie [abc].ass",
                "Movie [abc] 2.srt",
                "Other.srt",
                "Movie [abc].nfo",
            )

        assertThat(found.map { it.path }).containsExactly("Movie [abc].ass", "Movie [abc].en.srt").inOrder()
        assertThat(found.map { it.format }).containsExactly(CaptionFormat.SSA, CaptionFormat.SRT).inOrder()
        assertThat(found.map { it.languageTag }).containsExactly("", "en").inOrder()
    }

    @Test
    fun `dots, spaces and case do not stop a match`() {
        assertThat(matches("The.Movie.2023.1080p.mkv", "the movie 2023 1080p.French.srt").single().languageTag).isEqualTo("fr")
    }

    @Test
    fun `an episode finds its subtitles by its code and never another episode's`() {
        val found = matches("Show.S01E02.1080p.WEB.mkv", "Show S01E02 English.srt", "show.1x02.es.srt", "Show.S01E03.srt", "Show.srt")

        assertThat(found.map { it.path }).containsExactly("Show S01E02 English.srt", "show.1x02.es.srt")
        assertThat(found.map { it.languageTag }).containsExactly("en", "es")
    }

    @Test
    fun `a release's Subs folder for this video is taken whole`() {
        val found =
            matches(
                "Show.S01E02.mkv",
                "Subs/Show.S01E02/2_English.srt",
                "Subs/Show.S01E02/3_Spanish.srt",
                ownFolder = setOf("Subs/Show.S01E02/2_English.srt", "Subs/Show.S01E02/3_Spanish.srt"),
            )

        assertThat(found.map { it.languageTag }).containsExactly("en", "es").inOrder()
    }

    @Test
    fun `a movie alone in its folder takes any subtitle file there`() {
        assertThat(matches("Film.2020.mkv", "English.srt", "Subs/eng.srt", videosInFolder = 1).map { it.languageTag })
            .containsExactly("en", "en")
        assertThat(matches("Film.2020.mkv", "English.srt", videosInFolder = 2)).isEmpty()
    }

    @Test
    fun `the language after the name becomes a tag, markers are skipped`() {
        assertThat(subtitleLanguageOf(listOf("pt_BR"))).isEqualTo("pt-BR")
        assertThat(subtitleLanguageOf(listOf("forced", "eng"))).isEqualTo("en")
        assertThat(subtitleLanguageOf(listOf("SDH"))).isEmpty()
        assertThat(subtitleLanguageOf(listOf("2", "Français"))).isEqualTo("fr")
        // Away from the video's name a code only counts at the end, so title words stay words.
        assertThat(subtitleLanguageOf(listOf("de", "la", "vida", "final"), codesAnywhere = false)).isEmpty()
    }

    @Test
    fun `only text that is not unicode is copied for the player`() {
        assertThat(isUnicodeText("Blåbær".toByteArray())).isTrue()
        assertThat(isUnicodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0))).isTrue()
        assertThat(isUnicodeText("مرحبا".toByteArray(Charset.forName("windows-1256")))).isFalse()
    }

    @Test
    fun `a folder maps to the storage provider's document id`() {
        assertThat(externalStorageDocumentId("/storage/emulated/0/Movies/Film")).isEqualTo("primary:Movies/Film")
        assertThat(externalStorageDocumentId("/storage/emulated/0")).isEqualTo("primary:")
        assertThat(externalStorageDocumentId("/storage/1A2B-3C4D/Series/Show")).isEqualTo("1A2B-3C4D:Series/Show")
        assertThat(externalStorageDocumentId("/data/user/0/app")).isNull()
    }
}
