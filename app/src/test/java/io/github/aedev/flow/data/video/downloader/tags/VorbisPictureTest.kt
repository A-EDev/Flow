package io.github.aedev.flow.data.video.downloader.tags

import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.util.Base64

class VorbisPictureTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `a metadata block picture comment is the embedded cover`() {
        val encoded = Base64.getEncoder().encodeToString(pictureBlock(jpeg))
        val container = "OpusTags\u0000METADATA_BLOCK_PICTURE=$encoded\u0000audio".encodeToByteArray()

        assertThat(vorbisPictureBytes(container)).isEqualTo(jpeg)
    }

    @Test
    fun `a wrapped comment and a picture frame both become the cover`() {
        val encoded = Base64.getMimeEncoder().encodeToString(pictureBlock(jpeg))
        val fromComment = EmbeddedTags.fromEntries(listOf(VorbisComment("metadata_block_picture", encoded)))
        val fromFrame =
            EmbeddedTags.fromEntries(
                listOf(PictureFrame(3, "image/jpeg", "", 1, 1, 24, 0, jpeg)),
            )

        assertThat(fromComment.cover).isEqualTo(jpeg)
        assertThat(fromFrame.cover).isEqualTo(jpeg)
    }

    @Test
    fun `a truncated picture block is ignored`() {
        assertThat(pictureBytesFromComment("AAAA")).isNull()
        assertThat(vorbisPictureBytes("METADATA_BLOCK_PICTURE=".encodeToByteArray())).isNull()
        assertThat(vorbisPictureBytes("no picture here".encodeToByteArray())).isNull()
    }

    private fun pictureBlock(data: ByteArray): ByteArray {
        val mime = "image/jpeg".encodeToByteArray()
        val description = ByteArray(0)
        val buffer = ByteBuffer.allocate(32 + mime.size + description.size + data.size)
        buffer.putInt(3)
        buffer.putInt(mime.size)
        buffer.put(mime)
        buffer.putInt(description.size)
        buffer.put(description)
        buffer.putInt(1)
        buffer.putInt(1)
        buffer.putInt(24)
        buffer.putInt(0)
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }
}
