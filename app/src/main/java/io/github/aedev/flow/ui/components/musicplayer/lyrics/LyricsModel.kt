package io.github.aedev.flow.ui.components.musicplayer.lyrics

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.WordTimestamp
import kotlinx.coroutines.flow.first
import java.text.BreakIterator

internal sealed class LyricsListItem {
    data class Line(
        val index: Int,
        val entry: LyricsEntry,
    ) : LyricsListItem()

    data class Indicator(
        val afterLineIndex: Int,
        val gapStartMs: Long,
        val gapEndMs: Long,
    ) : LyricsListItem()
}

internal data class HyphenGroupWord(
    val pos: Int,
    val size: Int,
    val isLast: Boolean,
    val groupStartMs: Long,
    val groupEndMs: Long,
)

internal fun adaptiveLyricsTextSize(
    baseSize: Float,
    textLength: Int,
    isBackground: Boolean,
): Float {
    val foregroundSize =
        when {
            textLength > 92 -> baseSize * 0.66f
            textLength > 72 -> baseSize * 0.72f
            textLength > 54 -> baseSize * 0.8f
            textLength > 42 -> baseSize * 0.9f
            else -> baseSize
        }
    return if (isBackground) foregroundSize * 0.7f else foregroundSize
}

internal fun buildLines(
    lyrics: String?,
    syncedLyrics: List<LyricsEntry>,
): List<LyricsEntry> {
    if (syncedLyrics.isNotEmpty() && entriesLookSynced(syncedLyrics)) {
        return listOf(LyricsEntry(time = 0L, text = "")) + syncedLyrics.sorted()
    }

    val plainSource =
        lyrics?.takeIf { it.isNotBlank() }
            ?: syncedLyrics.joinToString("\n") { it.text }.takeIf { it.isNotBlank() }
    val plainLines =
        plainSource
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    if (plainLines.isEmpty()) return emptyList()
    return plainLines.mapIndexed { index, line ->
        LyricsEntry(time = 1_000_000L + index, text = line)
    }
}

internal fun entriesLookSynced(entries: List<LyricsEntry>): Boolean {
    if (entries.size < 2) return false
    val main = entries.filter { !it.isBackground }
    val list = if (main.size >= 2) main else entries
    val distinctTimes = list.map { it.time }.distinct()
    if (distinctTimes.size < 2) return false
    val firstPositive = distinctTimes.firstOrNull { it > 0L } ?: return false
    val maxTime = list.maxOf { it.time }
    if (maxTime - firstPositive < 5_000L) return false
    if (entries.any { !it.words.isNullOrEmpty() }) return true
    val distinctTimedLines = list.count { it.time > 0L }
    return distinctTimedLines >= (list.size * 0.5).toInt().coerceAtLeast(2)
}

internal fun buildMergedLyricsList(lines: List<LyricsEntry>): List<LyricsListItem> {
    val result = mutableListOf<LyricsListItem>()
    lines.forEachIndexed { index, entry ->
        if (entry.text.isNotBlank()) {
            result.add(LyricsListItem.Line(index, entry))
        }
        if (index < lines.lastIndex) {
            val nextStart = lines[index + 1].time
            val currentEnd =
                when {
                    !entry.words.isNullOrEmpty() -> entry.words.last().endTime
                    entry.text.isBlank() -> entry.time
                    else -> null
                }
            if (currentEnd != null && currentEnd < nextStart && nextStart - currentEnd > 4000L) {
                result.add(LyricsListItem.Indicator(index, currentEnd, nextStart))
            }
        }
    }
    return result
}

internal fun findActiveLineIndices(
    lines: List<LyricsEntry>,
    position: Long,
): Set<Int> {
    val active = mutableSetOf<Int>()
    val hasWordTimings = lines.any { !it.words.isNullOrEmpty() }

    val distinctMainTimes =
        lines
            .asSequence()
            .filter { !it.isBackground }
            .map { it.time }
            .distinct()
            .take(3)
            .toList()
    if (distinctMainTimes.size < 2) return active

    for (index in lines.indices) {
        val line = lines[index]
        if (line.time > position) break
        val lineEndMs =
            if (!line.words.isNullOrEmpty()) {
                line.words.last().endTime
            } else {
                (index + 1 until lines.size)
                    .asSequence()
                    .map { lines[it].time }
                    .firstOrNull { it > line.time }
                    ?: Long.MAX_VALUE
            }
        if (position <= lineEndMs) active.add(index)
    }

    if (!hasWordTimings && active.size > 1) {
        val mainActive = active.filter { !lines[it].isBackground }
        if (mainActive.size > 1) {
            val maxTime = mainActive.maxOf { lines[it].time }
            active.removeAll { it in mainActive && lines[it].time < maxTime }
        }
    }

    return active
}

internal fun sanitizeWordTimestamps(words: List<WordTimestamp>): List<WordTimestamp> {
    if (words.isEmpty()) return emptyList()
    return words.mapIndexed { index, word ->
        val nextWord = words.getOrNull(index + 1)
        val start = word.startTime.coerceAtLeast(0L)
        val endFromNext = nextWord?.startTime?.takeIf { it > start }
        val end =
            when {
                endFromNext != null && word.endTime > endFromNext -> endFromNext
                word.endTime <= start -> start + 80L
                else -> word.endTime
            }
        word.copy(startTime = start, endTime = end)
    }
}

