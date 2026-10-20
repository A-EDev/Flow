package io.github.aedev.flow.player.audio

import android.media.audiofx.AudioEffect
import androidx.media3.exoplayer.ExoPlayer
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AudioSessionAnnouncerTest {
    private val registry = AudioSessionRegistry(mockk(relaxed = true))

    private fun player(sessionId: Int): ExoPlayer =
        mockk(relaxed = true) {
            every { audioSessionId } returns sessionId
            every { isPlaying } returns false
        }

    @Test
    fun `the id ExoPlayer reports after build is opened`() {
        val ids = mutableListOf<Int>()
        val announcer = AudioSessionAnnouncer(registry, AudioEffect.CONTENT_TYPE_MOVIE) { ids += it }
        announcer.attach(player(0))

        assertThat(registry.activeSessionId.value).isEqualTo(AudioEffect.ERROR_BAD_VALUE)

        announcer.onAudioSessionIdChanged(41)

        assertThat(registry.activeSessionId.value).isEqualTo(41)
        assertThat(registry.contentTypeOf(41)).isEqualTo(AudioEffect.CONTENT_TYPE_MOVIE)
        assertThat(ids).containsExactly(41)
    }

    @Test
    fun `a changed id closes the old session`() {
        val announcer = AudioSessionAnnouncer(registry, AudioEffect.CONTENT_TYPE_MUSIC)
        announcer.attach(player(41))
        announcer.onAudioSessionIdChanged(42)

        assertThat(registry.contentTypeOf(41)).isNull()
        assertThat(registry.activeSessionId.value).isEqualTo(42)
    }

    @Test
    fun `the player that starts playing takes the system equalizer`() {
        val video = AudioSessionAnnouncer(registry, AudioEffect.CONTENT_TYPE_MOVIE).apply { attach(player(41)) }
        AudioSessionAnnouncer(registry, AudioEffect.CONTENT_TYPE_MUSIC).apply { attach(player(42)) }

        assertThat(registry.activeSessionId.value).isEqualTo(42)

        video.onIsPlayingChanged(true)

        assertThat(registry.activeSessionId.value).isEqualTo(41)
    }

    @Test
    fun `detach removes the listener and closes the session`() {
        val exo = player(41)
        val announcer = AudioSessionAnnouncer(registry, AudioEffect.CONTENT_TYPE_MOVIE)
        announcer.attach(exo)
        announcer.detach()

        verify { exo.removeListener(announcer) }
        assertThat(registry.contentTypeOf(41)).isNull()
        assertThat(registry.activeSessionId.value).isEqualTo(AudioEffect.ERROR_BAD_VALUE)
    }
}
