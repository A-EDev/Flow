package io.github.aedev.flow.data.video.downloader.request

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.video.downloader.tags.DownloadKind
import io.github.aedev.flow.data.video.downloader.work.LegacyDownloadRequest
import org.junit.Test

class DownloadRequestTest {
    private val video =
        Video(
            id = "abc",
            title = "A video",
            channelName = "A channel",
            channelId = "UC1",
            thumbnailUrl = "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            duration = 90,
            viewCount = 10,
            likeCount = 2,
            uploadDate = "",
        )

    @Test
    fun `a request survives being stored with its row`() {
        val request = video.toDownloadRequest(targetHeight = 1080, videoCodec = "vp9", videoItag = 248, audioItag = 140)

        assertThat(DownloadRequest.decode(request.encode())).isEqualTo(request)
        assertThat(DownloadRequest.decode("not json")).isNull()
        assertThat(DownloadRequest.decode(null)).isNull()
    }

    @Test
    fun `a video request carries what the card knew and a watch url`() {
        val tags = video.toDownloadRequest().tags

        assertThat(tags.kind).isEqualTo(DownloadKind.VIDEO)
        assertThat(tags.channelId).isEqualTo("UC1")
        assertThat(tags.sourceUrl).isEqualTo("https://www.youtube.com/watch?v=abc")
        assertThat(
            video
                .copy(isShort = true)
                .toDownloadRequest()
                .tags.sourceUrl,
        ).isEqualTo("https://www.youtube.com/shorts/abc")
    }

    @Test
    fun `a song request is audio only with its album, artists and track number`() {
        val track =
            MusicTrack(
                videoId = "song",
                title = "Intro",
                artist = "Artist A, Artist B",
                thumbnailUrl = "",
                duration = 60,
                album = "Album",
                albumId = "MPREb_1",
                artists = listOf(MusicArtist("Artist A", "UCa"), MusicArtist("Artist B", null)),
            )

        val request = track.toDownloadRequest(collectionId = "MPREb_1", trackNumber = 3, trackTotal = 12, albumArtist = "Artist A")

        assertThat(request.wantsAudioOnly).isTrue()
        assertThat(request.tags.artists).containsExactly("Artist A", "Artist B").inOrder()
        assertThat(request.tags.trackNumber).isEqualTo(3)
        assertThat(request.tags.sourceUrl).isEqualTo("https://music.youtube.com/watch?v=song")
        assertThat(request.artists).containsExactly(StoredArtist("Artist A", "UCa"), StoredArtist("Artist B", null)).inOrder()
    }

    @Test
    fun `a row from before requests were stored asks for the quality its label named`() {
        val row =
            DownloadWithItems(
                DownloadEntity(videoId = "old", title = "Old", uploader = "Someone"),
                listOf(
                    DownloadItemEntity(
                        videoId = "old",
                        fileType = DownloadFileType.VIDEO,
                        fileName = "a",
                        filePath = "/a",
                        quality = "VP9 1080p",
                    ),
                ),
            )

        val request = LegacyDownloadRequest.from(row)

        assertThat(request.targetHeight).isEqualTo(1080)
        assertThat(request.videoCodec).isEqualTo("vp9")
        assertThat(request.wantsAudioOnly).isFalse()
        assertThat(request.tags.channelName).isEqualTo("Someone")
    }

    @Test
    fun `stored artists read back, and junk reads as none`() {
        val artists = listOf(StoredArtist("A", "UC"), StoredArtist("B"))

        assertThat(StoredArtist.decode(StoredArtist.encode(artists))).isEqualTo(artists)
        assertThat(StoredArtist.decode("[{")).isEmpty()
    }
}
