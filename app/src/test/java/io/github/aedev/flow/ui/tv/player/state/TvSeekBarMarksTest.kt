package io.github.aedev.flow.ui.tv.player.state

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockSegment
import org.junit.Test

class TvSeekBarMarksTest {

    private fun segment(start: Float, end: Float, category: String = "sponsor") =
        SponsorBlockSegment(
            category = category,
            segment = listOf(start, end),
            uuid = "$category-$start",
            actionType = "skip",
        )

    @Test
    fun `zero duration yields empty marks`() {
        val marks = TvSeekBarMarks.from(
            chapterStartSeconds = listOf(10, 20),
            sponsorSegments = listOf(segment(1f, 2f)),
            durationMs = 0L,
        )
        assertThat(marks.isEmpty()).isTrue()
    }

    @Test
    fun `chapter fractions are normalized and zero start is dropped`() {
        val marks = TvSeekBarMarks.from(
            chapterStartSeconds = listOf(0, 30, 60),
            sponsorSegments = emptyList(),
            durationMs = 120_000L,
        )
        assertThat(marks.chapterFractions).containsExactly(0.25f, 0.5f).inOrder()
    }

    @Test
    fun `chapters past the duration are dropped`() {
        val marks = TvSeekBarMarks.from(
            chapterStartSeconds = listOf(30, 500),
            sponsorSegments = emptyList(),
            durationMs = 120_000L,
        )
        assertThat(marks.chapterFractions).containsExactly(0.25f)
    }

    @Test
    fun `segments map to clamped fraction ranges with category`() {
        val marks = TvSeekBarMarks.from(
            chapterStartSeconds = emptyList(),
            sponsorSegments = listOf(
                segment(30f, 60f, category = "selfpromo"),
                segment(100f, 500f),
            ),
            durationMs = 120_000L,
        )
        assertThat(marks.segments).hasSize(2)
        assertThat(marks.segments[0].startFraction).isEqualTo(0.25f)
        assertThat(marks.segments[0].endFraction).isEqualTo(0.5f)
        assertThat(marks.segments[0].category).isEqualTo("selfpromo")
        assertThat(marks.segments[1].endFraction).isEqualTo(1f)
    }

    @Test
    fun `inverted or out-of-range segments are dropped`() {
        val marks = TvSeekBarMarks.from(
            chapterStartSeconds = emptyList(),
            sponsorSegments = listOf(
                segment(60f, 30f),
                segment(500f, 600f),
            ),
            durationMs = 120_000L,
        )
        assertThat(marks.segments).isEmpty()
    }
}
