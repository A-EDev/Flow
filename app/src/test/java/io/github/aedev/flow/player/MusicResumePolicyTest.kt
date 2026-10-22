package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicResumePolicyTest {
    private val episode = 45 * 60_000L

    @Test
    fun `a long track resumes where it stopped`() {
        assertThat(MusicResumePolicy.startPosition(savedPosition = 23 * 60_000L, durationMs = episode)).isEqualTo(23 * 60_000L)
    }

    @Test
    fun `a song always starts over`() {
        assertThat(MusicResumePolicy.startPosition(savedPosition = 90_000L, durationMs = 4 * 60_000L)).isEqualTo(0L)
    }

    @Test
    fun `ten minutes is long enough`() {
        assertThat(MusicResumePolicy.isLongTrack(10 * 60_000L)).isTrue()
        assertThat(MusicResumePolicy.isLongTrack(10 * 60_000L - 1)).isFalse()
    }

    @Test
    fun `a finished track starts over`() {
        assertThat(MusicResumePolicy.startPosition(savedPosition = episode, durationMs = episode)).isEqualTo(0L)
    }

    @Test
    fun `an unknown length never resumes`() {
        assertThat(MusicResumePolicy.startPosition(savedPosition = 60_000L, durationMs = 0L)).isEqualTo(0L)
    }
}
