package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.repository.SponsorBlockFetchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import java.io.OutputStream
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorDetectionCoordinatorTest {
    @Test
    fun `api and model inference overlap`() =
        runTest {
            var concurrent = 0
            var apiRunning = false
            val coordinator =
                coordinator(
                    fetchSegments = {
                        apiRunning = true
                        delay(40)
                        apiRunning = false
                        SponsorBlockFetchResult.Empty
                    },
                    predictStream = { _, transcript, _ ->
                        if (apiRunning) concurrent++
                        delay(40)
                        inference(transcript)
                    },
                    cache = PassthroughCache,
                )

            coordinator.evaluate("video", emptyList())

            assertThat(concurrent).isEqualTo(1)
            assertThat(testScheduler.currentTime).isEqualTo(40)
        }

    @Test
    fun `api segments remain authoritative while model still runs`() =
        runTest {
            var predicted = false
            val api = listOf(segment("api", 10f, 20f))
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Success(api) },
                    predictStream = { _, transcript, _ ->
                        predicted = true
                        inference(transcript, SponsorPredictedSpan("model", 30_000, 40_000, 0.9))
                    },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(predicted).isTrue()
            assertThat(result.playbackSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.predictions).hasSize(1)
        }

    @Test
    fun `late evaluation failure does not discard authoritative api segments`() =
        runTest {
            val api = listOf(segment("api", 10f, 20f))
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Success(api) },
                    consentEnabled = { error("preference read failed") },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(result.playbackSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.apiSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.apiOutcome).isEqualTo(SponsorApiOutcome.SUCCESS)
        }

    @Test
    fun `empty or failed api activates model playback segments`() =
        runTest {
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.NetworkFailure("timeout") },
                    predictStream = { _, transcript, _ ->
                        inference(transcript, SponsorPredictedSpan("model", 1_000, 2_000, 0.8))
                    },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(result.playbackSegments.single().uuid).contains("flow-ml")
            assertThat(coordinator.state.value.apiOutcome).isEqualTo(SponsorApiOutcome.NETWORK_FAILURE)
        }

    @Test
    fun `offline saved segments stay authoritative`() =
        runTest {
            val saved = listOf(segment("offline", 5f, 8f))
            val coordinator =
                coordinator(
                    fetchSegments = { error("network should not be used") },
                    predictStream = { _, transcript, _ -> inference(transcript) },
                )

            val result = coordinator.evaluate("video", emptyList(), saved)

            assertThat(result.playbackSegments).containsExactlyElementsIn(saved)
            assertThat(coordinator.state.value.apiOutcome).isEqualTo(SponsorApiOutcome.OFFLINE_SAVED)
        }

    @Test
    fun `empty prediction stays ready and carries inference provenance`() =
        runTest {
            val transcript = transcript()
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Empty },
                    predictStream = { _, payload, _ ->
                        SponsorInferenceResult(
                            videoId = "video",
                            transcript = payload,
                            transcriptSha256 = sponsorTranscriptSha256(payload),
                            spans = emptyList(),
                            windowCount = 3,
                            inferenceMs = 7,
                        )
                    },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(result.playbackSegments).isEmpty()
            val state = coordinator.state.value
            assertThat(state.status).isEqualTo(SponsorDetectionStatus.READY)
            assertThat(state.predictions).isEmpty()
            assertThat(state.inference?.spanCount).isEqualTo(0)
            assertThat(state.inference?.windowCount).isEqualTo(3)
            assertThat(state.inference?.transcriptCueCount).isEqualTo(transcript.cues.size)
            assertThat(state.inference?.confidenceThreshold).isEqualTo(SPONSOR_CONFIDENCE_THRESHOLD)
        }

    @Test
    fun `missing captions skip inference`() =
        runTest {
            var predicted = false
            val coordinator =
                coordinator(
                    loadCaptions = { null },
                    predictStream = { _, transcript, _ ->
                        predicted = true
                        inference(transcript)
                    },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(predicted).isFalse()
            assertThat(result.playbackSegments).isEmpty()
            assertThat(coordinator.state.value.status).isEqualTo(SponsorDetectionStatus.SKIPPED)
        }

    @Test
    fun `rapid video change suppresses stale results`() =
        runTest {
            val releaseOld = CompletableDeferred<Unit>()
            val oldStarted = CompletableDeferred<Unit>()
            val coordinator =
                coordinator(
                    fetchSegments = { videoId ->
                        if (videoId == "old") {
                            oldStarted.complete(Unit)
                            releaseOld.await()
                            SponsorBlockFetchResult.Success(listOf(segment("old", 1f, 2f)))
                        } else {
                            SponsorBlockFetchResult.Success(listOf(segment("new", 3f, 4f)))
                        }
                    },
                    cache = PassthroughCache,
                )

            val old = async { coordinator.evaluate("old", emptyList()) }
            oldStarted.await()
            coordinator.evaluate("new", emptyList())
            releaseOld.complete(Unit)
            old.await()

            assertThat(coordinator.state.value.videoId).isEqualTo("new")
            assertThat(
                coordinator.state.value.apiSegments
                    .single()
                    .uuid,
            ).isEqualTo("new")
        }

    @Test
    fun `replay uses cache instead of rerunning inference`() =
        runTest {
            var predictions = 0
            val cache = SponsorInferenceCache(Files.createTempDirectory("sponsor-cache").toFile())
            val coordinator =
                coordinator(
                    cache = cache,
                    predictStream = { _, transcript, _ ->
                        predictions++
                        inference(transcript)
                    },
                )

            coordinator.evaluate("video", emptyList())
            coordinator.evaluate("video", emptyList())

            assertThat(predictions).isEqualTo(1)
        }

    @Test
    fun `consent off does not persist evaluation transcripts`() =
        runTest {
            val sink = RecordingSink()
            val coordinator =
                coordinator(
                    trainingSink = sink,
                    consentEnabled = { false },
                    journalDispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.evaluate("video", emptyList())
            advanceUntilIdle()

            assertThat(sink.evaluations).isEmpty()
            assertThat(coordinator.state.value.reviewAvailable).isFalse()
        }

    @Test
    fun `enabling consent adds focused transcript windows to the current evaluation`() =
        runTest {
            var consent = false
            val sink = RecordingSink()
            val coordinator =
                coordinator(
                    trainingSink = sink,
                    consentEnabled = { consent },
                )
            coordinator.evaluate("video", emptyList())

            consent = true
            coordinator.refreshConsent()

            assertThat(coordinator.state.value.reviewAvailable).isTrue()
            assertThat(sink.evaluations).hasSize(1)
            assertThat(sink.evaluations.single().transcriptWindows).isNotEmpty()
        }

    @Test
    fun `pending journal write rechecks disabled consent`() =
        runTest {
            var consent = true
            val sink = RecordingSink()
            val coordinator =
                coordinator(
                    trainingSink = sink,
                    consentEnabled = { consent },
                    journalDispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.evaluate("video", emptyList())
            consent = false
            advanceUntilIdle()

            assertThat(sink.evaluations).isEmpty()
        }

    @Test
    fun `journal writes do not block playback results`() =
        runTest {
            val journalDispatcher = StandardTestDispatcher(testScheduler)
            val started = CompletableDeferred<Unit>()
            val sink =
                object : RecordingSink() {
                    override suspend fun recordEvaluation(event: SponsorEvaluationEvent): Boolean {
                        started.complete(Unit)
                        delay(1_000)
                        return super.recordEvaluation(event)
                    }
                }
            val coordinator =
                coordinator(
                    trainingSink = sink,
                    journalDispatcher = journalDispatcher,
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(result.playbackSegments).isNotEmpty()
            assertThat(sink.evaluations).isEmpty()
            started.await()
            advanceUntilIdle()
            assertThat(sink.evaluations).hasSize(1)
            assertThat(sink.evaluations.single().transcriptWindows).isNotEmpty()
        }

    @Test
    fun `streaming publishes provisional predictions and playback before final`() =
        runTest {
            val provisionalPlayback = mutableListOf<List<SponsorBlockSegment>>()
            var sawProvisionalState = false
            val span = SponsorPredictedSpan("model", 10_000, 20_000, 0.9)
            lateinit var coordinator: SponsorDetectionCoordinator
            coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Empty },
                    predictStream = { _, payload, onUpdate ->
                        onUpdate(SponsorStreamUpdate(listOf(span), windowsDone = 1, windowTotal = 2))
                        sawProvisionalState = coordinator.state.value.isProvisional
                        onUpdate(SponsorStreamUpdate(listOf(span), windowsDone = 2, windowTotal = 2))
                        inference(payload, span)
                    },
                    onProvisionalPlayback = { _, segments -> provisionalPlayback += segments },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(provisionalPlayback).hasSize(2)
            assertThat(sawProvisionalState).isTrue()
            assertThat(result.playbackSegments.single().uuid).contains("flow-ml")
            val state = coordinator.state.value
            assertThat(state.status).isEqualTo(SponsorDetectionStatus.READY)
            assertThat(state.isProvisional).isFalse()
            assertThat(state.predictions).hasSize(1)
        }

    @Test
    fun `streaming does not override authoritative api segments`() =
        runTest {
            var provisionalCalls = 0
            val api = listOf(segment("api", 10f, 20f))
            val span = SponsorPredictedSpan("model", 30_000, 40_000, 0.9)
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Success(api) },
                    predictStream = { _, payload, onUpdate ->
                        onUpdate(SponsorStreamUpdate(listOf(span), windowsDone = 1, windowTotal = 1))
                        inference(payload, span)
                    },
                    onProvisionalPlayback = { _, _ -> provisionalCalls++ },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(provisionalCalls).isEqualTo(0)
            assertThat(result.playbackSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.isProvisional).isFalse()
        }

    @Test
    fun `on-device disabled skips inference but keeps api segments`() =
        runTest {
            var predicted = false
            val api = listOf(segment("api", 10f, 20f))
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Success(api) },
                    predictStream = { _, transcript, _ ->
                        predicted = true
                        inference(transcript)
                    },
                    onDeviceEnabled = { false },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(predicted).isFalse()
            assertThat(result.playbackSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.status).isEqualTo(SponsorDetectionStatus.SKIPPED)
        }

    @Test
    fun `missing on-device model skips inference but keeps api segments`() =
        runTest {
            var predicted = false
            val api = listOf(segment("api", 10f, 20f))
            val coordinator =
                coordinator(
                    fetchSegments = { SponsorBlockFetchResult.Success(api) },
                    predictStream = { _, transcript, _ ->
                        predicted = true
                        inference(transcript)
                    },
                    onDeviceModelInstalled = { false },
                )

            val result = coordinator.evaluate("video", emptyList())

            assertThat(predicted).isFalse()
            assertThat(result.playbackSegments).containsExactlyElementsIn(api)
            assertThat(coordinator.state.value.status).isEqualTo(SponsorDetectionStatus.SKIPPED)
        }

    private fun coordinator(
        fetchSegments: suspend (String) -> SponsorBlockFetchResult = { SponsorBlockFetchResult.Empty },
        loadCaptions: suspend (List<SubtitlesStream>) -> SponsorTranscriptPayload? = {
            transcript()
        },
        predictStream: suspend (
            String,
            SponsorTranscriptPayload,
            suspend (SponsorStreamUpdate) -> Unit,
        ) -> SponsorInferenceResult = { _, payload, _ -> inference(payload) },
        cache: SponsorPredictionCache = PassthroughCache,
        trainingSink: SponsorTrainingSink = RecordingSink(),
        consentEnabled: suspend () -> Boolean = { true },
        onProvisionalPlayback: suspend (String, List<SponsorBlockSegment>) -> Unit = { _, _ -> },
        onDeviceEnabled: suspend () -> Boolean = { true },
        onDeviceModelInstalled: () -> Boolean = { true },
        journalDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    ) = SponsorDetectionCoordinator(
        fetchSegments = fetchSegments,
        loadCaptions = loadCaptions,
        predictStream = predictStream,
        cache = cache,
        trainingSink = trainingSink,
        consentEnabled = consentEnabled,
        onProvisionalPlayback = onProvisionalPlayback,
        onDeviceEnabled = onDeviceEnabled,
        onDeviceModelInstalled = onDeviceModelInstalled,
        journalDispatcher = journalDispatcher,
    )
}

