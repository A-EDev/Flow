package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class HomeFeedCacheRepositoryTest {
    private fun video(id: String, channelId: String = "ch-$id") = Video(
        id = id,
        title = "title-$id",
        channelName = "channel-$id",
        channelId = channelId,
        thumbnailUrl = "",
        duration = 600,
        viewCount = 1,
        uploadDate = "1 day ago"
    )

    private fun cached(
        id: String,
        source: String,
        channelId: String = "ch-$id"
    ) = CachedHomeVideo(video(id, channelId), source)

    @Test
    fun `filterCachedHomeVideos drops watched suppressed and blocked rows`() {
        val rows = listOf(
            cached("keep", HomeFeedCacheRepository.SOURCE_RELATED, channelId = "allowed"),
            cached("watched", HomeFeedCacheRepository.SOURCE_RELATED, channelId = "allowed"),
            cached("suppressed", HomeFeedCacheRepository.SOURCE_RELATED, channelId = "allowed"),
            cached("blocked_channel", HomeFeedCacheRepository.SOURCE_RELATED, channelId = "blocked"),
            cached("suppressed_channel", HomeFeedCacheRepository.SOURCE_RELATED, channelId = "suppressed_ch")
        )

        val filtered = filterCachedHomeVideos(
            rows,
            HomeFeedCacheFilters(
                watchedVideoIds = setOf("watched"),
                suppressedVideoIds = setOf("suppressed"),
                blockedChannelIds = setOf("blocked"),
                suppressedChannelIds = setOf("suppressed_ch")
            )
        )

        assertThat(filtered.map { it.video.id }).containsExactly("keep")
    }

    @Test
    fun `selectReservePageFromCache takes bounded related and discovery candidates`() {
        val rows = listOf(
            cached("r1", HomeFeedCacheRepository.SOURCE_RELATED),
            cached("r2", HomeFeedCacheRepository.SOURCE_RELATED),
            cached("r3", HomeFeedCacheRepository.SOURCE_RELATED),
            cached("d1", HomeFeedCacheRepository.SOURCE_DISCOVERY),
            cached("d2", HomeFeedCacheRepository.SOURCE_DISCOVERY),
            cached("v1", HomeFeedCacheRepository.SOURCE_VIRAL)
        )

        val page = selectReservePageFromCache(rows, maxRelated = 2, maxDiscovery = 1)

        assertThat(page.map { it.video.id }).containsExactly("r1", "r2", "d1").inOrder()
    }
}
