package io.github.aedev.flow.player.audio

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.junit.Test

class LoudnessGainTest {
    private fun config(
        loudnessDb: Double? = null,
        perceptual: Double? = null,
        target: Double? = null,
    ) = PlayerResponse.PlayerConfig.AudioConfig(loudnessDb = loudnessDb, perceptualLoudnessDb = perceptual, loudnessTargetLkfs = target)

    @Test
    fun `perceptual loudness is measured against the target`() {
        assertThat(LoudnessGain.gainDb(config(loudnessDb = 9.0, perceptual = -8.0), null)).isEqualTo(-6f)
        assertThat(LoudnessGain.gainDb(config(perceptual = -10.0, target = -16.0), null)).isEqualTo(-6f)
    }

    @Test
    fun `loudnessDb is next, then the format's own value`() {
        assertThat(LoudnessGain.gainDb(config(loudnessDb = 4.5), 9.0)).isEqualTo(-4.5f)
        assertThat(LoudnessGain.gainDb(null, 3.0)).isEqualTo(-3f)
    }

    @Test
    fun `quiet tracks are never boosted and loud ones stop at minus 20 dB`() {
        assertThat(LoudnessGain.gainDb(config(loudnessDb = -7.0), null)).isEqualTo(0f)
        assertThat(LoudnessGain.gainDb(config(loudnessDb = 35.0), null)).isEqualTo(-20f)
    }

    @Test
    fun `no metadata means no gain`() {
        assertThat(LoudnessGain.gainDb(null, null)).isNull()
        assertThat(LoudnessGain.volume(null)).isEqualTo(1f)
    }

    @Test
    fun `minus 6 dB is about half the amplitude`() {
        assertThat(LoudnessGain.volume(-6f).toDouble()).isWithin(0.01).of(0.501)
    }
}