private object PassthroughCache : SponsorPredictionCache {
    override suspend fun get(
        videoId: String,
        transcript: SponsorTranscriptPayload,
    ): SponsorInferenceResult? = null

    override suspend fun put(result: SponsorInferenceResult) = Unit
}

private open class RecordingSink : SponsorTrainingSink {
    val evaluations = mutableListOf<SponsorEvaluationEvent>()
    private val _stats = MutableStateFlow(SponsorJournalStats())
    override val stats: StateFlow<SponsorJournalStats> = _stats

    override suspend fun recordEvaluation(event: SponsorEvaluationEvent): Boolean {
        evaluations += event
        return true
    }

    override suspend fun recordFeedback(event: SponsorFeedbackEvent): Boolean = true

    override suspend fun refreshStats() = Unit

    override suspend fun exportTo(output: OutputStream) = Unit

    override suspend fun clear() = Unit
}

private fun transcript() =
    SponsorTranscriptPayload(
        languageTag = "en",
        source = SponsorTranscriptSource.YOUTUBE,
        isAutoGenerated = false,
        cues = (0 until 12).map { index -> DetectionTranscriptCue(index * 10_000L, index * 10_000L + 9_000, "cue $index") },
    )

private fun inference(
    transcript: SponsorTranscriptPayload,
    span: SponsorPredictedSpan = SponsorPredictedSpan("model", 10_000, 20_000, 0.9),
) = SponsorInferenceResult(
    videoId = "video",
    transcript = transcript,
    transcriptSha256 = sponsorTranscriptSha256(transcript),
    spans = listOf(span),
    inferenceMs = 1,
)

private fun segment(
    id: String,
    start: Float,
    end: Float,
) = SponsorBlockSegment("sponsor", listOf(start, end), id)
