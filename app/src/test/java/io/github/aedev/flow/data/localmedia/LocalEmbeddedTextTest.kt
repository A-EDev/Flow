package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalEmbeddedTextTest {
    private val damaged =
        LocalMediaItem(
            id = 1L,
            isVideo = true,
            contentUri = "content://media/external/video/media/1",
            title = "Sn??lla ge oss en st??d like f??r detta ???????? #dance #viral",
            fileName = "Linnea Viola - Snälla ge oss en stöd like för detta #dance #viral.mp4",
            durationMs = 12_000L,
            sizeBytes = 1_870_850L,
            dateAddedMs = 0L,
            modifiedMs = 1_000L,
            artist = "Linnea & Viola",
        )

    @Test
    fun `the file's own title replaces the damaged one exactly`() {
        val title = "Snälla ge oss en stöd like för detta 😭💀 #dance #viral"

        val shown = damaged.withEmbeddedText(EmbeddedText(title = title))

        assertThat(shown.title).isEqualTo(title)
        assertThat(shown.artist).isEqualTo("Linnea & Viola")
        assertThat(shown.searchText).contains("😭💀")
    }

    @Test
    fun `fields the file lacks keep what MediaStore had`() {
        assertThat(damaged.withEmbeddedText(EmbeddedText())).isSameInstanceAs(damaged)
    }

    @Test
    fun `a renamed file without a title of its own takes its new name`() {
        val renamed = damaged.copy(title = "VID_0001", fileName = "Holiday in Rome.mp4")

        assertThat(renamed.withEmbeddedText(EmbeddedText(), titleless = true).title).isEqualTo("Holiday in Rome")
        assertThat(renamed.withEmbeddedText(EmbeddedText(title = "Roma"), titleless = true).title).isEqualTo("Roma")
        assertThat(renamed.withEmbeddedText(EmbeddedText()).title).isEqualTo("VID_0001")
    }

    @Test
    fun `a file name gives a title without its extension`() {
        assertThat(fileNameTitle("Holiday.in.Rome.mkv")).isEqualTo("Holiday.in.Rome")
        assertThat(fileNameTitle(".mp4")).isNull()
        assertThat(fileNameTitle(null)).isNull()
    }

    @Test
    fun `question marks and replacement characters look damaged`() {
        assertThat(damaged.looksDamaged()).isTrue()
        assertThat(damaged.copy(title = "Sn�lla").looksDamaged()).isTrue()
        assertThat(damaged.copy(title = "Snälla").looksDamaged()).isFalse()
    }

    @Test
    fun `a rewritten file gets a new stamp`() {
        assertThat(damaged.copy(modifiedMs = 2_000L).fileStamp).isNotEqualTo(damaged.fileStamp)
        assertThat(damaged.copy(sizeBytes = 1L).fileStamp).isNotEqualTo(damaged.fileStamp)
    }

    @Test
    fun `writer padding is dropped and empty fields read as missing`() {
        assertThat(embeddedField("Linnea & Viola\u0000")).isEqualTo("Linnea & Viola")
        assertThat(embeddedField("  ")).isNull()
        assertThat(embeddedField(null)).isNull()
    }
}
