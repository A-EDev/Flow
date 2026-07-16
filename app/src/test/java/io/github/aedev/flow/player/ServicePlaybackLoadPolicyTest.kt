package io.github.aedev.flow.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePlaybackLoadPolicyTest {
    @Test
    fun `service commits while it still owns an unprepared target`() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-b",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.COMMIT, decision)
    }

    @Test
    fun `service does not replace playback prepared by the ui`() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-b",
            isPreparedForRequestedVideo = true,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_ALREADY_PREPARED, decision)
    }

    @Test
    fun `service does not commit after player advances again`() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-c",
            globalVideoId = "video-c",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_STALE_REQUEST, decision)
    }

    @Test
    fun `service does not commit after global selection changes`() {
        val decision = ServicePlaybackLoadPolicy.decide(
            requestedVideoId = "video-b",
            playerVideoId = "video-b",
            globalVideoId = "video-c",
            isPreparedForRequestedVideo = false,
        )

        assertEquals(ServicePlaybackCommitDecision.SKIP_STALE_REQUEST, decision)
    }
}
