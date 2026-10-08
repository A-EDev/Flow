package io.github.aedev.flow.player.subtitle

import androidx.media3.common.text.Cue

/**
 * The line to show for an auto-generated track. Speech-recognised captions arrive as a rolling
 * window of overlapping, growing cues, so only the words the latest cue added are kept.
 */
internal fun List<Cue>.toRollingCaptionText(): String? {
    val textCues =
        mapNotNull { it.text?.toString()?.cleanRollingCueText() }
            .filter { it.isNotBlank() }
            .distinct()

    if (textCues.isEmpty()) return null

    return when (textCues.size) {
        1 -> textCues.first()
        else -> textCues.last()
    }.takeIf { it.isNotBlank() }
}

private fun String.cleanRollingCueText(): String {
    val lines =
        replace('\u00A0', ' ')
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

    if (lines.isEmpty()) return ""

    return lines.latestRollingCaptionLine()
}

private fun List<String>.latestRollingCaptionLine(): String {
    if (size == 1) return first()

    val latestLine = last()
    val previousLine = this[size - 2]
    val previousWords = previousLine.splitWords()
    val latestWords = latestLine.splitWords()

    if (previousWords.isEmpty() || latestWords.isEmpty()) return latestLine

    val overlap = longestSuffixPrefixOverlap(previousWords, latestWords)
    val freshWords = latestWords.drop(overlap)

    return freshWords
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")
        ?: latestLine
}

private fun String.splitWords(): List<String> = split(Regex("\\s+")).filter { it.isNotBlank() }

private fun longestSuffixPrefixOverlap(
    previous: List<String>,
    latest: List<String>,
): Int {
    val maxOverlap = minOf(previous.size, latest.size)
    for (count in maxOverlap downTo 1) {
        val previousSuffix = previous.takeLast(count)
        val latestPrefix = latest.take(count)
        if (previousSuffix.equalsWords(latestPrefix)) {
            return count
        }
    }
    return 0
}

private fun List<String>.equalsWords(other: List<String>): Boolean {
    if (size != other.size) return false
    return indices.all { index ->
        this[index].trimPunctuation().equals(other[index].trimPunctuation(), ignoreCase = true)
    }
}

private fun String.trimPunctuation(): String = trim { !it.isLetterOrDigit() }
