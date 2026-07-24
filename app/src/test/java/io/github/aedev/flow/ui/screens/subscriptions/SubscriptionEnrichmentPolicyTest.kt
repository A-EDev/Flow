package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionEnrichmentPolicyTest {
    @Test
    fun windowContainsVisibleVideosAndBoundedLookahead() {
        val videos = (0 until 20).map(::video)

        val result = visibleSubscriptionEnrichmentWindow(
            videos = videos,
            visibleVideoIds = setOf("video-3", "video-4"),
            lookahead = 4,
            maxCandidates = 12,
        )

        assertEquals(
            listOf("video-3", "video-4", "video-5", "video-6", "video-7", "video-8"),
            result.map { it.id },
        )
    }

    @Test
    fun windowNeverExceedsCandidateLimit() {
        val videos = (0 until 30).map(::video)

        val result = visibleSubscriptionEnrichmentWindow(
            videos = videos,
            visibleVideoIds = videos.mapTo(HashSet()) { it.id },
        )

        assertEquals(MAX_VISIBLE_DURATION_CANDIDATES, result.size)
    }

    @Test
    fun windowIsEmptyWhenNoFeedVideoIsVisible() {
        val result = visibleSubscriptionEnrichmentWindow(
            videos = (0 until 10).map(::video),
            visibleVideoIds = setOf("header"),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun durationCandidatesExcludeKnownAndIneligibleVideos() {
        val now = 100_000L
        val videos = listOf(
            video(0),
            video(1).copy(duration = 120),
            video(2).copy(isLive = true),
            video(3).copy(isUpcoming = true),
            video(4),
        )

        val result = missingDurationCandidates(
            videos = videos,
            attemptedAtMillis = mapOf("video-0" to 99_500L),
            nowMillis = now,
            retryAfterMillis = 1_000L,
        )

        assertEquals(listOf("video-4"), result.map { it.id })
    }

    @Test
    fun failedDurationCanRetryAfterCooldown() {
        val result = missingDurationCandidates(
            videos = listOf(video(0)),
            attemptedAtMillis = mapOf("video-0" to 10_000L),
            nowMillis = 11_001L,
            retryAfterMillis = 1_000L,
        )

        assertEquals(listOf("video-0"), result.map { it.id })
    }

    private fun video(index: Int) = Video(
        id = "video-$index",
        title = "Video $index",
        channelName = "Channel",
        channelId = "channel",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
    )
}
