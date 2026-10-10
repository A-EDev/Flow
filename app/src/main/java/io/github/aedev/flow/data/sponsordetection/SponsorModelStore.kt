package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import java.io.File

/**
 * Pinned source of the on-device sponsor model. The revision is a Hugging Face
 * commit so a given APK always downloads the exact bytes its SHA-256 constants
 * were validated against. Bumping a model means bumping [HF_REVISION] and
 * [MODEL_VERSION] together; older APKs keep working with their old revision.
 */
internal object SponsorModelConfig {
    const val HF_REPOSITORY = "CuriousDragon/ettin-17m-sponsor-combined-android-int8-mm"
    const val HF_REVISION = "0b8399f4f8bc226b21d8c8f39d4611abd536890c"
    const val MODEL_VERSION = "combined-android-int8mm-0b8399f"
    const val MODEL_FILE_NAME = "sponsor_detector_combined.int8.ort"
    const val TOKENIZER_FILE_NAME = "tokenizer.json"
    const val MODEL_BYTES = 17_966_296L
    const val TOKENIZER_BYTES = 3_583_228L
    const val TOTAL_BYTES = MODEL_BYTES + TOKENIZER_BYTES

    val MODEL_PAGE_URL = "https://huggingface.co/$HF_REPOSITORY"

    fun resolveUrl(fileName: String): String {
        check(fileName == MODEL_FILE_NAME || fileName == TOKENIZER_FILE_NAME) {
            "Unknown model asset: $fileName"
        }
        return "$MODEL_PAGE_URL/resolve/$HF_REVISION/$fileName"
    }
}

internal data class SponsorModelFiles(
    val modelFile: File,
    val tokenizerFile: File,
) {
    val sizeBytes: Long get() = modelFile.length() + tokenizerFile.length()
}

/** Raised when inference is requested before the model has been downloaded. */
internal class SponsorModelUnavailableException(
    message: String = "Sponsor model is not downloaded",
) : IllegalStateException(message)

sealed interface SponsorModelState {
    data object NotInstalled : SponsorModelState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : SponsorModelState

    data class Installed(
        val sizeBytes: Long,
    ) : SponsorModelState

    data object Failed : SponsorModelState
}

/**
 * Owns the on-disk layout of the downloaded model. Downloads are staged into a
 * sibling directory, verified, then promoted atomically by replacing the
 * version directory so a partial download can never be treated as installed.
 */
internal class SponsorModelStore(
    private val rootDirectory: File,
) {
    constructor(context: Context) : this(File(context.filesDir, SPONSOR_MODEL_DIRECTORY))

    private val versionDirectory = File(rootDirectory, SponsorModelConfig.MODEL_VERSION)
    private val stagingDirectory = File(rootDirectory, STAGING_DIRECTORY)

    fun installedFiles(): SponsorModelFiles? = versionDirectory.files()

    fun currentState(): SponsorModelState =
        installedFiles()?.let { SponsorModelState.Installed(it.sizeBytes) } ?: SponsorModelState.NotInstalled

    fun stagedModelFile(): File = File(stagingDirectory, SponsorModelConfig.MODEL_FILE_NAME)

    fun stagedTokenizerFile(): File = File(stagingDirectory, SponsorModelConfig.TOKENIZER_FILE_NAME)

    fun resetStaging() {
        stagingDirectory.deleteRecursively()
        stagingDirectory.mkdirs()
    }

    fun installStaged(): SponsorModelFiles {
        val staged = stagingDirectory.files() ?: error("Staged sponsor model files are incomplete")
        check(staged.modelFile.length() == SponsorModelConfig.MODEL_BYTES) {
            "Staged sponsor model size ${staged.modelFile.length()} does not match ${SponsorModelConfig.MODEL_BYTES} bytes"
        }
        if (versionDirectory.exists()) versionDirectory.deleteRecursively()
        rootDirectory.mkdirs()
        if (!stagingDirectory.renameTo(versionDirectory)) {
            stagingDirectory.copyRecursively(versionDirectory, overwrite = true)
            stagingDirectory.deleteRecursively()
        }
        return versionDirectory.files() ?: error("Sponsor model install failed")
    }

    fun delete() {
        rootDirectory.deleteRecursively()
    }

    private fun File.files(): SponsorModelFiles? {
        val model = File(this, SponsorModelConfig.MODEL_FILE_NAME)
        val tokenizer = File(this, SponsorModelConfig.TOKENIZER_FILE_NAME)
        return if (model.isFile && model.length() == SponsorModelConfig.MODEL_BYTES && tokenizer.isFile) {
            SponsorModelFiles(model, tokenizer)
        } else {
            null
        }
    }

    private companion object {
        const val SPONSOR_MODEL_DIRECTORY = "sponsor_models"
        const val STAGING_DIRECTORY = "staging"
    }
}