internal fun String.containsRtl(): Boolean {
    for (char in this) {
        val directionality = Character.getDirectionality(char).toInt()
        if (
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt() ||
            directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt()
        ) {
            return true
        }
    }
    return false
}

internal fun String.toGraphemeClusters(): List<String> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        result.add(substring(start, end))
        start = end
        end = iterator.next()
    }
    return result
}

/**
 * The last word of a line, when it holds hyphens ("la-la-la"), is sung as separate syllables:
 * split it into timed segments and keep, for each, the index of the word it came from.
 */
internal fun splitTrailingHyphenWord(sanitizedInputWords: List<WordTimestamp>): Pair<List<WordTimestamp>, List<Int>> =
    sanitizedInputWords
        .flatMapIndexed { originalIdx, word ->
            val shouldSplit = word.text.contains('-') && word.text.length > 1 && originalIdx == sanitizedInputWords.lastIndex
            if (shouldSplit) {
                val segments = mutableListOf<String>()
                var start = 0
                for (i in word.text.indices) {
                    if (word.text[i] == '-') {
                        segments.add(word.text.substring(start, i + 1))
                        start = i + 1
                    }
                }
                if (start < word.text.length) segments.add(word.text.substring(start))
                if (segments.size > 1) {
                    val totalDuration = word.endTime - word.startTime
                    val segmentDuration = totalDuration / segments.size
                    segments.mapIndexed { index, segmentText ->
                        WordTimestamp(
                            text = segmentText,
                            startTime = word.startTime + index * segmentDuration,
                            endTime = word.startTime + (index + 1) * segmentDuration,
                        ) to originalIdx
                    }
                } else {
                    listOf(word to originalIdx)
                }
            } else {
                listOf(word to originalIdx)
            }
        }.let { data -> data.map { it.first } to data.map { it.second } }

/** The char offset where each grapheme cluster starts. */
internal fun clusterStartOffsets(graphemeClusters: List<String>): IntArray {
    val clusterCount = graphemeClusters.size
    return IntArray(clusterCount).also { offsets ->
        var charOffset = 0
        graphemeClusters.forEachIndexed { i, cluster ->
            offsets[i] = charOffset
            charOffset += cluster.length
        }
    }
}

/**
 * For every grapheme cluster: which word it belongs to, its position inside that word, and the
 * word's length in clusters. A space after a word counts as the word's last cluster.
 */
internal fun mapClustersToWords(
    mainText: String,
    effectiveWords: List<WordTimestamp>,
    isBackground: Boolean,
    clusterCharOffsets: IntArray,
): Triple<IntArray, IntArray, IntArray> {
    val clusterCount = clusterCharOffsets.size
    val wordIdxMap = IntArray(clusterCount) { -1 }
    val charInWordMap = IntArray(clusterCount)
    val wordLenMap = IntArray(clusterCount) { 1 }
    var currentPos = 0
    var clusterCursor = 0
    effectiveWords.forEachIndexed { wordIdx, word ->
        val rawWordText =
            if (isBackground) {
                var text = word.text
                if (wordIdx == 0) text = text.removePrefix("(")
                if (wordIdx == effectiveWords.size - 1) text = text.removeSuffix(")")
                text
            } else {
                word.text
            }
        val indexInMain = mainText.indexOf(rawWordText, currentPos)
        if (indexInMain != -1) {
            val wordEndInMain = indexInMain + rawWordText.length
            while (clusterCursor < clusterCount && clusterCharOffsets[clusterCursor] < indexInMain) clusterCursor++
            val wordClusterIndices = mutableListOf<Int>()
            while (clusterCursor < clusterCount && clusterCharOffsets[clusterCursor] < wordEndInMain) {
                wordClusterIndices.add(clusterCursor)
                clusterCursor++
            }
            val wordClusterLen = wordClusterIndices.size
            wordClusterIndices.forEachIndexed { posInWord, clusterIndex ->
                wordIdxMap[clusterIndex] = wordIdx
                charInWordMap[clusterIndex] = posInWord
                wordLenMap[clusterIndex] = wordClusterLen
            }
            if (
                clusterCursor < clusterCount &&
                clusterCharOffsets[clusterCursor] == wordEndInMain &&
                wordEndInMain < mainText.length &&
                mainText[wordEndInMain] == ' '
            ) {
                wordIdxMap[clusterCursor] = wordIdx
                charInWordMap[clusterCursor] = wordClusterLen
                wordLenMap[clusterCursor] = wordClusterLen + 1
                clusterCursor++
            }
            currentPos = wordEndInMain
        }
    }
    return Triple(wordIdxMap, charInWordMap, wordLenMap)
}

/** Words joined by trailing hyphens form one group that swells together as it is sung. */
internal fun hyphenGroups(effectiveWords: List<WordTimestamp>): Map<Int, HyphenGroupWord> {
    val map = mutableMapOf<Int, HyphenGroupWord>()
    var currentGroup = mutableListOf<Int>()
    effectiveWords.forEachIndexed { wordIdx, word ->
        currentGroup.add(wordIdx)
        if (!word.text.endsWith("-")) {
            if (currentGroup.size > 1) {
                val groupSize = currentGroup.size
                val groupStartMs = effectiveWords[currentGroup.first()].startTime
                val groupEndMs = word.endTime
                currentGroup.forEachIndexed { pos, idx ->
                    map[idx] = HyphenGroupWord(pos, groupSize, pos == groupSize - 1, groupStartMs, groupEndMs)
                }
            }
            currentGroup = mutableListOf()
        }
    }
    return map
}
