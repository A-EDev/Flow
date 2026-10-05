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
    fun `download URLs pin the released multihead graph and tokenizer`() {
        val prefix =
            "https://huggingface.co/CuriousDragon/flow-smart-segments-20261004/resolve/" +
                "8fcf7c9a2faaacd3b7d8a5e8b5ddc87247ce1b9f/"
        assertThat(SponsorModelConfig.resolveUrl(SponsorModelConfig.MODEL_FILE_NAME))
            .isEqualTo(prefix + "runtime/rank4/production_multi_head.int8.onnx")
        assertThat(SponsorModelConfig.resolveUrl(SponsorModelConfig.TOKENIZER_FILE_NAME))
            .isEqualTo(prefix + "runtime/tokenizer.json")
        assertThrows(IllegalStateException::class.java) {
            SponsorModelConfig.resolveUrl("../unknown")
        }
    }

    @Test
    fun `older ORT model requires update until current model is installed`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        assertThat(store.currentState()).isEqualTo(SponsorModelState.NotInstalled)
        val previous = File(root, "combined-android-previous").apply { mkdirs() }
        File(previous, "model.ort").writeText("previous model")
        File(previous, "tokenizer.json").writeText("previous tokenizer")
        assertThat(store.currentState()).isEqualTo(SponsorModelState.UpdateAvailable)
        store.resetStaging()
        assertThat(store.currentState()).isEqualTo(SponsorModelState.UpdateAvailable)
        stageValidModel(store)
        store.installStaged()
        assertThat(store.currentState()).isInstanceOf(SponsorModelState.Installed::class.java)
        assertThat(previous.exists()).isTrue()
    }

    @Test
    fun `partial staging and incomplete old model do not trigger update`() {
        val root = Files.createTempDirectory("sponsor-model").toFile()
        val store = SponsorModelStore(root)
        stageValidModel(store)
        val previous = File(root, "incomplete").apply { mkdirs() }
        File(previous, "model.onnx").writeText("incomplete model")
        assertThat(store.currentState()).isEqualTo(SponsorModelState.NotInstalled)
    }

    private fun stageValidModel(store: SponsorModelStore) {
        store.resetStaging()
        RandomAccessFile(store.stagedModelFile(), "rw").setLength(SponsorModelConfig.MODEL_BYTES)
        store.stagedTokenizerFile().writeBytes(ByteArray(SponsorModelConfig.TOKENIZER_BYTES.toInt()))
    }
}
