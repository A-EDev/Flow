package io.github.aedev.flow.ui.screens.playlists

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class PlaylistSearchTest {
    private fun video(
        id: String,
        title: String,
        channel: String,
    ) = Video(
        id = id,
        title = title,
        channelName = channel,
        channelId = "",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
    )

    private val videos =
        listOf(
            video("1", "Café au lait recipe", "Kitchen"),
            video("2", "Lo-fi beats to study to", "Lofi Girl"),
            video("3", "Morning coffee routine", "Kitchen"),
        )

    @Test
    fun `a blank query keeps the whole playlist`() {
        assertThat(videos.matchingSearch("  ")).isEqualTo(videos)
    }

    @Test
    fun `case and accents are ignored`() {
        assertThat(videos.matchingSearch("CAFE").map { it.id }).containsExactly("1")
    }

    @Test
    fun `the channel name matches too`() {
        assertThat(videos.matchingSearch("kitchen").map { it.id }).containsExactly("1", "3").inOrder()
    }

    @Test
    fun `every word must match somewhere`() {
        assertThat(videos.matchingSearch("kitchen coffee").map { it.id }).containsExactly("3")
        assertThat(videos.matchingSearch("kitchen beats")).isEmpty()
    }
}
