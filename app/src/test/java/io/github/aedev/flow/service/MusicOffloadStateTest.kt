package io.github.aedev.flow.service

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicOffloadStateTest {
    @Test
    fun `untouched sound while playing may offload`() {
        assertThat(MusicOffloadState().allowed).isTrue()
    }

    @Test
    fun `an equalizer that changes the sound keeps offload off`() {
        assertThat(MusicOffloadState(processing = true).allowed).isFalse()
    }

    @Test
    fun `a changed speed or pitch keeps offload off`() {
        assertThat(MusicOffloadState(playbackChanged = true).allowed).isFalse()
    }

    @Test
    fun `a long pause gives offload up until playback resumes`() {
        val paused = MusicOffloadState(pausedLong = true)

        assertThat(paused.allowed).isFalse()
        assertThat(paused.copy(pausedLong = false).allowed).isTrue()
    }

    @Test
    fun `a speed a hair off one still counts as untouched`() {
        assertThat(MusicOffloadState.changesPlayback(speed = 0.99999994f, pitch = 1f)).isFalse()
        assertThat(MusicOffloadState.changesPlayback(speed = 1.25f, pitch = 1f)).isTrue()
        assertThat(MusicOffloadState.changesPlayback(speed = 1f, pitch = 1.0594631f)).isTrue()
    }
}
