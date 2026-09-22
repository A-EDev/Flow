package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class PlayerRelatedVideosPolicyTest {
    @Test
    fun `fallback supplies related videos when primary metadata is empty`() {
        val fallback = listOf(video("related"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = emptyList(),
                fallback = fallback,
                current = emptyList(),
            )

        assertThat(selected).containsExactlyElementsIn(fallback)
    }

    @Test
    fun `empty enrichment never clears current related videos`() {
        val current = listOf(video("current"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = emptyList(),
                fallback = emptyList(),
                current = current,
            )

        assertThat(selected).containsExactlyElementsIn(current)
    }

    @Test
    fun `selection removes playing video blank ids and duplicates`() {
        val duplicate = video("related")

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = listOf(video("playing"), video(""), duplicate, duplicate.copy(title = "duplicate")),
                fallback = emptyList(),
                current = emptyList(),
            )

        assertThat(selected).containsExactly(duplicate)
    }

    @Test
    fun `sanitizing display candidates keeps live and upcoming videos`() {
        val live = video("live").copy(isLive = true)
        val upcoming = video("upcoming").copy(isUpcoming = true)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(live, upcoming))

        assertThat(selected).containsExactly(live, upcoming).inOrder()
    }

    @Test
    fun `sanitizing drops reels when shorts are disabled`() {
        val reel = video("reel").copy(isShort = true)
        val shortMusicVideo = video("music").copy(duration = 45)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(reel, shortMusicVideo), shortsEnabled = false)

        assertThat(selected).containsExactly(shortMusicVideo)
    }

    @Test
    fun `sanitizing keeps reels when shorts are enabled`() {
        val reel = video("reel").copy(isShort = true)

        val selected = PlayerRelatedVideosPolicy.sanitize("playing", listOf(reel), shortsEnabled = true)

        assertThat(selected).containsExactly(reel)
    }

    @Test
    fun `a blocked creator is dropped from the related list`() {
        val candidates = listOf(video("keep", channelId = "wanted"), video("drop", channelId = "blocked"))

        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = candidates,
                blockedChannelIds = setOf("blocked"),
            )

        assertThat(sanitized.map { it.id }).containsExactly("keep")
    }

    @Test
    fun `a candidate with no channel id survives rather than being dropped blindly`() {
        val candidates = listOf(video("unknown", channelId = ""))

        val sanitized =
            PlayerRelatedVideosPolicy.sanitize(
                videoId = "playing",
                candidates = candidates,
                blockedChannelIds = setOf("blocked"),
            )

        assertThat(sanitized.map { it.id }).containsExactly("unknown")
    }

    @Test
    fun `a source made empty by blocking falls through to the next one`() {
        val primary = listOf(video("blocked-only", channelId = "blocked"))
        val fallback = listOf(video("wanted", channelId = "wanted"))

        val selected =
            PlayerRelatedVideosPolicy.select(
                videoId = "playing",
                primary = primary,
                fallback = fallback,
                current = emptyList(),
                blockedChannelIds = setOf("blocked"),
            )

        assertThat(selected.map { it.id }).containsExactly("wanted")
    }

    private fun video(
        id: String,
        channelId: String = "channel-id",
    ) = Video(
        id = id,
        title = id,
        channelName = "channel",
        channelId = channelId,
        thumbnailUrl = "thumbnail",
        duration = 60,
        viewCount = 1L,
        uploadDate = "today",
    )
}
