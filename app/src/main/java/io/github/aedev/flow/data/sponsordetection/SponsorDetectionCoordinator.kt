package io.github.aedev.flow.data.sponsordetection

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.repository.SponsorBlockFetchResult
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.OutputStream

internal data class SponsorDetectionLoadResult(
    val playbackSegments: List<SponsorBlockSegment>,
    val apiSegments: List<SponsorBlockSegment>,
)

internal class SponsorDetectionCoordinator(
    private val fetchSegments: suspend (String) -> SponsorBlockFetchResult,
    private val loadCaptions: suspend (List<SubtitlesStream>) -> SponsorTranscriptPayload?,
    private val predictStream: suspend (
        String,
        SponsorTranscriptPayload,
        suspend (SponsorStreamUpdate) -> Unit,
    ) -> SponsorInferenceResult,
    private val cache: SponsorPredictionCache,
    private val trainingSink: SponsorTrainingSink,
    private val consentEnabled: suspend () -> Boolean,
    private val onProvisionalPlayback: suspend (String, List<SponsorBlockSegment>) -> Unit = { _, _ -> },
    private val closeDetector: suspend () -> Unit = {},
    journalDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        context: Context,
        onProvisionalPlayback: suspend (String, List<SponsorBlockSegment>) -> Unit = { _, _ -> },
        playbackPositionMs: () -> Long = { 0L },
    ) : this(
        repository = SponsorBlockRepository(),
        captionLoader = SponsorCaptionLoader(context.applicationContext),
        detector = OnDeviceSponsorDetector(context.applicationContext, playbackPositionMs),
        cache = SponsorInferenceCache(context.applicationContext),
        trainingSink = SponsorFeedbackJournal(context.applicationContext),
        preferences = PlayerPreferences(context.applicationContext),
        onProvisionalPlayback = onProvisionalPlayback,
    )

    constructor(
        repository: SponsorBlockRepository,
        captionLoader: SponsorCaptionLoader,
        detector: OnDeviceSponsorDetector,
        cache: SponsorInferenceCache,
        trainingSink: SponsorTrainingSink,
        preferences: PlayerPreferences,
        onProvisionalPlayback: suspend (String, List<SponsorBlockSegment>) -> Unit = { _, _ -> },
        journalDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        fetchSegments = repository::fetchSegments,
        loadCaptions = captionLoader::load,
        predictStream = detector::predictStream,
        cache = cache,
        trainingSink = trainingSink,
        consentEnabled = { preferences.sponsorTrainingConsentEnabled.first() },
        onProvisionalPlayback = onProvisionalPlayback,
        closeDetector = detector::close,
        journalDispatcher = journalDispatcher,
    )

    private val stateMutex = Mutex()
    private val journalJob = SupervisorJob()
    private val journalScope = CoroutineScope(journalJob + journalDispatcher)
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(SponsorDetectionUiState())
    val state: StateFlow<SponsorDetectionUiState> = _state
    val journalStats: StateFlow<SponsorJournalStats> = trainingSink.stats
    private var generation = 0L
    private var currentEvaluationEvent: SponsorEvaluationEvent? = null
    private var currentTranscript: SponsorTranscriptPayload? = null

    suspend fun evaluate(
        videoId: String,
        subtitles: List<SubtitlesStream>,
        authoritativeSegments: List<SponsorBlockSegment>? = null,
    ): SponsorDetectionLoadResult =
        coroutineScope {
            val requestGeneration = begin(videoId)
            Log.i(TAG, "Sponsor evaluation started for $videoId")
            var resolvedApiResult: SponsorBlockFetchResult? =
                authoritativeSegments?.let(SponsorBlockFetchResult::Success)
            var resolvedApiSegments = authoritativeSegments.orEmpty()
            try {
                val apiDeferred =
                    async {
                        authoritativeSegments?.let { SponsorBlockFetchResult.Success(it) }
                            ?: fetchSegments(videoId)
                    }
                val transcriptDeferred = async { loadCaptions(subtitles) }
                val transcript = transcriptDeferred.await()

                suspend fun handleProvisional(
                    payload: SponsorTranscriptPayload,
                    update: SponsorStreamUpdate,
                ) {
                    if (!isCurrent(requestGeneration)) throw CancellationException("superseded sponsor evaluation")
                    if (update.spans.isEmpty()) return
                    publishIfCurrent(
                        requestGeneration,
                        SponsorDetectionUiState(
                            videoId = videoId,
                            status = SponsorDetectionStatus.LOADING,
                            predictions = update.spans,
                            inference =
                                SponsorInferenceSummary(
                                    modelName = SPONSOR_MODEL_NAME,
                                    confidenceThreshold = SPONSOR_CONFIDENCE_THRESHOLD,
                                    transcriptCueCount = payload.cues.size,
                                    windowCount = update.windowTotal,
                                    spanCount = update.spans.size,
                                    inferenceMs = 0,
                                    fromCache = false,
                                ),
                            isProvisional = true,
                        ),
                    )
                    // Provisional playback only once the API verdict is known and
                    // empty; otherwise the authoritative API list applied below
                    // would flap against early model spans.
                    if (apiDeferred.isCompleted) {
                        val apiSegments =
                            runCatching { apiDeferred.getCompleted() }
                                .getOrNull()
                                .let { it as? SponsorBlockFetchResult.Success }
                                ?.segments
                                .orEmpty()
                        if (apiSegments.isEmpty()) {
                            onProvisionalPlayback(videoId, update.spans.map { it.asSponsorBlockSegment(videoId) })
                        }
                    }
                }
                val inferenceDeferred =
                    transcript?.let { payload ->
                        async {
                            val cached = cache.get(videoId, payload)
                            if (cached != null) {
                                Log.i(TAG, "Sponsor inference for $videoId served from cache")
                            } else {
                                Log.i(
                                    TAG,
                                    "Sponsor inference for $videoId starting: " +
                                        "cues=${payload.cues.size} lang=${payload.languageTag}",
                                )
                            }
                            cached
                                ?: predictStream(videoId, payload) { update ->
                                    handleProvisional(payload, update)
                                }.also { cache.put(it) }
                        }
                    }
                val apiResult = apiDeferred.await()
                val apiSegments = (apiResult as? SponsorBlockFetchResult.Success)?.segments.orEmpty()
                resolvedApiResult = apiResult
                resolvedApiSegments = apiSegments
                if (transcript == null) {
                    Log.i(TAG, "Sponsor detection for $videoId skipped: no usable English captions")
                    publishIfCurrent(
                        requestGeneration,
                        SponsorDetectionUiState(
                            videoId = videoId,
                            status = SponsorDetectionStatus.SKIPPED,
                            apiOutcome = apiOutcome(apiResult, authoritativeSegments != null),
                            apiSegments = apiSegments,
                            errorMessage = "No usable English captions",
                        ),
                    )
                    return@coroutineScope SponsorDetectionLoadResult(apiSegments, apiSegments)
                }
                val inference =
                    try {
                        checkNotNull(inferenceDeferred).await()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Sponsor detection for $videoId failed: ${error.message ?: error.javaClass.simpleName}")
                        publishIfCurrent(
                            requestGeneration,
                            SponsorDetectionUiState(
                                videoId = videoId,
                                status = SponsorDetectionStatus.ERROR,
                                apiOutcome = apiOutcome(apiResult, authoritativeSegments != null),
                                apiSegments = apiSegments,
                                errorMessage = error.message ?: error.javaClass.simpleName,
                            ),
                        )
                        return@coroutineScope SponsorDetectionLoadResult(apiSegments, apiSegments)
                    }
                val (apiSponsorSpans, comparison) = compareSponsorSpans(inference.spans, apiSegments)
                val outcome = apiOutcome(apiResult, authoritativeSegments != null)
                val failureDetail = apiFailureDetail(apiResult)
                val dedupeKey =
                    sha256(
                        "$videoId|${inference.modelSha256}|${inference.tokenizerSha256}|${inference.transcriptSha256}",
                    )
                val evaluationId = dedupeKey
                val hasConsent = consentEnabled()
                val inferenceSummary = inference.summary()
                if (inference.spans.isEmpty()) {
                    Log.i(
                        TAG,
                        "Sponsor detection for $videoId ran with no predictions: " +
                            "cues=${inferenceSummary.transcriptCueCount} " +
                            "windows=${inferenceSummary.windowCount} apiOutcome=$outcome " +
                            "fromCache=${inferenceSummary.fromCache}",
                    )
                }
                val uiState =
                    SponsorDetectionUiState(
                        videoId = videoId,
                        status = SponsorDetectionStatus.READY,
                        evaluationId = evaluationId,
                        apiOutcome = outcome,
                        apiSegments = apiSegments,
                        predictions = inference.spans,
                        comparison = comparison,
                        inference = inferenceSummary,
                        reviewAvailable = hasConsent,
                    )
                val evaluationEvent =
                    SponsorEvaluationEvent(
                        evaluationId = evaluationId,
                        dedupeKey = dedupeKey,
                        createdAtEpochMs = System.currentTimeMillis(),
                        videoId = videoId,
                        modelName = inference.modelName,
                        modelSha256 = inference.modelSha256,
                        tokenizerSha256 = inference.tokenizerSha256,
                        transcriptSha256 = inference.transcriptSha256,
                        languageTag = transcript.languageTag,
                        transcriptSource = transcript.source,
                        isAutoGenerated = transcript.isAutoGenerated,
                        apiOutcome = outcome,
                        apiFailureDetail = failureDetail,
                        apiSpans = apiSponsorSpans,
                        predictions = inference.spans,
                        comparison = comparison,
                        transcriptWindows =
                            if (hasConsent) {
                                focusedTranscriptWindows(
                                    transcript = transcript,
                                    predictions = inference.spans,
                                    apiSpans = apiSponsorSpans,
                                    seed = dedupeKey,
                                )
                            } else {
                                emptyList()
                            },
                        inferenceMs = inference.inferenceMs,
                        confidenceThreshold = inference.confidenceThreshold,
                        windowCount = inference.windowCount,
                        transcriptCueCount = inference.transcriptCueCount,
                    )
                publishIfCurrent(requestGeneration, uiState, evaluationEvent, transcript)
                if (hasConsent && isCurrent(requestGeneration)) {
                    journalScope.launch { safelyRecordEvaluationIfConsented(evaluationEvent) }
                }
                SponsorDetectionLoadResult(
                    playbackSegments = selectSponsorPlaybackSegments(apiSegments, inference),
                    apiSegments = apiSegments,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publishIfCurrent(
                    requestGeneration,
                    SponsorDetectionUiState(
                        videoId = videoId,
                        status = SponsorDetectionStatus.ERROR,
                        apiOutcome = resolvedApiResult?.let { apiOutcome(it, authoritativeSegments != null) },
                        apiSegments = resolvedApiSegments,
                        errorMessage = error.message ?: error.javaClass.simpleName,
                    ),
                )
                SponsorDetectionLoadResult(resolvedApiSegments, resolvedApiSegments)
            }
        }

    suspend fun recordFeedback(
        verdict: SponsorFeedbackVerdict,
        targetSpan: SponsorPredictedSpan? = null,
        correctedSpan: SponsorSpan? = null,
    ): Boolean {
        if (!consentEnabled()) return false
        val current = state.value
        val evaluationId = current.evaluationId ?: return false
        currentEvaluationEvent?.takeIf { it.evaluationId == evaluationId }?.let { safelyRecordEvaluation(it) }
        val now = System.currentTimeMillis()
        val feedbackId =
            sha256("$evaluationId|${targetSpan?.spanId}|$verdict|${correctedSpan?.startMs}|${correctedSpan?.endMs}|$now")
        val stored =
            try {
                trainingSink.recordFeedback(
                    SponsorFeedbackEvent(
                        feedbackId = feedbackId,
                        evaluationId = evaluationId,
                        createdAtEpochMs = now,
                        targetSpanId = targetSpan?.spanId,
                        verdict = verdict,
                        originalSpan = targetSpan?.let { SponsorSpan(it.startMs, it.endMs) },
                        correctedSpan = correctedSpan,
                        transcriptWindow = correctedSpan?.let(::feedbackTranscriptWindow),
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        if (stored && targetSpan != null) {
            _state.value = current.copy(reviewedSpanIds = current.reviewedSpanIds + targetSpan.spanId)
        }
        return stored
    }

    suspend fun refreshConsent() {
        val enabled = consentEnabled()
        val eventToRecord =
            stateMutex.withLock {
                val event = currentEvaluationEvent
                val transcript = currentTranscript
                val updatedEvent =
                    if (enabled && event != null && transcript != null && event.transcriptWindows.isEmpty()) {
                        event.copy(
                            transcriptWindows =
                                focusedTranscriptWindows(
                                    transcript = transcript,
                                    predictions = event.predictions,
                                    apiSpans = event.apiSpans,
                                    seed = event.dedupeKey,
                                ),
                        )
                    } else {
                        event
                    }
                currentEvaluationEvent = updatedEvent
                _state.value =
                    _state.value.copy(
                        reviewAvailable = enabled && _state.value.status == SponsorDetectionStatus.READY,
                    )
                updatedEvent.takeIf { enabled }
            }
        eventToRecord?.let { safelyRecordEvaluation(it) }
    }

    suspend fun refreshJournalStats() = trainingSink.refreshStats()

    suspend fun exportTo(output: OutputStream) = trainingSink.exportTo(output)

    suspend fun clearJournal() = trainingSink.clear()

    suspend fun reset() {
        stateMutex.withLock {
            generation++
            currentEvaluationEvent = null
            currentTranscript = null
            _state.value = SponsorDetectionUiState()
        }
    }

    suspend fun close() {
        reset()
        closeDetector()
        journalJob.cancel()
    }

    private suspend fun begin(videoId: String): Long =
        stateMutex.withLock {
            generation++
            currentEvaluationEvent = null
            currentTranscript = null
            _state.value = SponsorDetectionUiState(videoId = videoId, status = SponsorDetectionStatus.LOADING)
            generation
        }

    private suspend fun publishIfCurrent(
        requestGeneration: Long,
        value: SponsorDetectionUiState,
        evaluationEvent: SponsorEvaluationEvent? = null,
        transcript: SponsorTranscriptPayload? = null,
    ) {
        stateMutex.withLock {
            if (generation == requestGeneration) {
                currentEvaluationEvent = evaluationEvent
                currentTranscript = transcript
                _state.value = value
            }
        }
    }

    private suspend fun isCurrent(requestGeneration: Long): Boolean = stateMutex.withLock { generation == requestGeneration }

    private fun apiOutcome(
        result: SponsorBlockFetchResult,
        offlineSaved: Boolean,
    ): SponsorApiOutcome =
        if (offlineSaved) {
            SponsorApiOutcome.OFFLINE_SAVED
        } else {
            when (result) {
                is SponsorBlockFetchResult.Success -> SponsorApiOutcome.SUCCESS
                SponsorBlockFetchResult.Empty -> SponsorApiOutcome.EMPTY
                is SponsorBlockFetchResult.HttpFailure -> SponsorApiOutcome.HTTP_FAILURE
                is SponsorBlockFetchResult.NetworkFailure -> SponsorApiOutcome.NETWORK_FAILURE
            }
        }

    private fun apiFailureDetail(result: SponsorBlockFetchResult): String? =
        when (result) {
            is SponsorBlockFetchResult.HttpFailure -> result.statusCode.toString()
            is SponsorBlockFetchResult.NetworkFailure -> result.reason
            else -> null
        }

    private suspend fun safelyRecordEvaluation(event: SponsorEvaluationEvent): Boolean =
        try {
            trainingSink.recordEvaluation(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private suspend fun safelyRecordEvaluationIfConsented(event: SponsorEvaluationEvent): Boolean =
        try {
            consentEnabled() && safelyRecordEvaluation(event)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }

    private fun feedbackTranscriptWindow(span: SponsorSpan): SponsorTranscriptWindow? {
        val transcript = currentTranscript ?: return null
        return focusedTranscriptWindows(
            transcript = transcript,
            predictions = listOf(SponsorPredictedSpan("feedback", span.startMs, span.endMs, 1.0)),
            apiSpans = emptyList(),
            maxNegativeWindows = 0,
            seed = "feedback-${span.startMs}-${span.endMs}",
        ).firstOrNull()
    }

    private companion object {
        const val TAG = "SponsorDetection"
    }
}
