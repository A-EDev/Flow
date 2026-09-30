package io.github.aedev.flow.data.video.downloader.tags

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class Mp4TextAtomsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val tags =
        DownloadTags(
            kind = DownloadKind.VIDEO,
            videoId = "abc123DEF45",
            title = "Title",
            description = "Snälla ge oss en like\nsecond line",
        )

    @Test
    fun `the description atom is read back`() {
        val file = folder.newFile().apply { writeBytes(Mp4Fixtures.moovLast().bytes) }
        Mp4TagWriter().write(file, tags)

        assertThat(read(file)).containsExactly(Mp4TextAtoms.DESCRIPTION, "Snälla ge oss en like\nsecond line")
    }

    @Test
    fun `a file with no tags has no text`() {
        val file = folder.newFile().apply { writeBytes(Mp4Fixtures.moovFirst().bytes) }

        assertThat(read(file)).isEmpty()
    }

    private fun read(file: File): Map<String, String> =
        RandomAccessFile(file, "r").use { Mp4TextAtoms.read(it.channel, setOf(Mp4TextAtoms.DESCRIPTION, Mp4TextAtoms.LONG_DESCRIPTION)) }
}
