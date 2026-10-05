package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and manages the on-device sponsor model from Hugging Face.
 *
 * The download is application-scoped so it survives leaving the settings screen.
 * Files are written to staging, SHA-256 verified, then promoted atomically; a
 * failed or interrupted transfer leaves any previously installed model intact.
 */
@Singleton
class SponsorModelRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ) {
        private val store = SponsorModelStore(context)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val downloadClient =
            okHttpClient
                .newBuilder()
                // The model is already stored on disk; never mirror it into the shared HTTP cache.
                .cache(null)
                .callTimeout(0, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        private val _state = MutableStateFlow(currentState())
        private var downloadJob: Job? = null

        val state: StateFlow<SponsorModelState> = _state.asStateFlow()

        fun refresh() {
            if (downloadJob?.isActive != true) _state.value = currentState()
        }

        fun download() {
            if (downloadJob?.isActive == true) return
            downloadJob =
                scope.launch {
                    store.resetStaging()
                    var downloaded = 0L
                    var lastReported = 0L

                    fun report() {
                        if (downloaded - lastReported >= PROGRESS_STEP_BYTES || downloaded >= SponsorModelConfig.TOTAL_BYTES) {
                            lastReported = downloaded
                            _state.value = SponsorModelState.Downloading(downloaded, SponsorModelConfig.TOTAL_BYTES)
                        }
                    }
                    _state.value = SponsorModelState.Downloading(0L, SponsorModelConfig.TOTAL_BYTES)
                    try {
                        downloadFile(
                            destination = store.stagedModelFile(),
                            fileName = SponsorModelConfig.MODEL_FILE_NAME,
                            expectedSha256 = SPONSOR_MODEL_SHA256,
                        ) { bytes ->
                            downloaded += bytes
                            report()
                        }
                        downloadFile(
                            destination = store.stagedTokenizerFile(),
                            fileName = SponsorModelConfig.TOKENIZER_FILE_NAME,
                            expectedSha256 = SPONSOR_TOKENIZER_SHA256,
                        ) { bytes ->
                            downloaded += bytes
                            report()
                        }
                        val installed = store.installStaged()
                        Log.i(TAG, "Sponsor model installed: ${installed.sizeBytes} bytes")
                        _state.value = SponsorModelState.Installed(installed.sizeBytes)
                    } catch (cancelled: CancellationException) {
                        store.resetStaging()
                        throw cancelled
                    } catch (error: Exception) {
                        store.resetStaging()
                        Log.w(TAG, "Sponsor model download failed", error)
                        _state.value = SponsorModelState.Failed
                    }
                }
        }

        fun delete() {
            downloadJob?.cancel()
            downloadJob = null
            scope.launch {
                store.delete()
                _state.value = currentState()
            }
        }

        private fun currentState(): SponsorModelState = store.currentState()

        private suspend fun downloadFile(
            destination: File,
            fileName: String,
            expectedSha256: String,
            onBytes: (Long) -> Unit,
        ) {
            val request = Request.Builder().url(SponsorModelConfig.resolveUrl(fileName)).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed (${response.code}) for $fileName")
                val body = response.body
                destination.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            onBytes(read.toLong())
                        }
                    }
                }
            }
            val actualSha = sha256File(destination)
            check(actualSha == expectedSha256) {
                "SHA-256 mismatch for $fileName (expected $expectedSha256, got $actualSha)"
            }
        }

        private companion object {
            const val TAG = "SponsorDetection"
            const val READ_TIMEOUT_SECONDS = 60L
            const val PROGRESS_STEP_BYTES = 256L * 1024L
        }
    }
