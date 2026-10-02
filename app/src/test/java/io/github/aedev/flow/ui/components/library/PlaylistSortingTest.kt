package io.github.aedev.flow.ui.components.library

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class PlaylistSortingTest {
    @Test
    fun `a YouTube playlist offers no date added orders`() {
        val remote = PlaylistSortOrder.availableFor(isLocalPlaylist = false)

        assertThat(remote).doesNotContain(PlaylistSortOrder.DATE_ADDED_NEWEST)
        assertThat(remote).doesNotContain(PlaylistSortOrder.DATE_ADDED_OLDEST)
        assertThat(remote).contains(PlaylistSortOrder.MANUAL)
    }

    @Test
    fun `a playlist of your own offers every order`() {
        assertThat(PlaylistSortOrder.availableFor(isLocalPlaylist = true)).containsExactlyElementsIn(PlaylistSortOrder.entries)
    }

    @Test
    fun `likes sort by when each was liked and cannot be ordered by hand`() {
        val likes = PlaylistSortOrder.availableFor(isLocalPlaylist = true, isLikes = true)

        assertThat(likes).doesNotContain(PlaylistSortOrder.MANUAL)
        assertThat(PlaylistSortOrder.defaultFor(isLikes = true)).isEqualTo(PlaylistSortOrder.DATE_ADDED_NEWEST)
        assertThat(PlaylistSortOrder.defaultFor(isLikes = false)).isEqualTo(PlaylistSortOrder.MANUAL)
    }

    @Test
    fun `only the date added orders show when a video was added`() {
        val showing = PlaylistSortOrder.entries.filter { it.showsDateAdded }

        assertThat(showing).containsExactly(PlaylistSortOrder.DATE_ADDED_NEWEST, PlaylistSortOrder.DATE_ADDED_OLDEST)
    }

    @Test
    fun `date added orders follow when each video was added, not its position`() {
        // A playlist of your own appends, so its stored order runs oldest first.
        val playlist = listOf(video("first", addedAt = 1_000L), video("second", addedAt = 2_000L), video("third", addedAt = 3_000L))

        assertThat(playlist.idsSortedBy(PlaylistSortOrder.DATE_ADDED_NEWEST)).containsExactly("third", "second", "first").inOrder()
        assertThat(playlist.idsSortedBy(PlaylistSortOrder.DATE_ADDED_OLDEST)).containsExactly("first", "second", "third").inOrder()
    }

    @Test
    fun `Watch Later, kept newest first, sorts by its add times too`() {
        val watchLater = listOf(video("newest", addedAt = 3_000L), video("middle", addedAt = 2_000L), video("oldest", addedAt = 1_000L))

        assertThat(watchLater.idsSortedBy(PlaylistSortOrder.DATE_ADDED_NEWEST)).containsExactly("newest", "middle", "oldest").inOrder()
        assertThat(watchLater.idsSortedBy(PlaylistSortOrder.DATE_ADDED_OLDEST)).containsExactly("oldest", "middle", "newest").inOrder()
    }

    @Test
    fun `videos without an add time sort as the oldest, latest position first`() {
        val playlist = listOf(video("untimed-a"), video("untimed-b"), video("timed", addedAt = 1_000L))

        assertThat(playlist.idsSortedBy(PlaylistSortOrder.DATE_ADDED_NEWEST)).containsExactly("timed", "untimed-b", "untimed-a").inOrder()
        assertThat(playlist.idsSortedBy(PlaylistSortOrder.DATE_ADDED_OLDEST)).containsExactly("untimed-a", "untimed-b", "timed").inOrder()
    }

    private fun video(
        id: String,
        addedAt: Long? = null,
    ) = Video(
        id = id,
        title = id,
        channelName = "",
        channelId = "",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
        addedAtInPlaylist = addedAt,
    )

    private fun List<Video>.idsSortedBy(order: PlaylistSortOrder) = sortedForPlaylist(order).map { it.id }
}
