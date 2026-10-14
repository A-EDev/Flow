package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.MusicMutePause.Action
import org.junit.Test

class MusicMutePauseTest {
    @Test
    fun `muting pauses playing music when the setting is on`() {
        assertThat(MusicMutePause.onVolume(silent = true, enabled = true, isPlaying = true, pausedForMute = false)).isEqualTo(Action.PAUSE)
        assertThat(MusicMutePause.onVolume(silent = true, enabled = false, isPlaying = true, pausedForMute = false)).isEqualTo(Action.NONE)
    }

    @Test
    fun `sound coming back resumes only music the mute paused`() {
        assertThat(
            MusicMutePause.onVolume(silent = false, enabled = true, isPlaying = false, pausedForMute = true),
        ).isEqualTo(Action.RESUME)
        assertThat(MusicMutePause.onVolume(silent = false, enabled = true, isPlaying = false, pausedForMute = false)).isEqualTo(Action.NONE)
    }

    @Test
    fun `turning the setting off still resumes what it paused`() {
        assertThat(
            MusicMutePause.onVolume(silent = false, enabled = false, isPlaying = false, pausedForMute = true),
        ).isEqualTo(Action.RESUME)
    }
}
