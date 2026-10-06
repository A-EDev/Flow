package io.github.aedev.flow.data.sponsordetection

import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val SPONSOR_MODEL_NAME = "curated_category_encoder_ensemble_20261004"
const val SPONSOR_MODEL_SHA256 = "4ff3d44f921de64b370745b1c9961d6ab1ab9aee8c703f39b6d6e22b1fdd7d34"
const val SPONSOR_TOKENIZER_SHA256 = "6c8aaa9a542084f2457eab775d4eeb51f92a70c0fd9de28d5edb0ddec3c08d30"

/** Sponsor-head threshold; category decoder thresholds are configured separately. */
const val SPONSOR_CONFIDENCE_THRESHOLD = 0.7

/**
 * Bump whenever the on-device decode/stitch logic changes (window size, thresholds,
 * minimum span, continuity merge, ...). The prediction cache keys on this so a
 * cached result produced by older logic is never replayed against new decoding.
 */
const val SPONSOR_DECODE_LOGIC_VERSION = 2

@Serializable
data class SponsorPredictedSpan(
    @SerialName("span_id") val spanId: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val confidence: Double,
    val category: String = "sponsor",
) {
    init {
        require(spanId.isNotBlank())
        require(startMs >= 0)
        require(endMs > startMs)
        require(confidence in 0.0..1.0)
        require(category in SPONSOR_MODEL_CATEGORIES)
    }

    fun asSponsorBlockSegment(videoId: String): SponsorBlockSegment =
        SponsorBlockSegment(
            category = category,
            segment = listOf(startMs / 1000f, endMs / 1000f),
            uuid = "flow-ml-$videoId-$spanId",
            actionType = "skip",
        )
}

internal val SPONSOR_MODEL_CATEGORIES = listOf("sponsor", "selfpromo", "interaction")

internal data class SponsorInferenceResult(
    val videoId: String,
    val modelName: String = SPONSOR_MODEL_NAME,
    val modelSha256: String = SPONSOR_MODEL_SHA256,
    val tokenizerSha256: String = SPONSOR_TOKENIZER_SHA256,
    val confidenceThreshold: Double = SPONSOR_CONFIDENCE_THRESHOLD,
    val transcript: SponsorTranscriptPayload,
    val transcriptSha256: String,
    val spans: List<SponsorPredictedSpan>,
    val windowCount: Int = 0,
    val inferenceMs: Long,
    val fromCache: Boolean = false,
) {
    val transcriptCueCount: Int get() = transcript.cues.size

    val playbackSegments: List<SponsorBlockSegment>
        get() = spans.map { it.asSponsorBlockSegment(videoId) }

    fun summary(): SponsorInferenceSummary =
        SponsorInferenceSummary(
            modelName = modelName,
            confidenceThreshold = confidenceThreshold,
            transcriptCueCount = transcriptCueCount,
            windowCount = windowCount,
            spanCount = spans.size,
            inferenceMs = inferenceMs,
            fromCache = fromCache,
        )
}

/** Provenance carried in UI state so an empty prediction is distinguishable from "never ran". */
data class SponsorInferenceSummary(
    val modelName: String,
    val confidenceThreshold: Double,
    val transcriptCueCount: Int,
    val windowCount: Int,
    val spanCount: Int,
    val inferenceMs: Long,
    val fromCache: Boolean,
)

@Serializable
data class SponsorSpan(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val category: String = "sponsor",
) {
    init {
        require(startMs >= 0)
        require(endMs > startMs)
        require(category in SPONSOR_MODEL_CATEGORIES)
    }
}

@Serializable
data class SponsorApiSpan(
    val id: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val category: String = "sponsor",
)

@Serializable
data class SponsorSpanMatch(
    @SerialName("prediction_id") val predictionId: String,
    @SerialName("api_id") val apiId: String,
    val iou: Double,
    @SerialName("start_error_ms") val startErrorMs: Long,
    @SerialName("end_error_ms") val endErrorMs: Long,
)

@Serializable
data class SponsorComparison(
    val matches: List<SponsorSpanMatch>,
    @SerialName("model_only_ids") val modelOnlyIds: List<String>,
    @SerialName("api_only_ids") val apiOnlyIds: List<String>,
)

