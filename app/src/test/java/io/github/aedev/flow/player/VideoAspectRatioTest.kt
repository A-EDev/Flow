package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoAspectRatioTest {
    @Test
    fun `ultrawide display ratio is not limited to two to one`() {
        assertThat(sanitizeDisplayAspectRatio(2.35f)).isWithin(0.001f).of(2.35f)
    }

    @Test
    fun `display ratio preserves source dimensions without an arbitrary clamp`() {
        assertThat(sanitizeDisplayAspectRatio(3.2f)).isWithin(0.001f).of(3.2f)
    }

    @Test
    fun `highest resolution determines source ratio when low rendition is rounded`() {
        val dimensions = listOf(
            256 to 128,
            426 to 182,
            1920 to 818,
            3840 to 1636,
        )

        assertThat(sourceVideoAspectRatio(dimensions))
            .isWithin(0.0001f)
            .of(3840f / 1636f)
    }

    @Test
    fun `invalid display ratio falls back to widescreen`() {
        assertThat(sanitizeDisplayAspectRatio(Float.NaN))
            .isWithin(0.001f)
            .of(DEFAULT_VIDEO_ASPECT_RATIO)
    }

    @Test
    fun `pip ratio stays inside platform ultrawide limit`() {
        assertThat(sanitizePipAspectRatio(2.5f)).isWithin(0.001f).of(2.39f)
    }

    @Test
    fun `pip preserves supported ultrawide ratio`() {
        assertThat(sanitizePipAspectRatio(2.35f)).isWithin(0.001f).of(2.35f)
    }
}
