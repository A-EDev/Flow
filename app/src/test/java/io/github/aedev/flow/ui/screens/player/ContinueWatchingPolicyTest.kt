package io.github.aedev.flow.ui.screens.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import org.junit.Test

class ContinueWatchingPolicyTest {
    private fun createStubEntity(
        videoId: String = "video_123",
        timestamp: Long = 1000L,
        position: Long = 30_000L,
        duration: Long = 120_000L,
    ) = WatchHistoryEntity(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = timestamp,
        title = "Test Video",
        thumbnailUrl = "https://example.com/thumb.jpg",
        channelName = "Test Channel",
        channelId = "channel_123",
        isMusic = false,
        isShort = false,
        isLocal = false,
    )

    @Test
    fun `shouldRestoreContinueWatching returns false when lastVideo is null`() {
        val result =
            shouldRestoreContinueWatching(
                lastVideo = null,
                dismissedVideoId = "video_123",
                dismissedTimestamp = 1000L,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldRestoreContinueWatching returns false when video is dismissed and not rewatched`() {
        val entity = createStubEntity(videoId = "video_123", timestamp = 1000L)
        val result =
            shouldRestoreContinueWatching(
                lastVideo = entity,
                dismissedVideoId = "video_123",
                dismissedTimestamp = 1000L,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldRestoreContinueWatching returns false when dismissed timestamp is later than video timestamp`() {
        val entity = createStubEntity(videoId = "video_123", timestamp = 900L)
        val result =
            shouldRestoreContinueWatching(
                lastVideo = entity,
                dismissedVideoId = "video_123",
                dismissedTimestamp = 1000L,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `shouldRestoreContinueWatching returns true when video was rewatched after dismissal`() {
        val entity = createStubEntity(videoId = "video_123", timestamp = 1200L)
        val result =
            shouldRestoreContinueWatching(
                lastVideo = entity,
                dismissedVideoId = "video_123",
                dismissedTimestamp = 1000L,
            )
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRestoreContinueWatching returns true for a different video`() {
        val entity = createStubEntity(videoId = "different_video", timestamp = 1000L)
        val result =
            shouldRestoreContinueWatching(
                lastVideo = entity,
                dismissedVideoId = "video_123",
                dismissedTimestamp = 1000L,
            )
        assertThat(result).isTrue()
    }

    @Test
    fun `shouldRestoreContinueWatching returns true when dismissedVideoId is null`() {
        val entity = createStubEntity(videoId = "video_123", timestamp = 1000L)
        val result =
            shouldRestoreContinueWatching(
                lastVideo = entity,
                dismissedVideoId = null,
                dismissedTimestamp = 0L,
            )
        assertThat(result).isTrue()
    }
}
