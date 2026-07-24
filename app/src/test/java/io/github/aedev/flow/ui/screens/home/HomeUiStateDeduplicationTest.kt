package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeUiStateDeduplicationTest {

    @Test
    fun `home state exposes one item per lazy-layout identity`() {
        val state = HomeUiState(
            videos = listOf(video("video"), video("video"), video("")),
            shorts = listOf(video("short"), video("short")),
            continueWatchingVideos = listOf(history("history"), history("history"))
        )

        val result = state.withUniqueLazyContent()

        assertEquals(listOf("video"), result.videos.map(Video::id))
        assertEquals(listOf("short"), result.shorts.map(Video::id))
        assertEquals(
            listOf("history"),
            result.continueWatchingVideos.map(VideoHistoryEntry::videoId)
        )
    }

    @Test
    fun `already valid home state keeps its identity`() {
        val state = HomeUiState(
            videos = listOf(video("video")),
            shorts = listOf(video("short")),
            continueWatchingVideos = listOf(history("history"))
        )

        assertSame(state, state.withUniqueLazyContent())
    }

    private fun video(id: String) = Video(
        id = id,
        title = id,
        channelName = "Channel",
        channelId = "channel",
        thumbnailUrl = "thumbnail",
        duration = 60,
        viewCount = 1,
        uploadDate = "today"
    )

    private fun history(id: String) = VideoHistoryEntry(
        videoId = id,
        position = 1,
        duration = 10,
        timestamp = 1,
        title = id,
        thumbnailUrl = "thumbnail"
    )
}
