package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackResumePolicyTest {
    @Test
    fun `queue transition always starts from beginning`() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 180_000L,
            durationMs = 240_000L,
            resumeAllowed = false
        )

        assertThat(startPosition).isEqualTo(0L)
    }

    @Test
    fun `direct playback resumes an unfinished video`() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 120_000L,
            durationMs = 240_000L,
            resumeAllowed = true
        )

        assertThat(startPosition).isEqualTo(120_000L)
    }

    @Test
    fun `direct playback restarts a completed video`() {
        val startPosition = PlaybackResumePolicy.resolveStartPosition(
            savedPosition = 239_000L,
            durationMs = 240_000L,
            resumeAllowed = true
        )

        assertThat(startPosition).isEqualTo(0L)
    }
}
