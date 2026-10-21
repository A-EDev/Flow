package io.github.aedev.flow.player.audio

import android.media.audiofx.AudioEffect
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class AudioSessionRegistryTest {
    private val registry = AudioSessionRegistry(mockk(relaxed = true))
    private val music = AudioEffect.CONTENT_TYPE_MUSIC
    private val movie = AudioEffect.CONTENT_TYPE_MOVIE

    @Test
    fun `session id 0 is never opened`() {
        registry.open(0, music)
        registry.playing(0)

        assertThat(registry.activeSessionId.value).isEqualTo(AudioEffect.ERROR_BAD_VALUE)
        assertThat(registry.contentTypeOf(0)).isNull()
    }

    @Test
    fun `a session that appears while nothing plays becomes active`() {
        registry.open(11, music)

        assertThat(registry.activeSessionId.value).isEqualTo(11)
    }

    @Test
    fun `a new idle session does not take over from one that is playing`() {
        registry.open(11, movie)
        registry.playing(11)
        registry.open(22, music)

        assertThat(registry.activeSessionId.value).isEqualTo(11)
    }

    @Test
    fun `the session that starts playing becomes active`() {
        registry.open(11, movie)
        registry.open(22, music)
        registry.playing(11)
        registry.paused(11)
        registry.playing(22)

        assertThat(registry.activeSessionId.value).isEqualTo(22)
        assertThat(registry.contentTypeOf(22)).isEqualTo(music)
    }

    @Test
    fun `closing the active session falls back to one still playing`() {
        registry.open(11, movie)
        registry.open(22, movie)
        registry.open(33, music)
        registry.playing(22)
        registry.playing(33)
        registry.close(33)

        assertThat(registry.activeSessionId.value).isEqualTo(22)
    }

    @Test
    fun `closing the last session clears the active one`() {
        registry.open(11, music)
        registry.close(11)

        assertThat(registry.activeSessionId.value).isEqualTo(AudioEffect.ERROR_BAD_VALUE)
    }

    @Test
    fun `playing a session that is not open changes nothing`() {
        registry.open(11, music)
        registry.playing(99)

        assertThat(registry.activeSessionId.value).isEqualTo(11)
    }
}
