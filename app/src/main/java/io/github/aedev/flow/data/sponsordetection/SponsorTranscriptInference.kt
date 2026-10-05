package io.github.aedev.flow.data.sponsordetection

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

internal data class DetectionTranscriptCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

internal data class SponsorDetectionSpan(
    val startMs: Long,
    val endMs: Long,
    val confidence: Double,
    val category: String = "sponsor",
)

internal data class AssembledSponsorTranscript(
    val text: String,
    val cueRanges: List<CueRange>,
) {
    val codePointLength: Int = text.codePointCount(0, text.length)
    private val cueEndCodePoints = IntArray(cueRanges.size) { cueRanges[it].endCodePoint }

    fun timestampsForSpan(
        startCodePoint: Int,
        endCodePoint: Int,
    ): Pair<Long, Long> {
        require(startCodePoint in 0 until endCodePoint && endCodePoint <= codePointLength)
        return timestampForCharacter(startCodePoint, false) to timestampForCharacter(endCodePoint, true)
    }

    private fun timestampForCharacter(
        character: Int,
        endBoundary: Boolean,
    ): Long {
        val bounded = character.coerceIn(0, codePointLength)
        var low = 0
        var high = cueEndCodePoints.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (bounded < cueEndCodePoints[middle]) {
                high = middle
            } else {
                low = middle + 1
            }
        }
        var index = low
        var range = cueRanges[index]
        if (bounded < range.startCodePoint && index > 0 && endBoundary) {
            index--
            range = cueRanges[index]
        }
        val length = (range.endCodePoint - range.startCodePoint).coerceAtLeast(1)
        val fraction = ((bounded - range.startCodePoint).toDouble() / length).coerceIn(0.0, 1.0)
        return (range.startMs + fraction * (range.endMs - range.startMs)).roundToLong()
    }
}

internal data class CueRange(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val startMs: Long,
    val endMs: Long,
)

internal const val SPONSOR_INFERENCE_BATCH_SIZE = 4
internal const val SPONSOR_INFERENCE_LOOKAHEAD_MS = 15L * 60L * 1000L
internal const val SPONSOR_INFERENCE_INTRA_OP_THREADS = 2
internal const val SPONSOR_WINDOW_MAX_LENGTH = 1024
internal const val SPONSOR_WINDOW_OVERLAP_TOKENS = 128

internal data class SponsorInferenceRuntimeConfig(
    val batchSize: Int = SPONSOR_INFERENCE_BATCH_SIZE,
    val intraOpThreads: Int = SPONSOR_INFERENCE_INTRA_OP_THREADS,
) {
    init {
        require(batchSize > 0) { "Sponsor inference batch size must be positive" }
        require(intraOpThreads > 0) { "Sponsor inference thread count must be positive" }
    }

    companion object {
        fun forAvailableHardware(
            coreCount: Int =
                java.lang.Runtime
                    .getRuntime()
                    .availableProcessors(),
        ): SponsorInferenceRuntimeConfig {
            require(coreCount > 0) { "Available processor count must be positive" }
            val batchSize = if (coreCount <= 4) 2 else SPONSOR_INFERENCE_BATCH_SIZE
            val intraOpThreads =
                when {
                    coreCount >= 8 -> 4
                    coreCount >= 6 -> 3
                    else -> 2
                }.coerceAtMost(coreCount)
            return SponsorInferenceRuntimeConfig(batchSize, intraOpThreads)
        }
    }
}

private const val CLS_TOKEN_ID = 50281L
private const val SEP_TOKEN_ID = 50282L

internal data class SponsorInferenceWindow(
    val index: Int,
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val offsets: List<IntRange>,
    val startMs: Long,
    val endMs: Long,
)

internal data class WindowSponsorSpan(
    val windowIndex: Int,
    val startCodePoint: Int,
    val endCodePoint: Int,
    val confidence: Double,
    val category: String = "sponsor",
)

internal fun normalizeSponsorCue(text: String): String {
    val lowered = text.lowercase(Locale.ROOT).replace(WHITESPACE_PATTERN, " ").trim()
    val urlsReplaced = lowered.replace(URL_PATTERN, "URL_TOKEN")
    val replaced =
        urlsReplaced
            .replace(NUMBER_PATTERN, "NUMBER_TOKEN")
            .replace(PLACEHOLDER_PATTERN) { it.value.uppercase(Locale.ROOT) }
    return Normalizer.normalize(replaced, Normalizer.Form.NFC)
}

