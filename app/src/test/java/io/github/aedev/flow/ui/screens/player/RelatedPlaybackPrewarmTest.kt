package io.github.aedev.flow.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RelatedPlaybackPrewarmTest {
    @Test
    fun `prewarm waits for meaningful dwell`() {
        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "video",
                positionMs = 19_000L,
                durationMs = 120_000L,
                isShort = false,
                isLocal = false,
                alreadyPrewarmed = false
            )
        ).isFalse()
    }

    @Test
    fun `prewarm starts after twenty seconds or thirty five percent`() {
        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "twenty_seconds",
                positionMs = 20_000L,
                durationMs = 120_000L,
                isShort = false,
                isLocal = false,
                alreadyPrewarmed = false
            )
        ).isTrue()

        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "strong_partial",
                positionMs = 14_000L,
                durationMs = 40_000L,
                isShort = false,
                isLocal = false,
                alreadyPrewarmed = false
            )
        ).isTrue()
    }

    @Test
    fun `prewarm skips shorts local media and already warmed videos`() {
        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "short",
                positionMs = 30_000L,
                durationMs = 60_000L,
                isShort = true,
                isLocal = false,
                alreadyPrewarmed = false
            )
        ).isFalse()

        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "local_1",
                positionMs = 30_000L,
                durationMs = 60_000L,
                isShort = false,
                isLocal = true,
                alreadyPrewarmed = false
            )
        ).isFalse()

        assertThat(
            shouldPrewarmRelatedPlayback(
                videoId = "already",
                positionMs = 30_000L,
                durationMs = 60_000L,
                isShort = false,
                isLocal = false,
                alreadyPrewarmed = true
            )
        ).isFalse()
    }
}
