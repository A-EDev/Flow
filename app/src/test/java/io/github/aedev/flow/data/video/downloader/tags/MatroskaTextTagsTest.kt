package io.github.aedev.flow.data.video.downloader.tags

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

class MatroskaTextTagsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val title = "Snälla ge oss en stöd like för detta 😭💀 #dance #viral"

    @Test
    fun `reads the segment title and the file-wide artist as written`() {
        val file =
            matroska(
                info(element(0x7BA9, title)),
                tags(tag(simpleTag("ARTIST", "Linnea & Viola"), simpleTag("COMMENT", "https://www.youtube.com/watch?v=nvU-3W2GO1U"))),
                cluster(),
            )

        val texts = read(file)

        assertThat(texts).containsEntry(MatroskaTextTags.TITLE, title)
        assertThat(texts).containsEntry(MatroskaTextTags.ARTIST, "Linnea & Viola")
    }

    @Test
    fun `tags of one track are not the file's`() {
        val file =
            matroska(
                tags(
                    tag(simpleTag("TITLE", "Video track"), trackUid = 7),
                    tag(simpleTag("TITLE", "The film")),
                ),
            )

        assertThat(read(file)).containsEntry(MatroskaTextTags.TITLE, "The film")
    }

    @Test
    fun `the segment title wins over a title tag`() {
        val file = matroska(info(element(0x7BA9, title)), tags(tag(simpleTag("TITLE", "Other"))))

        assertThat(read(file)).containsEntry(MatroskaTextTags.TITLE, title)
    }

    @Test
    fun `tags written after the clusters are found through the seek head`() {
        val clusterBytes = cluster()
        val infoBytes = info(element(0x7BA9, title))
        val tagsBytes = tags(tag(simpleTag("ARTIST", "Linnea & Viola")))
        val seekHeadSize = seekHead(0L).size
        val tagsPosition = (seekHeadSize + infoBytes.size + clusterBytes.size).toLong()
        val file = matroska(seekHead(tagsPosition), infoBytes, clusterBytes, tagsBytes)

        assertThat(read(file)).containsEntry(MatroskaTextTags.ARTIST, "Linnea & Viola")
    }

    @Test
    fun `padding writers leave after a value is dropped`() {
        val file = matroska(tags(tag(simpleTag("ARTIST", "Linnea & Viola\u0000\u0000"))))

        assertThat(read(file)).containsEntry(MatroskaTextTags.ARTIST, "Linnea & Viola")
    }

    @Test
    fun `a file that is not Matroska reads as null`() {
        val file = folder.newFile("clip.mp4").apply { writeBytes(byteArrayOf(0, 0, 0, 32) + "ftypisom".toByteArray() + ByteArray(24)) }

        assertThat(read(file.readBytes())).isNull()
    }

    private fun read(bytes: ByteArray): Map<String, String>? {
        val file = folder.newFile().apply { writeBytes(bytes) }
        return FileInputStream(file).channel.use(MatroskaTextTags::read)
    }

    private fun matroska(vararg children: ByteArray): ByteArray =
        element(0x1A45DFA3, element(0x4282, "webm")) + element(0x18538067, *children)

    private fun info(vararg children: ByteArray) = element(0x1549A966, *children)

    private fun tags(vararg children: ByteArray) = element(0x1254C367, *children)

    private fun cluster() = element(0x1F43B675, element(0xE7, byteArrayOf(0)), element(0xA3, ByteArray(64)))

    private fun tag(
        vararg simpleTags: ByteArray,
        trackUid: Long? = null,
    ): ByteArray {
        val targets = if (trackUid == null) element(0x63C0) else element(0x63C0, element(0x63C5, byteArrayOf(trackUid.toByte())))
        return element(0x7373, targets, *simpleTags)
    }

    private fun simpleTag(
        name: String,
        value: String,
    ) = element(0x67C8, element(0x45A3, name), element(0x4487, value))

    private fun seekHead(tagsPosition: Long): ByteArray {
        val position = ByteArray(Long.SIZE_BYTES) { (tagsPosition shr (8 * (7 - it))).toByte() }
        return element(0x114D9B74, element(0x4DBB, element(0x53AB, idBytes(0x1254C367)), element(0x53AC, position)))
    }

    private fun element(
        id: Long,
        text: String,
    ) = element(id, text.toByteArray(Charsets.UTF_8))

    private fun element(
        id: Long,
        vararg children: ByteArray,
    ): ByteArray {
        val body = children.fold(ByteArray(0)) { all, child -> all + child }
        return ByteArrayOutputStream()
            .apply {
                write(idBytes(id))
                write(byteArrayOf(0x08) + ByteArray(4) { (body.size shr (8 * (3 - it))).toByte() })
                write(body)
            }.toByteArray()
    }

    private fun idBytes(id: Long): ByteArray {
        val length = (64 - java.lang.Long.numberOfLeadingZeros(id) + 7) / 8
        return ByteArray(length) { (id shr (8 * (length - 1 - it))).toByte() }
    }
}