internal fun assembleSponsorTranscript(cues: List<DetectionTranscriptCue>): AssembledSponsorTranscript {
    val text = StringBuilder()
    val ranges = mutableListOf<CueRange>()
    var cursor = 0
    var previousStart = -1L
    for (cue in cues) {
        require(cue.startMs >= previousStart && cue.endMs >= cue.startMs)
        previousStart = cue.startMs
        val normalized = normalizeSponsorCue(cue.text)
        if (normalized.isEmpty()) continue
        if (text.isNotEmpty()) {
            text.append(' ')
            cursor++
        }
        val start = cursor
        text.append(normalized)
        cursor += normalized.codePointCount(0, normalized.length)
        ranges += CueRange(start, cursor, cue.startMs, cue.endMs)
    }
    return AssembledSponsorTranscript(text.toString(), ranges)
}

internal fun buildSponsorWindows(
    transcript: AssembledSponsorTranscript,
    tokenizer: SponsorTokenizer,
    maxLength: Int = SPONSOR_WINDOW_MAX_LENGTH,
    overlapTokens: Int = SPONSOR_WINDOW_OVERLAP_TOKENS,
): List<SponsorInferenceWindow> = sponsorWindowSequence(transcript, tokenizer, maxLength, overlapTokens).toList()

internal fun sponsorWindowSequence(
    transcript: AssembledSponsorTranscript,
    tokenizer: SponsorTokenizer,
    maxLength: Int = SPONSOR_WINDOW_MAX_LENGTH,
    overlapTokens: Int = SPONSOR_WINDOW_OVERLAP_TOKENS,
): Sequence<SponsorInferenceWindow> =
    sequence {
        require(maxLength > 2 && overlapTokens in 0 until maxLength - 2)
        if (transcript.text.isEmpty()) return@sequence
        val contentCapacity = maxLength - 2
        val step = contentCapacity - overlapTokens
        val buffer = ArrayDeque<SponsorToken>()
        var windowIndex = 0
        for (token in tokenizer.encodeContent(transcript.text)) {
            buffer += token
            if (buffer.size >= contentCapacity) {
                buildSponsorInferenceWindow(transcript, windowIndex, buffer.take(contentCapacity))
                    ?.let { yield(it) }
                windowIndex++
                repeat(step) { if (buffer.isNotEmpty()) buffer.removeFirst() }
            }
        }
        if (buffer.isNotEmpty() && (windowIndex == 0 || buffer.size > overlapTokens)) {
            buildSponsorInferenceWindow(transcript, windowIndex, buffer.toList())?.let { yield(it) }
        }
    }

private fun buildSponsorInferenceWindow(
    transcript: AssembledSponsorTranscript,
    index: Int,
    selected: List<SponsorToken>,
): SponsorInferenceWindow? {
    val tokens =
        buildList {
            add(SponsorToken(CLS_TOKEN_ID, 0, 0))
            addAll(selected)
            add(SponsorToken(SEP_TOKEN_ID, 0, 0))
        }
    val offsets = tokens.map { it.startCodePoint until it.endCodePoint }
    val contentOffsets = offsets.filter { !it.isEmpty() }
    if (contentOffsets.isEmpty()) return null
    val startChar = contentOffsets.first().first
    val endChar = contentOffsets.last().last + 1
    val times = transcript.timestampsForSpan(startChar, endChar)
    return SponsorInferenceWindow(
        index = index,
        inputIds = tokens.map { it.id }.toLongArray(),
        attentionMask = LongArray(tokens.size) { 1L },
        offsets = offsets,
        startMs = times.first,
        endMs = times.second,
    )
}

internal fun isSponsorWindowDue(
    window: SponsorInferenceWindow,
    positionMs: Long,
    lookaheadMs: Long = SPONSOR_INFERENCE_LOOKAHEAD_MS,
): Boolean {
    val horizon = positionMs.coerceAtLeast(0L) + lookaheadMs.coerceAtLeast(0L)
    return window.endMs >= positionMs && window.startMs <= horizon
}

