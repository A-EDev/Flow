package io.github.aedev.flow.data.music.video

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.SongItem
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class MusicVideoVersionsTest {
    private val song =
        MusicTrack(
            videoId = "song",
            title = "Kifak Inta",
            artist = "Fairuz",
            thumbnailUrl = "https://lh3.googleusercontent.com/art",
            duration = 212,
            album = "Kifak Inta",
            channelId = "fairuz",
            artists = listOf(MusicArtist("Fairuz", "fairuz")),
        )
    private val officialVideo =
        SongItem(
            id = "video",
            title = "Kifak Inta",
            artists = listOf(Artist("Fairuz", "fairuz")),
            duration = 214,
            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
            thumbnail = "",
        )

    @Test
    fun `a matched video keeps the song's title and art and plays for its own length`() =
        runTest {
            val versions = MusicVideoVersions { listOf(officialVideo) }

            val video = versions.videoFor(song)

            assertThat(video?.videoId).isEqualTo("video")
            assertThat(video?.title).isEqualTo("Kifak Inta")
            assertThat(video?.thumbnailUrl).isEqualTo(song.thumbnailUrl)
            assertThat(video?.album).isEqualTo("Kifak Inta")
            assertThat(video?.duration).isEqualTo(214)
            assertThat(video?.isVideoSong).isTrue()
        }

    @Test
    fun `a track that is already a video needs no search`() =
        runTest {
            var searches = 0
            val versions =
                MusicVideoVersions {
                    searches++
                    emptyList()
                }
            val musicVideo = song.copy(isVideoSong = true)

            assertThat(versions.videoFor(musicVideo)).isEqualTo(musicVideo)
            assertThat(searches).isEqualTo(0)
        }

    @Test
    fun `a song is searched once, whether or not it has a video`() =
        runTest {
            var searches = 0
            val versions =
                MusicVideoVersions {
                    searches++
                    emptyList()
                }

            assertThat(versions.videoFor(song)).isNull()
            assertThat(versions.videoFor(song)).isNull()
            assertThat(searches).isEqualTo(1)
        }

    @Test
    fun `a failed search is not remembered as no video`() =
        runTest {
            var fail = true
            val versions =
                MusicVideoVersions {
                    if (fail) throw IOException("offline")
                    listOf(officialVideo)
                }

            runCatching { versions.videoFor(song) }
            fail = false

            assertThat(versions.videoFor(song)?.videoId).isEqualTo("video")
        }
}
