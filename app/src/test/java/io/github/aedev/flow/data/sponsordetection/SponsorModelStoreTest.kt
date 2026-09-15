package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.RandomAccessFile
import java.nio.file.Files

class SponsorModelStoreTest {
    @Test
    fun `installStaged promotes verified files and reports size`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        stageValidModel(store)

        val installed = store.installStaged()

        assertThat(installed.modelFile.length()).isEqualTo(SponsorModelConfig.MODEL_BYTES)
        assertThat(store.installedFiles()?.sizeBytes)
            .isEqualTo(SponsorModelConfig.MODEL_BYTES + SponsorModelConfig.TOKENIZER_BYTES)
    }

    @Test
    fun `installedFiles ignores a truncated model`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        stageValidModel(store)
        val installed = store.installStaged()
        RandomAccessFile(installed.modelFile, "rw").setLength(10L)

        assertThat(store.installedFiles()).isNull()
    }

    @Test
    fun `delete removes the model directory`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        stageValidModel(store)
        store.installStaged()

        store.delete()

        assertThat(store.installedFiles()).isNull()
        assertThat(root.exists()).isFalse()
    }

    private fun stageValidModel(store: SponsorModelStore) {
        store.resetStaging()
        RandomAccessFile(store.stagedModelFile(), "rw").setLength(SponsorModelConfig.MODEL_BYTES)
        store.stagedTokenizerFile().writeBytes(ByteArray(SponsorModelConfig.TOKENIZER_BYTES.toInt()))
    }
}
