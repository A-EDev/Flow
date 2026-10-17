package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
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

    @Test
    fun `download URLs pin the released sponsor-only model and tokenizer`() {
        val prefix =
            "https://huggingface.co/CuriousDragon/ettin-17m-sponsor-combined-android-int8-mm/resolve/" +
                "0b8399f4f8bc226b21d8c8f39d4611abd536890c/"
        assertThat(SponsorModelConfig.resolveUrl(SponsorModelConfig.MODEL_FILE_NAME))
            .isEqualTo(prefix + "sponsor_detector_combined.int8.ort")
        assertThat(SponsorModelConfig.resolveUrl(SponsorModelConfig.TOKENIZER_FILE_NAME))
            .isEqualTo(prefix + "tokenizer.json")
        assertThrows(IllegalStateException::class.java) {
            SponsorModelConfig.resolveUrl("../unknown")
        }
    }

    @Test
    fun `staged files alone do not count as installed`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        stageValidModel(store)
        assertThat(store.currentState()).isEqualTo(SponsorModelState.NotInstalled)
        store.installStaged()
        assertThat(store.currentState()).isInstanceOf(SponsorModelState.Installed::class.java)
    }

    private fun stageValidModel(store: SponsorModelStore) {
        store.resetStaging()
        RandomAccessFile(store.stagedModelFile(), "rw").setLength(SponsorModelConfig.MODEL_BYTES)
        store.stagedTokenizerFile().writeBytes(ByteArray(SponsorModelConfig.TOKENIZER_BYTES.toInt()))
    }
}
