package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.mockk.mockk
import org.junit.Test

/**
 * Pins what the player screen takes from outside itself: the queue title it mirrors, the video id a
 * foreign player state makes it load, the live chat fold, and the two feed invalidations that prune
 * the related lane.
 */
class PlayerStateMirrorTest {
    @Test
    fun `the queue title is the only player state field mirrored straight onto the screen`() {
        val before = VideoPlayerUiState(cachedVideo = video("vid_a"), isLoading = true)

        val next = before.mirrorPlayerState(EnhancedPlayerState(currentVideoId = "vid_b", queueTitle = "My mix", isPrepared = true))

        assertThat(next).isEqualTo(before.copy(queueTitle = "My mix"))
    }

    @Test
    fun `a player on a video the screen knows nothing about needs a load`() {
        val needed = VideoPlayerUiState().foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "ext_1"))

        assertThat(needed).isEqualTo("ext_1")
    }

    @Test
    fun `a player on no video at all needs nothing`() {
        assertThat(VideoPlayerUiState().foreignVideoIdNeedingLoad(EnhancedPlayerState())).isNull()
    }

    @Test
    fun `the video already cached and streaming needs no load`() {
        val state = VideoPlayerUiState(cachedVideo = video("vid_a"), streamInfo = mockk(relaxed = true))

        val needed = state.foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "vid_a", isPrepared = true))

        assertThat(needed).isNull()
    }

    @Test
    fun `the cached video with no streams and an idle player is reloaded`() {
        val state = VideoPlayerUiState(cachedVideo = video("vid_a"))

        val needed = state.foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "vid_a"))

        assertThat(needed).isEqualTo("vid_a")
    }

    @Test
    fun `a load already in flight is never interrupted`() {
        val state = VideoPlayerUiState(isLoading = true)

        assertThat(state.foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "ext_1"))).isNull()
    }

    @Test
    fun `a restored session is left alone until the player has actually started it`() {
        val restored = VideoPlayerUiState(cachedVideo = video("vid_a"), isRestoredSession = true)

        val whileStreaming =
            restored.foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "ext_1", isPrepared = true))
        val whileIdle = restored.foreignVideoIdNeedingLoad(EnhancedPlayerState(currentVideoId = "ext_1"))

        assertThat(whileStreaming).isNull()
        assertThat(whileIdle).isEqualTo("ext_1")
    }

    @Test
    fun `the live chat fold writes the transcript and both flags`() {
        val next = VideoPlayerUiState().applyLiveChat(emptyList(), isLoading = true, isAvailable = true)

        assertThat(next.liveChatMessages).isEmpty()
        assertThat(next.isLiveChatLoading).isTrue()
        assertThat(next.isLiveChatAvailable).isTrue()
    }

    @Test
    fun `a not-interested event drops only that video from the related lane`() {
        val state = VideoPlayerUiState(relatedVideos = listOf(video("rel_1"), video("rel_2")))

        val next = state.applyFeedInvalidation(FeedInvalidationBus.Event.NotInterested(videoId = "rel_1", channelId = "channel_rel_1"))

        assertThat(next.relatedVideos.map { it.id }).containsExactly("rel_2")
    }

    @Test
    fun `a channel block drops the video and everything else from that channel`() {
        val state =
            VideoPlayerUiState(
                relatedVideos =
                    listOf(
                        video("rel_1", channelId = "ch_blocked"),
                        video("rel_2", channelId = "ch_blocked"),
                        video("rel_3", channelId = "ch_other"),
                    ),
            )

        val next = state.applyFeedInvalidation(FeedInvalidationBus.Event.ChannelBlocked(channelId = "ch_blocked", videoId = "rel_1"))

        assertThat(next.relatedVideos.map { it.id }).containsExactly("rel_3")
    }

    private fun video(
        id: String,
        channelId: String = "channel_$id",
    ): Video =
        Video(
            id = id,
            title = "Title $id",
            channelName = "Channel $id",
            channelId = channelId,
            thumbnailUrl = "https://example.invalid/$id.jpg",
            duration = 120,
            viewCount = 1L,
            uploadDate = "2026-01-01",
        )
}
