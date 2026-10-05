package io.github.aedev.flow.data.sponsordetection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class SmartSegmentDecoderTest {
    @Test
    fun `extracts independent category heads and applies fixed category thresholds`() {
        val logits = logits(3)
        logits[0][0] = tag(1)
        logits[1][0] = tag(3)
        logits[0][1] = tag(4)
        logits[0][2] = tag(4)

        val decoded = decodeSponsorBilou(logits, listOf(0 until 6, 6 until 12, 12 until 18))

        assertThat(decoded.map { it.category }).containsExactly("sponsor", "selfpromo", "interaction")
        assertThat(decoded.map { it.confidence }.distinct()).hasSize(1)
    }

    @Test
    fun `closes malformed open spans and starts stray continuation tags`() {
        val logits = logits(4)
        logits[0][0] = tag(1)
        logits[1][0] = tag(2)
        logits[2][0] = tag(3)
        logits[3][0] = tag(3)

        val decoded = decodeSponsorBilou(logits, (0 until 4).map { it * 12 until it * 12 + 12 })

        assertThat(decoded.map { it.startCodePoint to it.endCodePoint })
            .containsExactly(0 to 36, 36 to 48)
            .inOrder()
    }

    @Test
    fun `stitches window overlap without joining separate events or categories`() {
        val transcript = AssembledSponsorTranscript("x".repeat(100), listOf(CueRange(0, 100, 0, 10_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 10, 22, 0.91, "sponsor"),
                WindowSponsorSpan(1, 10, 22, 0.88, "sponsor"),
                WindowSponsorSpan(0, 40, 55, 0.92, "sponsor"),
                WindowSponsorSpan(0, 10, 22, 0.9, "selfpromo"),
            )

        val stitched = stitchSponsorSpans(transcript, spans)

        assertThat(stitched).hasSize(3)
        assertThat(stitched.map { it.category }).containsExactly("selfpromo", "sponsor", "sponsor").inOrder()
        assertThat(stitched[0].startMs).isEqualTo(stitched[1].startMs)
    }

    @Test
    fun `legacy prediction payload defaults category to sponsor`() {
        val oldPayload = """{"span_id":"legacy","start_ms":100,"end_ms":200,"confidence":0.9}"""

        val decoded = Json.decodeFromString<SponsorPredictedSpan>(oldPayload)

        assertThat(decoded.category).isEqualTo("sponsor")
        assertThat(decoded.asSponsorBlockSegment("v").category).isEqualTo("sponsor")
    }

    @Test
    fun `predicted category reaches playback segment and feedback span`() {
        val prediction = SponsorPredictedSpan("p", 100, 500, 0.9, "interaction")

        assertThat(prediction.asSponsorBlockSegment("v").category).isEqualTo("interaction")
        val serializedFeedbackSpan = Json.encodeToString(SponsorSpan(100, 500, prediction.category))

        assertThat(Json.decodeFromString<SponsorSpan>(serializedFeedbackSpan).category).isEqualTo("interaction")
    }

    @Test
    fun `normalization retains reserved placeholders when repeated`() {
        val normalized = normalizeSponsorCue("Visit URL_TOKEN and save NUMBER_TOKEN today")
        assertThat(normalizeSponsorCue(normalized)).isEqualTo(normalized)
        assertThat(normalized).contains("URL_TOKEN")
        assertThat(normalized).contains("NUMBER_TOKEN")
    }

    @Test
    fun `minimum span applies after overlap stitching`() {
        val transcript = AssembledSponsorTranscript("x".repeat(100), listOf(CueRange(0, 100, 0, 10_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 0, 11, 0.9, "interaction"),
                WindowSponsorSpan(1, 8, 20, 0.9, "interaction"),
                WindowSponsorSpan(0, 50, 61, 0.9, "sponsor"),
            )

        val stitched = stitchSponsorSpans(transcript, spans)

        assertThat(stitched).hasSize(1)
        assertThat(stitched.single().category).isEqualTo("interaction")
    }

    @Test
    fun `API comparison cannot match an overlapping different category`() {
        val predictions =
            listOf(
                SponsorPredictedSpan("s", 100, 500, 0.9, "sponsor"),
                SponsorPredictedSpan("i", 100, 500, 0.9, "interaction"),
            )
        val segments = listOf(SponsorBlockSegment(category = "interaction", segment = listOf(0.1f, 0.5f), uuid = "api-i"))

        val (api, comparison) = compareSponsorSpans(predictions, segments)

        assertThat(api.single().category).isEqualTo("interaction")
        assertThat(comparison.matches.single().predictionId).isEqualTo("i")
    }

    @Test
    fun `continuity merge reunites one sponsor event split by a confidence dropout`() {
        val transcript = AssembledSponsorTranscript("x".repeat(200), listOf(CueRange(0, 200, 0, 100_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 0, 90, 0.97, "sponsor"),
                WindowSponsorSpan(1, 95, 180, 0.95, "sponsor"),
            )

        val stitched = stitchSponsorSpans(transcript, spans)

        assertThat(stitched).hasSize(1)
        assertThat(stitched.single().startMs).isEqualTo(0)
        assertThat(stitched.single().endMs).isEqualTo(90_000)
    }

    @Test
    fun `continuity merge does not join a large gap`() {
        val transcript = AssembledSponsorTranscript("x".repeat(200), listOf(CueRange(0, 200, 0, 200_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 0, 40, 0.97, "sponsor"),
                WindowSponsorSpan(1, 160, 200, 0.95, "sponsor"),
            )

        assertThat(stitchSponsorSpans(transcript, spans)).hasSize(2)
    }

    @Test
    fun `continuity merge never chains two short fragments`() {
        val transcript = AssembledSponsorTranscript("x".repeat(100), listOf(CueRange(0, 100, 0, 100_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 0, 20, 0.97, "sponsor"),
                WindowSponsorSpan(1, 25, 45, 0.95, "sponsor"),
            )

        assertThat(stitchSponsorSpans(transcript, spans)).hasSize(2)
    }

    @Test
    fun `interaction is left unmerged`() {
        val transcript = AssembledSponsorTranscript("x".repeat(200), listOf(CueRange(0, 200, 0, 100_000)))
        val spans =
            listOf(
                WindowSponsorSpan(0, 0, 90, 0.9, "interaction"),
                WindowSponsorSpan(1, 95, 180, 0.9, "interaction"),
            )

        assertThat(stitchSponsorSpans(transcript, spans)).hasSize(2)
    }

    private fun logits(sequenceSize: Int): List<Array<FloatArray>> = List(sequenceSize) { Array(SPONSOR_MODEL_CATEGORIES.size) { tag(0) } }

    private fun tag(tag: Int): FloatArray = FloatArray(5) { if (it == tag) 4f else 0f }
}
