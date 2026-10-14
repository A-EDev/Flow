package io.github.aedev.flow.data.video.downloader.tags

import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CoverPicturesTest {
    private fun block(
        type: Int,
        vararg data: Byte,
    ) = PictureFrame(type, "image/jpeg", "", 1, 1, 24, 0, data)

    @Test
    fun `an ogg or flac picture block is a cover whatever its type`() {
        assertThat(listOf(block(0, 1, 2)).coverPicture()).isEqualTo(byteArrayOf(1, 2))
    }

    @Test
    fun `the front cover wins over pictures listed before it`() {
        val entries = listOf(block(4, 9), ApicFrame("image/png", "", 0, byteArrayOf(8)), block(3, 7))

        assertThat(entries.coverPicture()).isEqualTo(byteArrayOf(7))
    }

    @Test
    fun `empty pictures and other entries are not covers`() {
        assertThat(listOf(block(3), VorbisComment("TITLE", "Song")).coverPicture()).isNull()
    }

    @Test
    fun `embedded tags take a picture block as the cover`() {
        assertThat(EmbeddedTags.fromEntries(listOf(block(0, 5))).cover).isEqualTo(byteArrayOf(5))
    }
}
