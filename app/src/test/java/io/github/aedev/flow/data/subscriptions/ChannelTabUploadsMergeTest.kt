package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/** Channel-tab uploads fill in what RSS left bare without overwriting RSS's exact publish time. */
class ChannelTabUploadsMergeTest {
    private fun video(
        id: String,
        duration: Int,
        timestamp: Long,
        viewCount: Long = 0,
    ) = Video(
        id = id,
        title = "title-$id",
        channelName = "c",
        channelId = "UCc",
        thumbnailUrl = "",
        duration = duration,
        viewCount = viewCount,
        uploadDate = "",
        timestamp = timestamp,
    )

    @Test
    fun `a stored row gains the length and keeps its publish time, an unseen upload is added`() {
        val stored = listOf(video("known", duration = 0, timestamp = 1_234L))
        val tab =
            listOf(video("known", duration = 540, timestamp = 9_000L, viewCount = 77), video("new", duration = 60, timestamp = 9_000L))

        val (inserts, updates) = splitChannelTabUploads(tab, stored)

        assertThat(inserts.map { it.id }).containsExactly("new")
        assertThat(updates.single().duration).isEqualTo(540)
        assertThat(updates.single().viewCount).isEqualTo(77)
        assertThat(updates.single().timestamp).isEqualTo(1_234L)
    }

    @Test
    fun `a stored RSS row stays exact when a channel tab fills it in`() {
        val stored = listOf(video("known", duration = 0, timestamp = 1_234L).copy(timestampIsExact = true))
        val tab = listOf(video("known", duration = 540, timestamp = 9_000L))

        val (_, updates) = splitChannelTabUploads(tab, stored)

        assertThat(updates.single().timestampIsExact).isTrue()
    }
}
