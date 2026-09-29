package io.github.aedev.flow.data.video

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.video.downloader.request.StoredArtist
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.tags.DownloadTags
import org.junit.Test

class RecoveredDownloadTest {
    private val song = FoundFile("/Music/Flow/Artist - Album/05 - Song.m4a", "05 - Song.m4a", 4_000, 215_000, 42)
    private val tags =
        DownloadTags(
            kind = DownloadKind.MUSIC,
            videoId = "kcxK1Tnwy5M",
            title = "Song",
            artists = listOf("Daft Punk"),
            channelId = "UC1",
            album = "Alive 2007",
            albumId = "MPREb_1",
            trackNumber = 5,
            viewCount = 10,
            thumbnailUrl = "https://example.com/cover.jpg",
        )

    @Test
    fun `a file Flow tagged comes back as the download it was`() {
        val (download, item) = RecoveredDownload.rows(song, tags, title = "ignored", artist = "ignored", coverPath = "/covers/k.jpg")

        assertThat(download.videoId).isEqualTo("kcxK1Tnwy5M")
        assertThat(download.title).isEqualTo("Song")
        assertThat(download.uploader).isEqualTo("Daft Punk")
        assertThat(download.kind).isEqualTo(DownloadKind.MUSIC)
        assertThat(download.album).isEqualTo("Alive 2007")
        assertThat(download.trackNumber).isEqualTo(5)
        assertThat(download.duration).isEqualTo(215)
        assertThat(download.thumbnailPath).isEqualTo("/covers/k.jpg")
        assertThat(StoredArtist.decode(download.artistsJson).map { it.name }).containsExactly("Daft Punk")
        assertThat(item.videoId).isEqualTo("kcxK1Tnwy5M")
        assertThat(item.fileType).isEqualTo(DownloadFileType.AUDIO)
        assertThat(item.mimeType).isEqualTo("audio/mp4")
        assertThat(item.status).isEqualTo(DownloadItemStatus.COMPLETED)
    }

    @Test
    fun `an untagged file gets a stable id from its path and its own title`() {
        val file = FoundFile("/Download/Flow/clip.webm", "clip.webm", 1, 0, 0)

        val (download, item) = RecoveredDownload.rows(file, null, title = null, artist = null, coverPath = null)

        assertThat(download.videoId).isEqualTo(RecoveredDownload.idFor(file.path, null))
        assertThat(download.videoId).startsWith("recovered_")
        assertThat(download.title).isEqualTo("clip")
        assertThat(download.uploader).isEqualTo(RecoveredDownload.LOCAL_FILE_ARTIST)
        assertThat(download.kind).isEqualTo(DownloadKind.VIDEO)
        assertThat(item.fileType).isEqualTo(DownloadFileType.VIDEO)
        assertThat(item.mimeType).isEqualTo("video/webm")
    }

    @Test
    fun `each container gets its own mime type`() {
        assertThat(RecoveredDownload.mimeTypeOf("mp3")).isEqualTo("audio/mpeg")
        assertThat(RecoveredDownload.mimeTypeOf("mkv")).isEqualTo("video/x-matroska")
        assertThat(RecoveredDownload.mimeTypeOf("opus")).isEqualTo("audio/ogg")
    }

    @Test
    fun `only media files are picked up`() {
        assertThat(RecoveredDownload.isMedia("Song.M4A")).isTrue()
        assertThat(RecoveredDownload.isMedia("abc.pending")).isFalse()
        assertThat(RecoveredDownload.isMedia("cover.jpg")).isFalse()
    }
}
