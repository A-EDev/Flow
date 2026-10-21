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

    private val missingPiece =
        listOf(
            LyricsEntry(0L, "また掴めずに空に消えて行くreason to be"),
            LyricsEntry(1_000L, "引き裂かれた 背中の my broken wings"),
            LyricsEntry(2_000L, "全て"),
            LyricsEntry(3_000L, "cry my heart out"),
        )

    @Test
    fun `a song with kana is Japanese, kanji-only lines and all (#1352)`() {
        assertThat(isJapaneseLyrics(missingPiece.map { it.text })).isTrue()
        assertThat(isJapaneseLyrics(listOf("我爱你 baby", "你的笑容", "春天的花の香"))).isFalse()
        assertThat(isJapaneseLyrics(listOf("사랑해", "hello"))).isFalse()
        assertThat(isJapaneseLyrics(emptyList())).isFalse()
    }

    @Test
    fun `Japanese lyrics get no on-device line, other scripts still do`() {
        val asked = mutableListOf<String>()
        val fake: (String) -> String? = {
            asked += it
            "roman"
        }
        val auto = LyricsDisplayOptions(autoRomanize = true)

        assertThat(lyricsRomanizations(missingPiece, auto, fake)).containsExactly(null, null, null, null)
        assertThat(asked).isEmpty()

        val chinese = listOf(LyricsEntry(0L, "我爱你 baby"))
        assertThat(lyricsRomanizations(chinese, auto, fake)).containsExactly("roman")
    }

    @Test
    fun `a source romanization still shows for Japanese`() {
        val lines = missingPiece.mapIndexed { i, line -> if (i == 1) line.copy(romanization = "hikisakareta senaka no") else line }

        assertThat(lyricsRomanizations(lines, LyricsDisplayOptions(autoRomanize = true)) { "roman" })
            .containsExactly(null, "hikisakareta senaka no", null, null)
            .inOrder()
    }

    @Test
    fun `a space goes where Latin meets another script, and nowhere else`() {
        assertThat(spaceBetweenScripts("消えて行くreason to be")).isEqualTo("消えて行く reason to be")
        assertThat(spaceBetweenScripts("사랑해baby")).isEqualTo("사랑해 baby")
        assertThat(spaceBetweenScripts("引き裂かれた")).isEqualTo("引き裂かれた")
        assertThat(spaceBetweenScripts("Café 123")).isEqualTo("Café 123")
    }
}