internal fun selectSponsorInferenceBatch(
    remaining: List<SponsorInferenceWindow>,
    positionMs: Long,
    lookaheadMs: Long = SPONSOR_INFERENCE_LOOKAHEAD_MS,
    batchSize: Int = SPONSOR_INFERENCE_BATCH_SIZE,
    tokenizerComplete: Boolean,
): List<SponsorInferenceWindow> {
    if (remaining.isEmpty() || batchSize <= 0) return emptyList()
    val due =
        remaining
            .filter { isSponsorWindowDue(it, positionMs, lookaheadMs) }
            .sortedBy { it.startMs }
    if (due.isNotEmpty()) return due.take(batchSize)
    if (!tokenizerComplete) return emptyList()
    val horizon = positionMs.coerceAtLeast(0L) + lookaheadMs.coerceAtLeast(0L)
    val future = remaining.filter { it.startMs > horizon }.sortedBy { it.startMs }
    if (future.isNotEmpty()) return future.take(batchSize)
    return remaining.sortedByDescending { it.endMs }.take(batchSize)
}

internal const val SPONSOR_MIN_SPAN_CHARS = 12
internal val SPONSOR_CATEGORY_THRESHOLDS = mapOf("sponsor" to 0.7, "selfpromo" to 0.875, "interaction" to 0.825)

// A category head can lose confidence in the middle of one event (most visibly a
// sponsor read whose narrative lead-in is less overtly promotional), so decoding
// splits it into pieces with a hole and a skip lets the middle play. These gaps
// reunite such pieces. Sponsor is the skip target and gets the largest gap;
// selfpromo is advisory; interaction is left unmerged because it under-detects
// and bridging only extends false events.
internal val SPONSOR_CONTINUITY_GAP_MS = mapOf("sponsor" to 30_000L, "selfpromo" to 15_000L, "interaction" to 0L)
internal const val SPONSOR_CONTINUITY_MIN_ANCHOR_CHARS = 40

internal fun decodeSponsorBilou(
    logits: List<Array<FloatArray>>,
    offsets: List<IntRange>,
): List<WindowDecodedSpan> {
    require(logits.size == offsets.size)
    require(logits.all { it.size == SPONSOR_MODEL_CATEGORIES.size && it.all { head -> head.size == 5 } })
    val decoded = mutableListOf<WindowDecodedSpan>()
    SPONSOR_MODEL_CATEGORIES.forEachIndexed { categoryIndex, category ->
        var openStart = -1
        val openProbabilities = mutableListOf<Double>()

        fun close(endIndex: Int) {
            if (openStart < 0) return
            val confidence = openProbabilities.average()
            if (confidence >= (SPONSOR_CATEGORY_THRESHOLDS[category] ?: 0.5)) {
                val startOffset = offsets[openStart]
                val endOffset = offsets[endIndex - 1]
                val startChar = startOffset.first
                val endChar = endOffset.last + 1
                decoded +=
                    WindowDecodedSpan(
                        startChar,
                        endChar,
                        (confidence * 1_000_000).roundToLong() / 1_000_000.0,
                        category,
                    )
            }
            openStart = -1
            openProbabilities.clear()
        }
        offsets.indices.forEach { index ->
            val offset = offsets[index]
            if (offset.isEmpty()) {
                close(index)
                return@forEach
            }
            val values = logits[index][categoryIndex]
            val maximum = values.maxOrNull() ?: return@forEach
            val exponentials = DoubleArray(values.size) { kotlin.math.exp(values[it].toDouble() - maximum.toDouble()) }
            val probabilitySum = exponentials.sum()
            val tag = values.indices.maxBy { values[it] }
            val probability = exponentials[tag] / probabilitySum
            when (tag) {
                0 -> {
                    close(index)
                }

                1, 4 -> {
                    close(index)
                    openStart = index
                    openProbabilities += probability
                    if (tag == 4) close(index + 1)
                }

                2, 3 -> {
                    if (openStart < 0) openStart = index
                    openProbabilities += probability
                    if (tag == 3) close(index + 1)
                }
            }
        }
        close(offsets.size)
    }
    return decoded.sortedWith(
        compareBy(
            WindowDecodedSpan::startCodePoint,
            WindowDecodedSpan::endCodePoint,
            WindowDecodedSpan::category,
        ),
    )
}

internal data class WindowDecodedSpan(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val confidence: Double,
    val category: String = "sponsor",
)