@Serializable
enum class SponsorApiOutcome {
    SUCCESS,
    EMPTY,
    HTTP_FAILURE,
    NETWORK_FAILURE,
    OFFLINE_SAVED,
    DISABLED,
}

@Serializable
enum class SponsorTranscriptSource {
    @SerialName("youtube")
    YOUTUBE,

    @SerialName("offline_caption")
    OFFLINE_CAPTION,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
data class SponsorTranscriptCue(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val text: String,
)

@Serializable
data class SponsorTranscriptWindow(
    val kind: SponsorTranscriptWindowKind,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    val cues: List<SponsorTranscriptCue>,
)

@Serializable
enum class SponsorTranscriptWindowKind {
    SEGMENT_CONTEXT,
    WEAK_NEGATIVE,
}

@Serializable
data class SponsorEvaluationEvent(
    @SerialName("event_type") val eventType: String = "evaluation",
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("evaluation_id") val evaluationId: String,
    @SerialName("dedupe_key") val dedupeKey: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("video_id") val videoId: String,
    @SerialName("model_name") val modelName: String,
    @SerialName("model_sha256") val modelSha256: String,
    @SerialName("tokenizer_sha256") val tokenizerSha256: String,
    @SerialName("transcript_sha256") val transcriptSha256: String,
    @SerialName("preprocessing_version") val preprocessingVersion: Int = 1,
    @SerialName("language_tag") val languageTag: String,
    @SerialName("transcript_source") val transcriptSource: SponsorTranscriptSource,
    @SerialName("is_auto_generated") val isAutoGenerated: Boolean,
    @SerialName("api_outcome") val apiOutcome: SponsorApiOutcome,
    @SerialName("api_failure_detail") val apiFailureDetail: String? = null,
    @SerialName("api_spans") val apiSpans: List<SponsorApiSpan>,
    val predictions: List<SponsorPredictedSpan>,
    val comparison: SponsorComparison,
    @SerialName("transcript_windows") val transcriptWindows: List<SponsorTranscriptWindow>,
    @SerialName("inference_ms") val inferenceMs: Long,
    @SerialName("confidence_threshold") val confidenceThreshold: Double = SPONSOR_CONFIDENCE_THRESHOLD,
    @SerialName("window_count") val windowCount: Int = 0,
    @SerialName("transcript_cue_count") val transcriptCueCount: Int = 0,
)

@Serializable
data class SponsorFeedbackEvent(
    @SerialName("event_type") val eventType: String = "feedback",
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("feedback_id") val feedbackId: String,
    @SerialName("evaluation_id") val evaluationId: String,
    @SerialName("created_at_epoch_ms") val createdAtEpochMs: Long,
    @SerialName("target_span_id") val targetSpanId: String? = null,
    val verdict: SponsorFeedbackVerdict,
    @SerialName("original_span") val originalSpan: SponsorSpan? = null,
    @SerialName("corrected_span") val correctedSpan: SponsorSpan? = null,
    @SerialName("transcript_window") val transcriptWindow: SponsorTranscriptWindow? = null,
)

@Serializable
enum class SponsorFeedbackVerdict {
    ACCEPTED,
    REJECTED,
    CORRECTED,
    MISSED,
    CONFIRMED_NO_SPONSOR,
}

data class SponsorJournalStats(
    val evaluationCount: Int = 0,
    val feedbackCount: Int = 0,
    val sizeBytes: Long = 0,
    val isFull: Boolean = false,
)

enum class SponsorDetectionStatus {
    IDLE,
    LOADING,
    READY,
    SKIPPED,
    ERROR,
}

data class SponsorDetectionUiState(
    val videoId: String? = null,
    val status: SponsorDetectionStatus = SponsorDetectionStatus.IDLE,
    val evaluationId: String? = null,
    val apiOutcome: SponsorApiOutcome? = null,
    val apiSegments: List<SponsorBlockSegment> = emptyList(),
    val predictions: List<SponsorPredictedSpan> = emptyList(),
    val comparison: SponsorComparison? = null,
    val inference: SponsorInferenceSummary? = null,
    /** True while [predictions] are partial streaming results, replaced by the final stitch. */
    val isProvisional: Boolean = false,
    val reviewedSpanIds: Set<String> = emptySet(),
    val reviewAvailable: Boolean = false,
    val errorMessage: String? = null,
)
