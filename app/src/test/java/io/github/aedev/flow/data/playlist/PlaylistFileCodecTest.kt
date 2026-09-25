package io.github.aedev.flow.data.playlist

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class PlaylistFileCodecTest {
    private fun video(
        id: String,
        title: String = "Video $id",
        addedAt: Long? = null,
    ) = Video(
        id = id,
        title = title,
        channelName = "Channel",
        channelId = "UC123",
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hq720.jpg",
        duration = 245,
        viewCount = 1_000L,
        uploadDate = "2 years ago",
        timestamp = 1_600_000_000_000L,
        isMusic = id.startsWith("m"),
        addedAtInPlaylist = addedAt,
    )

    @Test
    fun `a playlist reads back with its name, order and every field a row shows`() {
        val videos = listOf(video("dQw4w9WgXcQ", addedAt = 5L), video("mBcDeFgHiJk"), video("aBcDeFgHiJ_"))

        val read = PlaylistFileCodec.decode(PlaylistFileCodec.encode("Road trip", "Songs for the car", videos, exportedAt = 1L))

        assertThat(read).isInstanceOf(PlaylistFileRead.Read::class.java)
        val file = (read as PlaylistFileRead.Read).file
        assertThat(file.playlist.name).isEqualTo("Road trip")
        assertThat(file.playlist.description).isEqualTo("Songs for the car")
        val restored = file.videos.map(PlaylistFileVideo::toVideo)
        assertThat(restored.map { it.id }).containsExactly("dQw4w9WgXcQ", "mBcDeFgHiJk", "aBcDeFgHiJ_").inOrder()
        assertThat(restored.first()).isEqualTo(videos.first())
        assertThat(restored[1].isMusic).isTrue()
        assertThat(restored[1].addedAtInPlaylist).isNull()
    }

    @Test
    fun `a music playlist says so, and files from before music export read as video`() {
        val music =
            PlaylistFileCodec.decode(
                PlaylistFileCodec.encode("Mix", "", listOf(video("dQw4w9WgXcQ")), exportedAt = 1L, isMusic = true),
            )
        val old = PlaylistFileCodec.decode("""{"format":"flow-playlist","version":1,"exportedAt":1,"playlist":{"name":"x"},"videos":[]}""")

        assertThat((music as PlaylistFileRead.Read).file.playlist.isMusic).isTrue()
        assertThat((old as PlaylistFileRead.Read).file.playlist.isMusic).isFalse()
    }

    @Test
    fun `device files are left out of the file`() {
        val text = PlaylistFileCodec.encode("Mixed", "", listOf(video("dQw4w9WgXcQ"), video("local_42")), exportedAt = 1L)

        val file = (PlaylistFileCodec.decode(text) as PlaylistFileRead.Read).file

        assertThat(file.videos.map { it.id }).containsExactly("dQw4w9WgXcQ")
    }

    @Test
    fun `malformed and repeated ids are dropped on import`() {
        val text =
            """
            {"format":"flow-playlist","version":1,"exportedAt":1,"playlist":{"name":"x"},
             "videos":[{"id":"dQw4w9WgXcQ"},{"id":"'; DROP TABLE"},{"id":""},{"id":"dQw4w9WgXcQ"}]}
            """.trimIndent()

        val file = (PlaylistFileCodec.decode(text) as PlaylistFileRead.Read).file

        assertThat(file.videos.map { it.id }).containsExactly("dQw4w9WgXcQ")
    }

    @Test
    fun `a video without a thumbnail gets YouTube's`() {
        val text = """{"format":"flow-playlist","version":1,"exportedAt":1,"playlist":{"name":"x"},"videos":[{"id":"dQw4w9WgXcQ"}]}"""

        val video =
            (PlaylistFileCodec.decode(text) as PlaylistFileRead.Read)
                .file.videos
                .single()
                .toVideo()

        assertThat(video.thumbnailUrl).isEqualTo("https://i.ytimg.com/vi/dQw4w9WgXcQ/hq720.jpg")
    }

    @Test
    fun `other JSON and plain text are not playlists`() {
        assertThat(PlaylistFileCodec.decode("""{"localPlaylists":[]}""")).isEqualTo(PlaylistFileRead.NotAPlaylist)
        assertThat(PlaylistFileCodec.decode("hello")).isEqualTo(PlaylistFileRead.NotAPlaylist)
        assertThat(PlaylistFileCodec.decode("""{"format":"other","version":1,"exportedAt":1,"playlist":{"name":"x"},"videos":[]}"""))
            .isEqualTo(PlaylistFileRead.NotAPlaylist)
    }

    @Test
    fun `a file from a newer format is refused rather than half read`() {
        val text = """{"format":"flow-playlist","version":99,"exportedAt":1,"playlist":{"name":"x"},"videos":[]}"""

        assertThat(PlaylistFileCodec.decode(text)).isEqualTo(PlaylistFileRead.TooNew)
    }

    @Test
    fun `file names drop characters file systems reject`() {
        assertThat(PlaylistFileCodec.fileName("Mix: 80s/90s?")).isEqualTo("Mix_ 80s_90s_.json")
        assertThat(PlaylistFileCodec.fileName("   ")).isEqualTo("flow-playlist.json")
        assertThat(PlaylistFileCodec.fileName("a".repeat(200))).hasLength(80 + ".json".length)
    }
}
