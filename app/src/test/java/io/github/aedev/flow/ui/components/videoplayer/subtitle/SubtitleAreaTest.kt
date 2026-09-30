package io.github.aedev.flow.ui.components.videoplayer.subtitle

import androidx.media3.common.text.Cue
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.subtitle.toRollingCaptionText
import org.junit.Test

class SubtitleAreaTest {
    @Test
    fun `a landscape video in a tall screen gets captions on the picture, not the screen edge`() {
        val area = subtitleArea(width = 400f, height = 900f, videoAspectRatio = 16f / 9f, resizeMode = 0)

        assertThat(area.width).isEqualTo(400f)
        assertThat(area.height).isEqualTo(225f)
        assertThat(area.top).isEqualTo(337.5f)
    }

    @Test
    fun `a vertical video in a wide screen is pillarboxed`() {
        val area = subtitleArea(width = 1600f, height = 900f, videoAspectRatio = 9f / 16f, resizeMode = 0)

        assertThat(area.height).isEqualTo(900f)
        assertThat(area.width).isEqualTo(506.25f)
        assertThat(area.left).isEqualTo(546.875f)
    }

    @Test
    fun `zoom, fill and an unknown ratio use the whole player`() {
        val whole = SubtitleArea(0f, 0f, 400f, 900f)

        assertThat(subtitleArea(400f, 900f, 16f / 9f, resizeMode = 2)).isEqualTo(whole)
        assertThat(subtitleArea(400f, 900f, 16f / 9f, resizeMode = 1)).isEqualTo(whole)
        assertThat(subtitleArea(400f, 900f, null, resizeMode = 0)).isEqualTo(whole)
    }

    @Test
    fun `a rolling auto caption keeps only the words the latest cue added`() {
        val cues = listOf(Cue.Builder().setText("so today we are\nso today we are going to talk").build())

        assertThat(cues.toRollingCaptionText()).isEqualTo("going to talk")
        assertThat(emptyList<Cue>().toRollingCaptionText()).isNull()
    }
}
