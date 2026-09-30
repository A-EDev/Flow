package io.github.aedev.flow.data.video.downloader.tags

import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmbeddedTagsTest {
    private val full =
        DownloadTags(
            kind = DownloadKind.VIDEO,
            videoId = "abc123DEF45",
            title = "Title 🎵",
            artists = listOf("A, with comma", "B"),
            channelId = "UC123",
            channelName = "Channel",
            album = "Album",
            albumId = "MPREb",
            albumArtist = "A",
            trackNumber = 3,
            trackTotal = 12,
            playlistId = "PL1",
            releaseDate = "2024-02-29",
            description = "Line one\nLine two",
            sourceUrl = "https://www.youtube.com/watch?v=abc123DEF45",
            viewCount = 42L,
            likeCount = 7L,
            thumbnailUrl = "https://i.ytimg.com/vi/abc123DEF45/hqdefault.jpg",
            lyrics = "la la",
        )

    @Test
    fun `embedded tags come from mdta entries and fill gaps from display frames`() {
        val entries =
            FlowTagFields.encode(full.copy(album = null, lyrics = null)).map { (field, value) ->
                MdtaMetadataEntry(FlowTagFields.mdtaKey(field), value.toByteArray(), MdtaMetadataEntry.TYPE_INDICATOR_STRING)
            } +
                listOf(
                    TextInformationFrame("TALB", null, listOf("Frame album")),
                    TextInformationFrame("USLT", null, listOf("frame lyrics")),
                    ApicFrame("image/jpeg", null, 3, byteArrayOf(1, 2, 3)),
                )

        val embedded = EmbeddedTags.fromEntries(entries)

        assertThat(embedded.flow).isEqualTo(full.copy(album = "Frame album", lyrics = "frame lyrics"))
        assertThat(embedded.album).isEqualTo("Frame album")
        assertThat(embedded.artist).isEqualTo("A, with comma, B")
        assertThat(embedded.cover).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `any file gives its comment and a flac its description`() {
        val embedded =
            EmbeddedTags.fromEntries(
                listOf(
                    TextInformationFrame("TIT2", null, listOf("Title")),
                    CommentFrame("eng", "", "https://example.com/watch"),
                    VorbisComment("description", "Vorbis description"),
                ),
            )

        assertThat(embedded.flow).isNull()
        assertThat(embedded.comment).isEqualTo("https://example.com/watch")
        assertThat(embedded.description).isEqualTo("Vorbis description")
    }

    @Test
    fun `lyrics come from an mp3 USLT frame or a vorbis comment`() {
        val uslt = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "[00:01.00]Line".toByteArray()

        assertThat(EmbeddedTags.fromEntries(listOf(BinaryFrame("USLT", uslt))).lyrics).isEqualTo("[00:01.00]Line")
        assertThat(EmbeddedTags.fromEntries(listOf(VorbisComment("LYRICS", "Vorbis line"))).lyrics).isEqualTo("Vorbis line")
        assertThat(EmbeddedTags.fromEntries(listOf(TextInformationFrame("USLT", null, listOf("m4a line")))).lyrics).isEqualTo("m4a line")
    }

    @Test
    fun `internal frames from another domain are ignored`() {
        val entries =
            listOf(
                InternalFrame("com.apple.iTunes", "videoId", "nope"),
                InternalFrame(FlowTagFields.NAMESPACE, "kind", "VIDEO"),
                TextInformationFrame("TIT2", null, listOf("Plain title")),
                TextInformationFrame("TPE1", null, listOf("Plain artist")),
            )

        val embedded = EmbeddedTags.fromEntries(entries)

        assertThat(embedded.flow).isNull()
        assertThat(embedded.title).isEqualTo("Plain title")
        assertThat(embedded.artist).isEqualTo("Plain artist")
    }

    @Test
    fun `track frame fills number and total`() {
        val entries =
            listOf(
                InternalFrame(FlowTagFields.NAMESPACE, "videoId", "v"),
                InternalFrame(FlowTagFields.NAMESPACE, "kind", "MUSIC"),
                TextInformationFrame("TIT2", null, listOf("Song")),
                TextInformationFrame("TRCK", null, listOf("4/9")),
            )

        val flow = EmbeddedTags.fromEntries(entries).flow!!

        assertThat(flow.title).isEqualTo("Song")
        assertThat(flow.trackNumber).isEqualTo(4)
        assertThat(flow.trackTotal).isEqualTo(9)
    }
}
