package io.github.aedev.flow.ui.components.musicplayer.lyrics

import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.lyrics.WordTimestamp
import org.junit.Test

class LyricsTimingTest {
    @Test
    fun `words fill the line in order and leave a breath before the next line`() {
        val words = synthesizeWordTimings("hold me close tonight", lineStartMs = 10_000L, nextLineMs = 13_000L)

        assertThat(words.map { it.text }).containsExactly("hold", "me", "close", "tonight").inOrder()
        assertThat(words.first().startTime).isEqualTo(10_000L)
        words.zipWithNext().forEach { (a, b) -> assertThat(b.startTime).isEqualTo(a.endTime) }
        assertThat(words.last().endTime).isLessThan(13_000L)
        assertThat(words.last().endTime).isAtLeast(12_500L)
    }

    @Test
    fun `longer words take longer than short ones`() {
        val words = synthesizeWordTimings("I remember", lineStartMs = 0L, nextLineMs = 2_000L)

        val short = words[0].endTime - words[0].startTime
        val long = words[1].endTime - words[1].startTime
        assertThat(long).isGreaterThan(short * 2)
    }

    @Test
    fun `a long gap after the line becomes a pause instead of a slow fill`() {
        val words = synthesizeWordTimings("oh", lineStartMs = 0L, nextLineMs = 30_000L)

        assertThat(words.single().endTime).isAtMost(650L)
    }

    @Test
    fun `the last line without a next line still gets a natural length`() {
        val words = synthesizeWordTimings("goodbye my love", lineStartMs = 5_000L, nextLineMs = null)

        val sung = words.last().endTime - words.first().startTime
        assertThat(sung).isIn(Range.closed(600L, 2_000L))
    }

    @Test
    fun `blank lines have no words`() {
        assertThat(synthesizeWordTimings("   ", 0L, 1_000L)).isEmpty()
    }

    @Test
    fun `chinese without spaces fills per character with punctuation attached`() {
        assertThat(lyricTokens("我爱你，")).containsExactly("我", "爱", "你，").inOrder()
    }

    @Test
    fun `syllables are counted from vowel groups, accents ignored`() {
        assertThat(syllableWeight("beautiful")).isEqualTo(3)
        assertThat(syllableWeight("corazón")).isEqualTo(3)
        assertThat(syllableWeight("!")).isEqualTo(1)
    }

    @Test
    fun `scripts without latin vowels count about a syllable per three letters`() {
        assertThat(syllableWeight("حبيبي")).isEqualTo(2)
    }

    @Test
    fun `word ranges are found in order and repeated words map to their own place`() {
        val text = "la la land"
        val words = listOf(WordTimestamp("la", 0, 1), WordTimestamp("la", 1, 2), WordTimestamp("land", 2, 3))

        assertThat(wordCharRanges(text, words, isBackground = false)).containsExactly(0..1, 3..4, 6..9).inOrder()
    }

    @Test
    fun `background words match without the stripped parentheses`() {
        val words = listOf(WordTimestamp("(ooh", 0, 1), WordTimestamp("yeah)", 1, 2))

        assertThat(wordCharRanges("ooh yeah", words, isBackground = true)).containsExactly(0..2, 4..7).inOrder()
    }

    @Test
    fun `a word missing from the text has no range and does not stop the rest`() {
        val words = listOf(WordTimestamp("hello", 0, 1), WordTimestamp("xyz", 1, 2), WordTimestamp("world", 2, 3))

        assertThat(wordCharRanges("hello world", words, isBackground = false)).containsExactly(0..4, null, 6..10).inOrder()
    }

    @Test
    fun `the word lift springs past its rest and settles`() {
        assertThat(wordLiftProgress(0L)).isEqualTo(0f)
        assertThat((50L..400L step 10L).maxOf { wordLiftProgress(it) }).isGreaterThan(1f)
        assertThat(wordLiftProgress(2_000L)).isWithin(0.01f).of(1f)
    }

    @Test
    fun `row cells meet halfway between their ink and the outer ones reach past the row`() {
        val bounds = rowCellBounds(inkLefts = listOf(10f, 60f, 90f), inkRights = listOf(50f, 80f, 120f), width = 200f)

        assertThat(bounds.toList()).containsExactly(-200f, 55f, 55f, 85f, 85f, 400f).inOrder()
    }
}
