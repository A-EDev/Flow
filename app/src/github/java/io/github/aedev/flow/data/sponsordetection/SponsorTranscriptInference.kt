package io.github.aedev.flow.data.sponsordetection

import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
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
internal const val SPONSOR_WINDOW_MAX_LENGTH = 768
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
    return Normalizer.normalize(urlsReplaced.replace(NUMBER_PATTERN, "NUMBER_TOKEN"), Normalizer.Form.NFC)
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

internal fun decodeSponsorBilou(
    logits: List<FloatArray>,
    offsets: List<IntRange>,
): List<WindowDecodedSpan> {
    require(logits.size == offsets.size)
    val tokenIndexes = offsets.indices.filter { !offsets[it].isEmpty() }
    if (tokenIndexes.isEmpty()) return emptyList()
    val probabilities = Array(tokenIndexes.size) { index -> logSoftmax(logits[tokenIndexes[index]]) }
    val paths = Array(probabilities.size) { IntArray(LABEL_COUNT) }
    var scores =
        DoubleArray(LABEL_COUNT) { label ->
            if (label in START_LABELS) probabilities[0][label] else Double.NEGATIVE_INFINITY
        }
    for (position in 1 until probabilities.size) {
        val next = DoubleArray(LABEL_COUNT)
        for (label in 0 until LABEL_COUNT) {
            val previous = bestPrevious(scores, PREVIOUS_LABELS[label])
            paths[position][label] = previous
            next[label] = scores[previous] + probabilities[position][label]
        }
        scores = next
    }
    val path = IntArray(probabilities.size)
    path[path.lastIndex] = END_LABELS.maxBy { scores[it] }
    for (position in path.lastIndex downTo 1) path[position - 1] = paths[position][path[position]]

    return buildList {
        var position = 0
        while (position < path.size) {
            val label = path[position]
            if (label == LABEL_O) {
                position++
                continue
            }
            var endPosition = position
            if (label == LABEL_B) {
                endPosition++
                while (path[endPosition] == LABEL_I) endPosition++
            } else {
                check(label == LABEL_U)
            }
            var logConfidence = 0.0
            for (tokenPosition in position..endPosition) {
                logConfidence += probabilities[tokenPosition][path[tokenPosition]]
            }
            val confidence = exp(logConfidence / (endPosition - position + 1))
            val startToken = tokenIndexes[position]
            val endToken = tokenIndexes[endPosition]
            add(
                WindowDecodedSpan(
                    startCodePoint = offsets[startToken].first,
                    endCodePoint = offsets[endToken].last + 1,
                    confidence = confidence,
                ),
            )
            position = endPosition + 1
        }
    }
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
    confidenceThreshold: Double = 0.0,
    mergeGapCharacters: Int = 24,
    mergeGapMs: Long = 1_500,
): List<SponsorDetectionSpan> {
    val selected =
        spans
            .filter {
                it.confidence >= confidenceThreshold &&
                    it.startCodePoint in 0 until it.endCodePoint &&
                    it.endCodePoint <= transcript.codePointLength
            }.sortedWith(compareBy(WindowSponsorSpan::startCodePoint, WindowSponsorSpan::endCodePoint))
    if (selected.isEmpty()) return emptyList()
    val clusters = mutableListOf<MutableList<WindowSponsorSpan>>()
    for (span in selected) {
        if (clusters.isNotEmpty() && span.startCodePoint <= clusters.last().maxOf { it.endCodePoint }) {
            clusters.last() += span
        } else {
            clusters += mutableListOf(span)
        }
    }
    val fused =
        clusters.map { cluster ->
            val start = cluster.minOf { it.startCodePoint }
            val end = cluster.maxOf { it.endCodePoint }
            val times = transcript.timestampsForSpan(start, end)
            CharacterSponsorSpan(start, end, times.first, times.second, cluster.maxOf { it.confidence })
        }
    val merged = mutableListOf<CharacterSponsorSpan>()
    for (span in fused) {
        val previous = merged.lastOrNull()
        if (
            previous != null &&
            span.startCodePoint - previous.endCodePoint <= mergeGapCharacters &&
            span.startMs - previous.endMs <= mergeGapMs
        ) {
            val times = transcript.timestampsForSpan(previous.startCodePoint, span.endCodePoint)
            merged[merged.lastIndex] =
                CharacterSponsorSpan(
                    previous.startCodePoint,
                    span.endCodePoint,
                    times.first,
                    times.second,
                    maxOf(previous.confidence, span.confidence),
                )
        } else {
            merged += span
        }
    }
    return merged.map { SponsorDetectionSpan(it.startMs, it.endMs, it.confidence) }
}

private data class CharacterSponsorSpan(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val startMs: Long,
    val endMs: Long,
    val confidence: Double,
)

private fun logSoftmax(values: FloatArray): DoubleArray {
    require(values.size == LABEL_COUNT)
    var maximum = values[0].toDouble()
    for (index in 1 until values.size) maximum = maxOf(maximum, values[index].toDouble())
    var exponentialSum = 0.0
    for (value in values) exponentialSum += exp(value - maximum)
    val denominator = maximum + ln(exponentialSum)
    return DoubleArray(values.size) { index -> values[index] - denominator }
}

private fun bestPrevious(
    scores: DoubleArray,
    candidates: IntArray,
): Int {
    var best = candidates[0]
    for (index in 1 until candidates.size) {
        val candidate = candidates[index]
        if (scores[candidate] > scores[best]) best = candidate
    }
    return best
}

private const val LABEL_COUNT = 5
private const val LABEL_O = 0
private const val LABEL_B = 1
private const val LABEL_I = 2
private const val LABEL_L = 3
private const val LABEL_U = 4
private val START_LABELS = intArrayOf(LABEL_O, LABEL_B, LABEL_U)
private val END_LABELS = intArrayOf(LABEL_O, LABEL_L, LABEL_U)
private val PREVIOUS_LABELS =
    arrayOf(
        intArrayOf(LABEL_O, LABEL_L, LABEL_U),
        intArrayOf(LABEL_O, LABEL_L, LABEL_U),
        intArrayOf(LABEL_B, LABEL_I),
        intArrayOf(LABEL_B, LABEL_I),
        intArrayOf(LABEL_O, LABEL_L, LABEL_U),
    )

private val URL_PATTERN = Regex("(?i)\\b(?:https?://|www\\.)\\S+|\\b\\S+\\.(?:com|net|org)\\S*")
private val NUMBER_PATTERN = Regex("\\b\\d+(?:[.,:]\\d+)*\\b")
private val WHITESPACE_PATTERN = Regex("\\s+")
