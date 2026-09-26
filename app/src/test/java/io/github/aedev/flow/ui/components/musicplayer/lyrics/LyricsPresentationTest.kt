package io.github.aedev.flow.ui.components.musicplayer.lyrics

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.lyrics.LyricsEntry
import org.junit.Test

class LyricsPresentationTest {
    @Test
    fun `duet singers sit on opposite sides in order of appearance`() {
        val lines =
            listOf(
                LyricsEntry(0L, "a", agent = "v2"),
                LyricsEntry(1_000L, "b", agent = "v1"),
                LyricsEntry(2_000L, "c", agent = "v2"),
                LyricsEntry(3_000L, "together", agent = "v1000"),
            )

        assertThat(duetSides(lines))
            .containsExactly(DuetSide.START, DuetSide.END, DuetSide.START, DuetSide.BOTH)
            .inOrder()
    }

    @Test
    fun `background vocals follow the line before them and do not count as a singer`() {
        val lines =
            listOf(
                LyricsEntry(0L, "a", agent = "v1"),
                LyricsEntry(500L, "(ooh)", agent = "bg", isBackground = true),
                LyricsEntry(1_000L, "b", agent = "v2"),
                LyricsEntry(1_500L, "(aah)", agent = "bg", isBackground = true),
            )

        assertThat(duetSides(lines))
            .containsExactly(DuetSide.START, DuetSide.START, DuetSide.END, DuetSide.END)
            .inOrder()
    }

    @Test
    fun `a single singer is not a duet`() {
        val lines = listOf(LyricsEntry(0L, "a", agent = "v1"), LyricsEntry(1_000L, "b", agent = "v1"))

        assertThat(duetSides(lines)).isNull()
    }

    @Test
    fun `each line is bounded by the next main line, skipping background vocals`() {
        val lines =
            listOf(
                LyricsEntry(0L, "a"),
                LyricsEntry(500L, "(bg)", isBackground = true),
                LyricsEntry(2_000L, "b"),
            )

        assertThat(nextMainLineTimes(lines)).containsExactly(2_000L, 2_000L, null).inOrder()
    }

    @Test
    fun `only lines with letters outside the latin script need romanizing`() {
        assertThat(needsRomanization("Hello, world")).isFalse()
        assertThat(needsRomanization("Café 123")).isFalse()
        assertThat(needsRomanization("사랑해")).isTrue()
        assertThat(needsRomanization("love you 愛してる")).isTrue()
    }

    @Test
    fun `the source romanization shows unless romanization is turned off`() {
        val lines = listOf(LyricsEntry(0L, "사랑해", romanization = "saranghae"), LyricsEntry(1_000L, "hello"))

        assertThat(lyricsRomanizations(lines, LyricsDisplayOptions())).containsExactly("saranghae", null).inOrder()
        assertThat(lyricsRomanizations(lines, LyricsDisplayOptions(showRomanization = false))).containsExactly(null, null)
    }
}
