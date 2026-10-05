package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockSegment
import org.junit.Test

class SponsorDetectionDataTest {
    @Test
    fun `api segments remain authoritative over model predictions`() {
        val api = listOf(segment("api", 10f, 20f))
        val inference = inference(SponsorPredictedSpan("model", 30_000, 40_000, 0.9))

        assertThat(selectSponsorPlaybackSegments(api, inference)).containsExactlyElementsIn(api)
    }

    @Test
    fun `timeline overlay adds model-only spans when api playback is present`() {
        val api = listOf(segment("api", 10f, 20f))
        val detection =
            SponsorDetectionUiState(
                videoId = "video",
                predictions = listOf(SponsorPredictedSpan("model", 30_000, 40_000, 0.9)),
            )

        val overlay = overlaySponsorTimelineSegments(api, detection)

        assertThat(overlay.map { it.uuid }).containsExactly("api", "flow-ml-video-model").inOrder()
    }

    @Test
    fun `timeline overlay does not duplicate model playback segments`() {
        val inference = inference(SponsorPredictedSpan("model", 30_000, 40_000, 0.9))
        val playback = selectSponsorPlaybackSegments(emptyList(), inference)
        val detection =
            SponsorDetectionUiState(
                videoId = "video",
                predictions = inference.spans,
            )

        assertThat(overlaySponsorTimelineSegments(playback, detection)).isEqualTo(playback)
    }

    @Test
    fun `timeline overlay keeps playback when detection has no video or predictions`() {
        val api = listOf(segment("api", 10f, 20f))

        assertThat(overlaySponsorTimelineSegments(api, SponsorDetectionUiState())).isEqualTo(api)
        assertThat(
            overlaySponsorTimelineSegments(api, SponsorDetectionUiState(videoId = "video")),
        ).isEqualTo(api)
    }

    @Test
    fun `model predictions become playback segments when api is empty`() {
        val inference = inference(SponsorPredictedSpan("model", 30_000, 40_000, 0.9))

        val selected = selectSponsorPlaybackSegments(emptyList(), inference)

        assertThat(selected).hasSize(1)
        assertThat(selected.single().uuid).contains("flow-ml")
    }

    @Test
    fun `comparison uses one to one matches at temporal iou threshold`() {
        val predictions =
            listOf(
                SponsorPredictedSpan("exact", 0, 10_000, 0.9),
                SponsorPredictedSpan("half", 20_000, 30_000, 0.8),
                SponsorPredictedSpan("below", 40_000, 50_000, 0.7),
            )
        val api =
            listOf(
                segment("api-exact", 0f, 10f),
                segment("api-half", 20f, 40f),
                segment("api-below", 46f, 56f),
                SponsorBlockSegment("intro", listOf(60f, 70f), "intro"),
            )

        val (apiSpans, comparison) = compareSponsorSpans(predictions, api)

        assertThat(apiSpans.map { it.id }).containsExactly("api-exact", "api-half", "api-below")
        assertThat(comparison.matches.map { it.predictionId }).containsExactly("exact", "half")
        assertThat(comparison.matches.single { it.predictionId == "half" }.iou).isWithin(0.0001).of(0.5)
        assertThat(comparison.modelOnlyIds).containsExactly("below")
        assertThat(comparison.apiOnlyIds).containsExactly("api-below")
    }

    @Test
    fun `comparison keeps one to one matches when several predictions overlap one api span`() {
        val predictions =
            listOf(
                SponsorPredictedSpan("better", 0, 10_000, 0.9),
                SponsorPredictedSpan("worse", 2_000, 12_000, 0.8),
            )
        val api = listOf(segment("api", 0f, 10f))

        val (_, comparison) = compareSponsorSpans(predictions, api)

        assertThat(comparison.matches.map { it.predictionId }).containsExactly("better")
        assertThat(comparison.modelOnlyIds).containsExactly("worse")
        assertThat(comparison.apiOnlyIds).isEmpty()
    }

    @Test
    fun `invalid and unsupported category API ranges are excluded from evaluation`() {
        val predictions = listOf(SponsorPredictedSpan("model", 0, 10_000, 0.9))
        val api =
            listOf(
                segment("zero", 4f, 4f),
                segment("reversed", 8f, 3f),
                SponsorBlockSegment("intro", listOf(0f, 10f), "intro"),
            )

        val (apiSpans, comparison) = compareSponsorSpans(predictions, api)

        assertThat(apiSpans).isEmpty()
        assertThat(comparison.matches).isEmpty()
        assertThat(comparison.modelOnlyIds).containsExactly("model")
        assertThat(comparison.apiOnlyIds).isEmpty()
    }

    @Test
    fun `focused windows merge context and select deterministic negatives`() {
        val transcript =
            SponsorTranscriptPayload(
                languageTag = "en",
                source = SponsorTranscriptSource.YOUTUBE,
                isAutoGenerated = false,
                cues = (0 until 20).map { index -> DetectionTranscriptCue(index * 10_000L, index * 10_000L + 9_000, "cue $index") },
            )
        val prediction = SponsorPredictedSpan("p", 50_000, 60_000, 0.9)

        val first = focusedTranscriptWindows(transcript, listOf(prediction), emptyList(), seed = "stable")
        val second = focusedTranscriptWindows(transcript, listOf(prediction), emptyList(), seed = "stable")

        assertThat(first).isEqualTo(second)
        assertThat(first.first().kind).isEqualTo(SponsorTranscriptWindowKind.SEGMENT_CONTEXT)
        assertThat(first.first().startMs).isEqualTo(20_000)
        assertThat(first.first().endMs).isEqualTo(90_000)
        assertThat(first.count { it.kind == SponsorTranscriptWindowKind.WEAK_NEGATIVE }).isAtMost(3)
    }

    @Test
    fun `stitch drops window spans below the confidence threshold`() {
        val transcript = AssembledSponsorTranscript("x".repeat(100), listOf(CueRange(0, 100, 0, 10_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 10, 22, 0.9),
                WindowSponsorSpan(0, 40, 52, 0.4),
            )

        val kept = stitchSponsorSpans(transcript, spans, confidenceThreshold = 0.5)
        val frozenPoint = stitchSponsorSpans(transcript, spans, confidenceThreshold = SPONSOR_CONFIDENCE_THRESHOLD)

        assertThat(kept).hasSize(1)
        assertThat(kept.single().confidence).isWithin(0.0001).of(0.9)
        assertThat(frozenPoint).hasSize(1)
        assertThat(frozenPoint.single().confidence).isWithin(0.0001).of(0.9)
    }

    @Test
    fun `transcript hash changes with cue text`() {
        val original = transcript("sponsor copy")
        val changed = transcript("different copy")

        assertThat(sponsorTranscriptSha256(original)).isNotEqualTo(sponsorTranscriptSha256(changed))
        assertThat(sponsorTranscriptSha256(original)).hasLength(64)
    }

    private fun transcript(text: String) =
        SponsorTranscriptPayload(
            languageTag = "en",
            source = SponsorTranscriptSource.YOUTUBE,
            isAutoGenerated = false,
            cues = listOf(DetectionTranscriptCue(0, 1_000, text)),
        )

    private fun inference(span: SponsorPredictedSpan) =
        SponsorInferenceResult(
            videoId = "video",
            transcript = transcript("copy"),
            transcriptSha256 = "a".repeat(64),
            spans = listOf(span),
            inferenceMs = 1,
        )

    private fun segment(
        id: String,
        startSeconds: Float,
        endSeconds: Float,
    ) = SponsorBlockSegment("sponsor", listOf(startSeconds, endSeconds), id)
}
