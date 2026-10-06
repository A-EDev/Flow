package io.github.aedev.flow.data.video.downloader.transfer

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TransferStateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stream(
        itag: Int,
        size: Long,
        fileSize: Long = size,
    ) = TransferStream(StreamRole.VIDEO, "u", folder.newFile().apply { writeBytes(ByteArray(fileSize.toInt())) }, itag, size)

    @Test
    fun `a saved block map comes back from disk`() {
        val original = stream(137, 100)
        original.completedBlocks.addAll(listOf(0, 2))
        original.partialBlockBytes[1] = 17L
        val file = folder.root.resolve("state.json")

        TransferState.write(file, TransferState.of(TransferJob("v", listOf(original), 1, "ua")))
        val restored = stream(137, 100)
        val didRestore = TransferState.read(file)!!.restoreInto(listOf(restored))

        assertThat(didRestore).isTrue()
        assertThat(restored.completedBlocks).containsExactly(0, 2)
        assertThat(restored.partialBlockBytes).containsExactly(1, 17L)
    }

    @Test
    fun `blocks never land on a different stream or a resized part`() {
        val saved = TransferState(listOf(TransferState.StreamState(StreamRole.VIDEO, 137, 100, listOf(0), emptyMap())))

        val otherItag = stream(248, 100)
        val otherSize = stream(137, 200)
        val shortFile = stream(137, 100, fileSize = 10)

        assertThat(saved.restoreInto(listOf(otherItag))).isFalse()
        assertThat(saved.restoreInto(listOf(otherSize))).isFalse()
        assertThat(saved.restoreInto(listOf(shortFile))).isFalse()
        assertThat(otherItag.completedBlocks + otherSize.completedBlocks + shortFile.completedBlocks).isEmpty()
    }

    @Test
    fun `an unreadable state file restores nothing`() {
        val file = folder.newFile().apply { writeText("{broken") }

        assertThat(TransferState.read(file)).isNull()
    }

    @Test
    fun `an unresolved job does not overwrite a resumable state after a failed probe`() {
        val saved = TransferState(listOf(TransferState.StreamState(StreamRole.VIDEO, 137, 100, listOf(0), emptyMap())))
        val file = folder.root.resolve("resume.json")
        TransferState.write(file, saved)
        val unresolved = stream(137, 0, fileSize = 100)

        TransferState.write(file, TransferJob("v", listOf(unresolved), 1, "ua"))

        assertThat(TransferState.read(file)).isEqualTo(saved)
    }

    @Test
    fun `a resolved job replaces its saved block map`() {
        val file = folder.root.resolve("resume.json")
        val resolved = stream(137, 100)
        resolved.completedBlocks.add(0)
        val job = TransferJob("v", listOf(resolved), 1, "ua")

        TransferState.write(file, job)

        assertThat(TransferState.read(file)).isEqualTo(TransferState.of(job))
    }
}
