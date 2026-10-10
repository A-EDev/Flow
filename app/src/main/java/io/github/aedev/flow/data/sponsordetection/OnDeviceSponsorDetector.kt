package io.github.aedev.flow.data.sponsordetection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.player.stream.ResolvedCaption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.coroutines.coroutineContext
import kotlin.math.max

/** Cumulative streaming emission: stitched spans over all windows finished so far. */
internal data class SponsorStreamUpdate(
    val spans: List<SponsorPredictedSpan>,
    val windowsDone: Int,
    val windowTotal: Int,
)

internal class OnDeviceSponsorDetector(
    context: Context,
    private val playbackPositionMs: () -> Long = { 0L },
    private val runtimeConfig: SponsorInferenceRuntimeConfig = SponsorInferenceRuntimeConfig.forAvailableHardware(),
    private val modelStore: SponsorModelStore = SponsorModelStore(context),
    private val cache: SponsorPredictionCache = SponsorInferenceCache(context),
) {
    private val applicationContext = context.applicationContext
    private val captionLoader = SponsorCaptionLoader(applicationContext)
    private val inferenceMutex = Mutex()

    @Volatile
    private var runtime: Runtime? = null

    suspend fun predict(
        videoId: String,
        subtitles: List<ResolvedCaption>,
        onProvisional: suspend (List<SponsorBlockSegment>) -> Unit = {},
    ): List<SponsorBlockSegment> {
        val transcript = captionLoader.load(subtitles) ?: return emptyList()
        cache.get(videoId, transcript)?.let { return it.playbackSegments }
        return predictStream(videoId, transcript) { update ->
            if (update.spans.isNotEmpty()) onProvisional(update.spans.map { it.asSponsorBlockSegment(videoId) })
        }.also { cache.put(it) }.playbackSegments
    }

    /**
     * Runs inference window by window, invoking [onUpdate] with the cumulative
     * stitched spans after every batch so callers can overlay/apply early
     * segments instead of waiting for the whole transcript. Returns the final
     * result once every window is decoded.
     */
    suspend fun predictStream(
        videoId: String,
        transcript: SponsorTranscriptPayload,
        onUpdate: suspend (SponsorStreamUpdate) -> Unit = {},
    ): SponsorInferenceResult {
        val queuedAt = SystemClock.elapsedRealtime()
        val outcome =
            inferenceMutex.withLock {
                withContext(Dispatchers.Default) {
                    val computeStarted = SystemClock.elapsedRealtime()
                    var firstResultMs = 0L
                    var firstEmitted = false
                    val prediction =
                        runtime().streamPredict(transcript.cues, playbackPositionMs) { spans, windowsDone, windowTotal ->
                            if (!firstEmitted) {
                                firstEmitted = true
                                firstResultMs = SystemClock.elapsedRealtime() - computeStarted
                            }
                            onUpdate(
                                SponsorStreamUpdate(
                                    spans = spans.mapIndexed { index, span -> span.toPredictedSpan(index) },
                                    windowsDone = windowsDone,
                                    windowTotal = windowTotal,
                                ),
                            )
                        }
                    DetailedOutcome(
                        prediction,
                        computeMs = SystemClock.elapsedRealtime() - computeStarted,
                        firstResultMs = firstResultMs,
                    )
                }
            }
        val queueMs = SystemClock.elapsedRealtime() - queuedAt - outcome.computeMs
        val spans = outcome.prediction.spans
        if (spans.isEmpty()) {
            Log.i(
                TAG,
                "On-device sponsor inference for $videoId found no sponsor: " +
                    "cues=${transcript.cues.size} windows=${outcome.prediction.windowCount} " +
                    "threshold=$SPONSOR_CONFIDENCE_THRESHOLD computeMs=${outcome.computeMs} queueMs=$queueMs",
            )
        } else {
            Log.i(
                TAG,
                "On-device sponsor inference for $videoId produced ${spans.size} spans: " +
                    "cues=${transcript.cues.size} windows=${outcome.prediction.windowCount} " +
                    "threshold=$SPONSOR_CONFIDENCE_THRESHOLD computeMs=${outcome.computeMs} " +
                    "firstResultMs=${outcome.firstResultMs} queueMs=$queueMs " +
                    "ranges=${spans.joinToString { "${it.startMs}-${it.endMs}" }}",
            )
        }
        return SponsorInferenceResult(
            videoId = videoId,
            transcript = transcript,
            transcriptSha256 = sponsorTranscriptSha256(transcript),
            spans = spans.mapIndexed { index, span -> span.toPredictedSpan(index) },
            windowCount = outcome.prediction.windowCount,
            confidenceThreshold = SPONSOR_CONFIDENCE_THRESHOLD,
            inferenceMs = outcome.computeMs,
        )
    }

    private fun runtime(): Runtime =
        runtime ?: synchronized(this) {
            runtime ?: Runtime.load(modelStore, runtimeConfig).also { runtime = it }
        }

    fun isModelInstalled(): Boolean = modelStore.installedFiles() != null

    suspend fun close() {
        inferenceMutex.withLock {
            runtime?.close()
            runtime = null
        }
    }

    private fun SponsorDetectionSpan.toPredictedSpan(index: Int): SponsorPredictedSpan =
        SponsorPredictedSpan(
            spanId = "$index-$startMs-$endMs",
            startMs = startMs,
            endMs = endMs,
            confidence = confidence,
        )

    private data class DetailedOutcome(
        val prediction: RuntimePrediction,
        val computeMs: Long,
        val firstResultMs: Long,
    )

    private data class RuntimePrediction(
        val spans: List<SponsorDetectionSpan>,
        val windowCount: Int,
        val firstBatchMs: Long,
        val tokenizationMs: Long,
        val inputPrepMs: Long,
        val ortMs: Long,
        val decodeMs: Long,
        val stitchMs: Long,
    )

    private data class WindowBatchResult(
        val spans: List<WindowSponsorSpan>,
        val inputPrepMs: Long,
        val ortMs: Long,
        val decodeMs: Long,
    )

    private class Runtime(
        private val environment: OrtEnvironment,
        private val session: OrtSession,
        private val options: OrtSession.SessionOptions,
        private val tokenizer: SponsorTokenizer,
        private val config: SponsorInferenceRuntimeConfig,
    ) {
        // The OrtEnvironment comes from OrtEnvironment.getEnvironment(), which is a
        // process-wide singleton owned by the ORT library: only the session and its
        // options are ours to close here. Closing the shared environment would break
        // later sessions, and the options must outlive the session.
        fun close() {
            try {
                session.close()
            } finally {
                options.close()
            }
        }

        /**
         * Tokenizes incrementally and runs ORT on windows due at the playhead
         * (current position through [SPONSOR_INFERENCE_LOOKAHEAD_MS]) before
         * finishing the rest. Early emissions stay usable for overlay/skip.
         */
        suspend fun streamPredict(
            cues: List<DetectionTranscriptCue>,
            playbackPositionMs: () -> Long = { 0L },
            onBatch: suspend (spans: List<SponsorDetectionSpan>, windowsDone: Int, windowTotal: Int) -> Unit,
        ): RuntimePrediction {
            val started = SystemClock.elapsedRealtime()
            val transcript = assembleSponsorTranscript(cues)
            val assembleMs = SystemClock.elapsedRealtime() - started
            val windowIter = sponsorWindowSequence(transcript, tokenizer).iterator()
            var tokenizationMs = 0L
            val initialHasNextStarted = SystemClock.elapsedRealtime()
            var tokenizerComplete = !windowIter.hasNext()
            tokenizationMs += SystemClock.elapsedRealtime() - initialHasNextStarted
            Log.i(TAG, "Sponsor inference starting: cues=${cues.size}")
            val remaining = mutableListOf<SponsorInferenceWindow>()
            val windowSpans = mutableListOf<WindowSponsorSpan>()
            var done = 0
            var stitched: List<SponsorDetectionSpan> = emptyList()
            var firstWindowMs = 0L
            var firstOrtMs = 0L
            var inputPrepMs = 0L
            var ortMs = 0L
            var decodeMs = 0L
            var stitchMs = 0L
            var batches = 0
            while (true) {
                coroutineContext.ensureActive()
                val positionMs = playbackPositionMs().coerceAtLeast(0L)
                while (!tokenizerComplete) {
                    val dueCount = remaining.count { isSponsorWindowDue(it, positionMs) }
                    if (dueCount >= config.batchSize) break
                    val hasNextStarted = SystemClock.elapsedRealtime()
                    val hasNext = windowIter.hasNext()
                    tokenizationMs += SystemClock.elapsedRealtime() - hasNextStarted
                    if (!hasNext) {
                        tokenizerComplete = true
                        break
                    }
                    val nextStarted = SystemClock.elapsedRealtime()
                    remaining += windowIter.next()
                    tokenizationMs += SystemClock.elapsedRealtime() - nextStarted
                    if (firstWindowMs == 0L) firstWindowMs = SystemClock.elapsedRealtime() - started
                    if (dueCount >= 1 && !isSponsorWindowDue(remaining.last(), positionMs)) break
                }
                if (!tokenizerComplete) {
                    val hasNextStarted = SystemClock.elapsedRealtime()
                    val hasNext = windowIter.hasNext()
                    tokenizationMs += SystemClock.elapsedRealtime() - hasNextStarted
                    if (!hasNext) tokenizerComplete = true
                }
                val batch =
                    selectSponsorInferenceBatch(
                        remaining = remaining,
                        positionMs = positionMs,
                        tokenizerComplete = tokenizerComplete,
                        batchSize = config.batchSize,
                    )
                if (batch.isEmpty()) {
                    if (tokenizerComplete) break
                    continue
                }
                val batchStarted = SystemClock.elapsedRealtime()
                val decoded = runWindowBatch(batch)
                val batchMs = SystemClock.elapsedRealtime() - batchStarted
                inputPrepMs += decoded.inputPrepMs
                ortMs += decoded.ortMs
                decodeMs += decoded.decodeMs
                windowSpans += decoded.spans
                val batchIndexes = batch.map { it.index }.toSet()
                remaining.removeAll { it.index in batchIndexes }
                done += batch.size
                val stitchStarted = SystemClock.elapsedRealtime()
                stitched =
                    stitchSponsorSpans(
                        transcript,
                        windowSpans,
                        confidenceThreshold = SPONSOR_CONFIDENCE_THRESHOLD,
                    )
                stitchMs += SystemClock.elapsedRealtime() - stitchStarted
                batches++
                if (firstOrtMs == 0L) firstOrtMs = SystemClock.elapsedRealtime() - started
                val windowTotal = done + remaining.size + if (tokenizerComplete) 0 else 1
                Log.d(
                    TAG,
                    "Sponsor batch $done/$windowTotal: ${stitched.size} spans in ${batchMs}ms pos=${positionMs}ms",
                )
                try {
                    onBatch(stitched, done, windowTotal)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Sponsor streaming observer failed, continuing inference", error)
                }
                yield()
            }
            Log.i(
                TAG,
                "Sponsor inference timing: assembleMs=$assembleMs firstWindowMs=$firstWindowMs " +
                    "firstOrtMs=$firstOrtMs tokenizationMs=$tokenizationMs inputPrepMs=$inputPrepMs " +
                    "ortMs=$ortMs decodeMs=$decodeMs stitchMs=$stitchMs " +
                    "windows=$done batches=$batches computeMs=${SystemClock.elapsedRealtime() - started}",
            )
            return RuntimePrediction(stitched, done, firstOrtMs, tokenizationMs, inputPrepMs, ortMs, decodeMs, stitchMs)
        }

        private fun runWindowBatch(batch: List<SponsorInferenceWindow>): WindowBatchResult {
            val maxLength = batch.maxOf { it.inputIds.size }
            val shape = longArrayOf(batch.size.toLong(), maxLength.toLong())
            val spans = mutableListOf<WindowSponsorSpan>()
            var decodeMs = 0L
            var ortMs = 0L
            val inputPrepStarted = SystemClock.elapsedRealtime()
            val idsBuffer =
                reusableLongBuffer(batch.size * maxLength, inputIdsBuffer).also { buffer ->
                    inputIdsBuffer = buffer
                    batch.forEach { window ->
                        buffer.put(window.inputIds)
                        repeat(maxLength - window.inputIds.size) { buffer.put(PAD_ID) }
                    }
                    buffer.flip()
                }
            val masksBuffer =
                reusableLongBuffer(batch.size * maxLength, attentionMaskBuffer).also { buffer ->
                    attentionMaskBuffer = buffer
                    batch.forEach { window ->
                        buffer.put(window.attentionMask)
                        repeat(maxLength - window.attentionMask.size) { buffer.put(0L) }
                    }
                    buffer.flip()
                }
            val inputPrepMs = SystemClock.elapsedRealtime() - inputPrepStarted
            val ortStarted = SystemClock.elapsedRealtime()
            OnnxTensor.createTensor(environment, idsBuffer, shape).use { inputIds ->
                OnnxTensor.createTensor(environment, masksBuffer, shape).use { attentionMask ->
                    session.run(mapOf("input_ids" to inputIds, "attention_mask" to attentionMask)).use { result ->
                        ortMs = SystemClock.elapsedRealtime() - ortStarted
                        val output = result[0] as OnnxTensor
                        val outputShape = output.info.shape
                        check(
                            outputShape.size == 3 &&
                                outputShape[0] == batch.size.toLong() &&
                                outputShape[1] == maxLength.toLong() &&
                                outputShape[2] == LABEL_COUNT.toLong(),
                        ) {
                            "Unexpected sponsor model output shape ${outputShape.toList()}, " +
                                "expected [${batch.size}, $maxLength, $LABEL_COUNT]"
                        }
                        val decodeStarted = SystemClock.elapsedRealtime()
                        val logits = output.floatBuffer
                        batch.forEach { window ->
                            val sequenceLength = window.offsets.size
                            val windowLogits =
                                List(sequenceLength) {
                                    FloatArray(LABEL_COUNT) { logits.get() }
                                }
                            val padding = maxLength - sequenceLength
                            if (padding > 0) {
                                logits.position(logits.position() + padding * LABEL_COUNT)
                            }
                            val decoded = decodeSponsorBilou(windowLogits, window.offsets)
                            spans +=
                                decoded.map {
                                    WindowSponsorSpan(
                                        window.index,
                                        it.startCodePoint,
                                        it.endCodePoint,
                                        it.confidence,
                                        it.category,
                                    )
                                }
                        }
                        decodeMs = SystemClock.elapsedRealtime() - decodeStarted
                    }
                }
            }
            return WindowBatchResult(spans, inputPrepMs, ortMs, decodeMs)
        }

        private var inputIdsBuffer: LongBuffer? = null
        private var attentionMaskBuffer: LongBuffer? = null

        private fun reusableLongBuffer(
            requiredElements: Int,
            existing: LongBuffer?,
        ): LongBuffer =
            if (existing == null || existing.capacity() < requiredElements) {
                ByteBuffer
                    .allocateDirect(requiredElements * Long.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asLongBuffer()
            } else {
                existing.clear()
                existing
            }

        companion object {
            fun load(
                modelStore: SponsorModelStore,
                config: SponsorInferenceRuntimeConfig,
            ): Runtime {
                val files =
                    modelStore.installedFiles()
                        ?: throw SponsorModelUnavailableException()
                val tokenizer = SponsorTokenizer.fromJson(files.tokenizerFile.readText())
                val tokenizerSha = sha256File(files.tokenizerFile)
                check(tokenizerSha == SPONSOR_TOKENIZER_SHA256) {
                    "Sponsor tokenizer SHA-256 mismatch: cached predictions would be misattributed"
                }
                val modelFile = files.modelFile
                check(modelFile.length() == SponsorModelConfig.MODEL_BYTES) {
                    "Sponsor model size ${modelFile.length()} does not match " +
                        "${SponsorModelConfig.MODEL_FILE_NAME} (${SponsorModelConfig.MODEL_BYTES} bytes)"
                }
                val actualSha = sha256File(modelFile)
                check(actualSha == SPONSOR_MODEL_SHA256) {
                    "Sponsor model SHA-256 mismatch: cached predictions would be misattributed"
                }
                val environment = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions()
                var session: OrtSession? = null
                try {
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    val intraOpThreads = max(1, minOf(config.intraOpThreads, hostCoreCount()))
                    options.setIntraOpNumThreads(intraOpThreads)
                    options.setInterOpNumThreads(1)
                    options.addConfigEntry("session.intra_op.allow_spinning", "0")
                    val sessionStarted = SystemClock.elapsedRealtime()
                    session = environment.createSession(modelFile.absolutePath, options)
                    Log.i(TAG, "Sponsor ORT session created in ${SystemClock.elapsedRealtime() - sessionStarted}ms")
                    check(session.inputNames == setOf("input_ids", "attention_mask"))
                    check(session.outputNames == setOf("logits"))
                    Log.i(
                        TAG,
                        "Sponsor ORT config: batchSize=${config.batchSize} " +
                            "intraOpThreads=$intraOpThreads hostCores=${hostCoreCount()}",
                    )
                    return Runtime(environment, session, options, tokenizer, config)
                } catch (error: Exception) {
                    session?.close()
                    options.close()
                    throw error
                }
            }

            private fun hostCoreCount(): Int =
                java.lang.Runtime
                    .getRuntime()
                    .availableProcessors()
        }
    }

    private companion object {
        const val TAG = "SponsorDetection"
        const val PAD_ID = 50_283L
        const val LABEL_COUNT = 5
    }
}
