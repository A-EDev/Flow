package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class HomeFeedRowTest {
    @Test
    fun `the shelves follow exactly one full first row`() {
        val rows = homeFeedRows(videos(7), columns = 3, showContinueWatching = true, showShorts = true)

        assertThat(rows.map { it.key })
            .containsExactly("0", "1", "2", "continue_watching_shelf", "shorts_shelf", "3", "4", "5", "6")
            .inOrder()
        assertThat(rows.map { it.spansRow }.count { it }).isEqualTo(2)
    }

    @Test
    fun `a phone puts the shelves after the first card`() {
        val rows = homeFeedRows(videos(3), columns = 1, showContinueWatching = false, showShorts = true)

        assertThat(rows.map { it.key }).containsExactly("0", "shorts_shelf", "1", "2").inOrder()
    }

    @Test
    fun `fewer videos than a row still place the shelves after them`() {
        val rows = homeFeedRows(videos(2), columns = 3, showContinueWatching = true, showShorts = false)

        assertThat(rows.map { it.key }).containsExactly("0", "1", "continue_watching_shelf").inOrder()
    }

    @Test
    fun `no videos means no shelves`() {
        assertThat(homeFeedRows(emptyList(), columns = 3, showContinueWatching = true, showShorts = true)).isEmpty()
    }

    private fun videos(count: Int) =
        List(count) { index ->
            Video(
                id = "$index",
                title = "$index",
                channelName = "Channel",
                channelId = "channel",
                thumbnailUrl = "thumbnail",
                duration = 60,
                viewCount = 1,
                uploadDate = "today",
            )
        }
}
