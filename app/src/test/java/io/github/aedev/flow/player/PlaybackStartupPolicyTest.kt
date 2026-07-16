package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaybackStartupPolicyTest {
    @Test
    fun `progressive NewPipe stream can start without fallback`() {
        val readiness = PlaybackStartupPolicy.classifyNewPipeResult(
            hasProgressiveVideo = true,
            hasVideoOnly = false,
            hasAudio = false,
            hasDashManifest = false,
            hasHlsManifest = false,
            isKnownUpcoming = false
        )

        assertThat(readiness).isEqualTo(PlaybackResolverReadiness.PLAYABLE)
    }

    @Test
    fun `adaptive NewPipe streams require video and audio`() {
        val readiness = PlaybackStartupPolicy.classifyNewPipeResult(
            hasProgressiveVideo = false,
            hasVideoOnly = true,
            hasAudio = false,
            hasDashManifest = false,
            hasHlsManifest = false,
            isKnownUpcoming = false
        )

        assertThat(readiness).isEqualTo(PlaybackResolverReadiness.NEEDS_FALLBACK)
    }

    @Test
    fun `known upcoming result does not wait for another resolver`() {
        val readiness = PlaybackStartupPolicy.classifyNewPipeResult(
            hasProgressiveVideo = false,
            hasVideoOnly = false,
            hasAudio = false,
            hasDashManifest = false,
            hasHlsManifest = false,
            isKnownUpcoming = true
        )

        assertThat(readiness).isEqualTo(PlaybackResolverReadiness.TERMINAL_WITHOUT_PLAYBACK)
    }

    @Test
    fun `first completed playback resolver wins`() = runTest {
        val primary = CompletableDeferred<String>()
        val fallback = CompletableDeferred("innertube")

        val winner = awaitFirstPlaybackResolver(primary, fallback)

        assertThat(winner).isEqualTo(PlaybackResolverWinner.Fallback("innertube"))
    }

    @Test
    fun `secondary content waits only for matching active playback`() {
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "current"
            )
        ).isTrue()
        assertThat(
            PlaybackStartupPolicy.shouldDelaySecondaryContent(
                isPlaybackLoading = true,
                currentVideoId = "current",
                requestedVideoId = "other"
            )
        ).isFalse()
    }
}