internal fun stitchSponsorSpans(
    transcript: AssembledSponsorTranscript,
    spans: List<WindowSponsorSpan>,
    confidenceThreshold: Double? = null,
): List<SponsorDetectionSpan> {
    val selected =
        spans
            .filter {
                it.confidence >= (confidenceThreshold ?: SPONSOR_CATEGORY_THRESHOLDS.getValue(it.category)) &&
                    it.startCodePoint in 0 until it.endCodePoint &&
                    it.endCodePoint <= transcript.codePointLength
            }.sortedWith(
                compareBy(
                    WindowSponsorSpan::startCodePoint,
                    WindowSponsorSpan::endCodePoint,
                    WindowSponsorSpan::category,
                ),
            )
    if (selected.isEmpty()) return emptyList()
    val clusters =
        selected.map { it.category }.distinct().flatMap { category ->
            val categoryClusters = mutableListOf<MutableList<WindowSponsorSpan>>()
            selected.filter { it.category == category }.forEach { span ->
                val lastCluster = categoryClusters.lastOrNull()
                if (
                    lastCluster != null && span.startCodePoint <= lastCluster.maxOf { it.endCodePoint } &&
                    lastCluster.none { it.windowIndex == span.windowIndex }
                ) {
                    lastCluster += span
                } else {
                    categoryClusters += mutableListOf(span)
                }
            }
            categoryClusters
        }
    val fused =
        clusters.mapNotNull { cluster ->
            val start = cluster.minOf { it.startCodePoint }
            val end = cluster.maxOf { it.endCodePoint }
            if (end - start < SPONSOR_MIN_SPAN_CHARS) return@mapNotNull null
            val times = transcript.timestampsForSpan(start, end)
            CharacterSponsorSpan(
                start,
                end,
                times.first,
                times.second,
                cluster.maxOf { it.confidence },
                cluster.first().category,
            )
        }
    return fused
        .groupBy { it.category }
        .flatMap { (category, group) -> mergeContinuousSpans(group, SPONSOR_CONTINUITY_GAP_MS[category] ?: 0L) }
        .map { SponsorDetectionSpan(it.startMs, it.endMs, it.confidence, it.category) }
        .sortedWith(
            compareBy(
                SponsorDetectionSpan::startMs,
                SponsorDetectionSpan::endMs,
                SponsorDetectionSpan::category,
            ),
        )
}

/**
 * Reunite same-category spans that a mid-event confidence dropout split in two.
 *
 * Two spans of one category are joined when the time gap between them is within
 * [gapMs] and at least one is a substantive event ([SPONSOR_CONTINUITY_MIN_ANCHOR_CHARS]
 * characters wide), so two short noise fragments are never chained together. A
 * [gapMs] of zero (for example the advisory interaction head) returns the spans
 * unchanged.
 */
private fun mergeContinuousSpans(
    spans: List<CharacterSponsorSpan>,
    gapMs: Long,
): List<CharacterSponsorSpan> {
    if (gapMs <= 0L) return spans
    val merged = mutableListOf<CharacterSponsorSpan>()
    for (span in spans.sortedWith(compareBy(CharacterSponsorSpan::startCodePoint, CharacterSponsorSpan::endCodePoint))) {
        val last = merged.lastOrNull()
        val anchor =
            last != null &&
                maxOf(last.endCodePoint - last.startCodePoint, span.endCodePoint - span.startCodePoint) >=
                SPONSOR_CONTINUITY_MIN_ANCHOR_CHARS
        if (last != null && span.startMs - last.endMs <= gapMs && anchor) {
            merged[merged.lastIndex] =
                last.copy(
                    endCodePoint = maxOf(last.endCodePoint, span.endCodePoint),
                    endMs = maxOf(last.endMs, span.endMs),
                    confidence = maxOf(last.confidence, span.confidence),
                )
        } else {
            merged += span
        }
    }
    return merged
}

private data class CharacterSponsorSpan(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val startMs: Long,
    val endMs: Long,
    val confidence: Double,
    val category: String,
)

private val URL_PATTERN = Regex("(?i)\\b(?:https?://|www\\.)\\S+|\\b\\S+\\.(?:com|net|org)\\S*")
private val NUMBER_PATTERN = Regex("\\b\\d+(?:[.,:]\\d+)*\\b")
private val WHITESPACE_PATTERN = Regex("\\s+")

private val PLACEHOLDER_PATTERN = Regex("(?i)\\b(?:url_token|number_token)\\b")
