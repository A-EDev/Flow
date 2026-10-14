package io.github.aedev.flow.data.video.downloader.work

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DownloadLyricsTest {
    private val helper: LyricsHelper = mockk()
    private val dao: DownloadDao = mockk(relaxed = true)
    private val lyrics = DownloadLyrics(helper, dao)

    private fun request(
        kind: DownloadKind = DownloadKind.MUSIC,
        lyrics: String? = null,
    ) = DownloadRequest(
        DownloadTags(kind = kind, videoId = "v", title = "Song", artists = listOf("Artist"), lyrics = lyrics),
        audioOnly = true,
        durationSeconds = 200,
    )

    @Test
    fun `a song keeps its synced lyrics as lrc and stores them with the request`() =
        runTest {
            val entries = listOf(LyricsEntry(1_000, "First"), LyricsEntry(65_500, "Second"))
            coEvery { helper.getLyrics("v", "Song", "Artist", 200, null, null) } returns (entries to "LrcLib")
            every { helper.entriesAreSynced(entries) } returns true

            val result = lyrics.addTo(request())

            assertThat(result.tags.lyrics).isEqualTo("[00:01.00]First\n[01:05.50]Second")
            coVerify { dao.updateRequest("v", result.encode()) }
        }

    @Test
    fun `unsynced lyrics are kept as plain lines`() =
        runTest {
            val entries = listOf(LyricsEntry(0, "First"), LyricsEntry(0, "Second"))
            coEvery { helper.getLyrics(any(), any(), any(), any(), any(), any()) } returns (entries to "YouTube")
            every { helper.entriesAreSynced(entries) } returns false

            assertThat(lyrics.addTo(request()).tags.lyrics).isEqualTo("First\nSecond")
        }

    @Test
    fun `videos, songs that already have lyrics and misses are left alone`() =
        runTest {
            coEvery { helper.getLyrics(any(), any(), any(), any(), any(), any()) } returns null

            assertThat(lyrics.addTo(request(kind = DownloadKind.VIDEO)).tags.lyrics).isNull()
            assertThat(lyrics.addTo(request(lyrics = "kept")).tags.lyrics).isEqualTo("kept")
            assertThat(lyrics.addTo(request()).tags.lyrics).isNull()
            coVerify(exactly = 1) { helper.getLyrics(any(), any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { dao.updateRequest(any(), any()) }
        }
}
