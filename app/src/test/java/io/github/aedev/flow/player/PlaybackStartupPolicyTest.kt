package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaybackStartupPolicyTest {
    @Test
    fun `secondary content waits only for matching active playback`() {
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "current",
            ),
        ).isTrue()
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "other",
            ),
        ).isFalse()
    }
}
