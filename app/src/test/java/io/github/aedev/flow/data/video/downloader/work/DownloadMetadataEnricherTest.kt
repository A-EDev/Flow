package io.github.aedev.flow.data.video.downloader.work

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset

class DownloadMetadataEnricherTest {
    private val repository: YouTubeRepository = mockk()
    private val dao: DownloadDao = mockk(relaxed = true)
    private val enricher = DownloadMetadataEnricher(repository, dao)

    private fun request(kind: DownloadKind = DownloadKind.VIDEO) =
        DownloadRequest(DownloadTags(kind = kind, videoId = "v", title = "Title"))

    @Test
    fun `the watch page fills date, description, counts and channel, and is stored`() =
        runTest {
            coEvery { repository.enrichFromWatchMetadata(any()) } answers {
                firstArg<Video>().copy(
                    channelId = "UC",
                    description = "About",
                    likeCount = 5,
                    viewCount = 50,
                    timestamp = 1_700_000_000_000,
                )
            }

            val enriched = enricher.enrich(request())

            assertThat(enriched.tags.channelId).isEqualTo("UC")
            assertThat(enriched.tags.description).isEqualTo("About")
            assertThat(enriched.tags.likeCount).isEqualTo(5)
            assertThat(enriched.tags.releaseDate).isNotNull()
            coVerify(exactly = 1) { dao.updateMetadata("v", "Title", any(), "UC", "About", any(), 50, 5, any()) }
        }

    @Test
    fun `songs never ask the watch page`() =
        runTest {
            val song = request(DownloadKind.MUSIC)

            assertThat(enricher.enrich(song)).isSameInstanceAs(song)
            coVerify(exactly = 0) { repository.enrichFromWatchMetadata(any()) }
        }

    @Test
    fun `a failed watch page leaves the request as it was`() =
        runTest {
            coEvery { repository.enrichFromWatchMetadata(any()) } throws IllegalStateException("offline")
            val original = request()

            assertThat(enricher.enrich(original)).isSameInstanceAs(original)
        }

    @Test
    fun `release dates are iso calendar dates`() {
        assertThat(DownloadMetadataEnricher.isoDate(1_700_000_000_000, ZoneOffset.UTC)).isEqualTo("2023-11-14")
        assertThat(DownloadMetadataEnricher.isoDate(0)).isNull()
    }
}
