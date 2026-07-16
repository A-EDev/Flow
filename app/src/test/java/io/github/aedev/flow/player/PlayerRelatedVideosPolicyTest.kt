package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class PlayerRelatedVideosPolicyTest {
    @Test
    fun `fallback supplies related videos when primary metadata is empty`() {
        val fallback = listOf(video("related"))

        val selected = PlayerRelatedVideosPolicy.select(
            videoId = "playing",
            primary = emptyList(),
            fallback = fallback,
            current = emptyList()
        )

        assertThat(selected).containsExactlyElementsIn(fallback)
    }

    @Test
    fun `empty enrichment never clears current related videos`() {
        val current = listOf(video("current"))

        val selected = PlayerRelatedVideosPolicy.select(
            videoId = "playing",
            primary = emptyList(),
            fallback = emptyList(),
            current = current
        )

        assertThat(selected).containsExactlyElementsIn(current)
    }

    @Test
    fun `selection removes playing video blank ids and duplicates`() {
        val duplicate = video("related")

        val selected = PlayerRelatedVideosPolicy.select(
            videoId = "playing",
            primary = listOf(video("playing"), video(""), duplicate, duplicate.copy(title = "duplicate")),
            fallback = emptyList(),
            current = emptyList()
        )

        assertThat(selected).containsExactly(duplicate)
    }

    private fun video(id: String) = Video(
        id = id,
        title = id,
        channelName = "channel",
        channelId = "channel-id",
        thumbnailUrl = "thumbnail",
        duration = 60,
        viewCount = 1L,
        uploadDate = "today"
    )
}
