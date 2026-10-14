package io.github.aedev.flow.ui.screens.library

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.DownloadedVideo
import org.junit.Test

class DownloadsFilteringTest {
    private fun download(
        id: String,
        title: String,
        channel: String,
        size: Long,
        at: Long,
    ) = DownloadedVideo(
        video =
            Video(
                id = id,
                title = title,
                channelName = channel,
                channelId = "UC$id",
                thumbnailUrl = "",
                duration = 60,
                viewCount = 0,
                uploadDate = "",
            ),
        filePath = "/downloads/$id.mp4",
        downloadedAt = at,
        fileSize = size,
    )

    private val downloads =
        listOf(
            download("a", "Casting aluminium", "Foundry Friday", size = 300, at = 1),
            download("b", "Bench restore", "Bench Notes", size = 900, at = 3),
            download("c", "Cutting dovetails", "Oak and Iron", size = 100, at = 2),
        )

    @Test
    fun `search matches title or channel, ignoring case`() {
        assertThat(downloads.filterAndSort("bench", DownloadSort.NEWEST).map { it.video.id }).containsExactly("b")
        assertThat(downloads.filterAndSort("OAK", DownloadSort.NEWEST).map { it.video.id }).containsExactly("c")
    }

    @Test
    fun `a blank query keeps everything`() {
        assertThat(downloads.filterAndSort("  ", DownloadSort.NEWEST)).hasSize(3)
    }

    @Test
    fun `each order sorts by what it names`() {
        assertThat(downloads.filterAndSort("", DownloadSort.NEWEST).map { it.video.id }).containsExactly("b", "c", "a").inOrder()
        assertThat(downloads.filterAndSort("", DownloadSort.OLDEST).map { it.video.id }).containsExactly("a", "c", "b").inOrder()
        assertThat(downloads.filterAndSort("", DownloadSort.LARGEST).map { it.video.id }).containsExactly("b", "a", "c").inOrder()
        assertThat(downloads.filterAndSort("", DownloadSort.TITLE).map { it.video.id }).containsExactly("b", "a", "c").inOrder()
        assertThat(downloads.filterAndSort("", DownloadSort.CHANNEL).map { it.video.id }).containsExactly("b", "a", "c").inOrder()
    }
}
