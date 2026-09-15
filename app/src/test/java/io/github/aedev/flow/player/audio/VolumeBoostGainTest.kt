package io.github.aedev.flow.player.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VolumeBoostGainTest {
    @Test
    fun `no gain is asked for at or below unity`() {
        assertThat(boostGainMillibels(0f)).isEqualTo(0)
        assertThat(boostGainMillibels(0.5f)).isEqualTo(0)
        assertThat(boostGainMillibels(1f)).isEqualTo(0)
    }

    @Test
    fun `a boost above unity becomes amplifier gain in hundredths of a decibel`() {
        // Doubling the amplitude is +6.02 dB.
        assertThat(boostGainMillibels(2f)).isEqualTo(602)
        assertThat(boostGainMillibels(1.5f)).isEqualTo(352)
    }

    @Test
    fun `gain rises with the boost and is bounded`() {
        assertThat(boostGainMillibels(1.2f)).isGreaterThan(boostGainMillibels(1.1f))
        assertThat(boostGainMillibels(100f)).isAtMost(2000)
    }
}
