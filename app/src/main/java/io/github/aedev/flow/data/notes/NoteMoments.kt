package io.github.aedev.flow.data.notes

import io.github.aedev.flow.utils.formatDurationMillis
import io.github.aedev.flow.utils.parseTimestampMs

/** A time written in a note: where it sits in the text, where it points in the video, and the words after it. */
data class NoteMoment(
    val range: IntRange,
    val positionMs: Long,
    val label: String,
)

/** Reads the times a note mentions, the way descriptions and comments do, and writes new ones in. */
object NoteMoments {
    private val timestamp = Regex("""(?<![\d:.])(?:\d{1,2}:)?\d{1,2}:\d{2}(?!\d|:\d)""")
    private val labelTrim = charArrayOf(' ', '\t', '-', '–', '—', ':', '|', '·', '•')

    /**
     * Every time in [text], in writing order. With [durationMs] known, a time past the end of the
     * video is not one of its moments; minutes or seconds of 60 and more never are.
     */
    fun find(
        text: String,
        durationMs: Long = 0L,
    ): List<NoteMoment> =
        timestamp
            .findAll(text)
            .mapNotNull { match ->
                val fields = match.value.split(':').map { it.toInt() }
                if (fields.drop(1).any { it >= 60 }) return@mapNotNull null
                val positionMs = parseTimestampMs(match.value) ?: return@mapNotNull null
                if (durationMs > 0 && positionMs > durationMs) return@mapNotNull null
                NoteMoment(match.range, positionMs, labelAfter(text, match.range))
            }.toList()

    /** One entry per time, earliest first, for a list of where to jump. */
    fun timeline(
        text: String,
        durationMs: Long = 0L,
    ): List<NoteMoment> = find(text, durationMs).distinctBy { it.positionMs }.sortedBy { it.positionMs }

    /**
     * Writes [positionMs] at [cursor], on a line of its own unless the cursor already starts one, and
     * returns the new text with the cursor after the time, ready for its description.
     */
    fun insert(
        text: String,
        cursor: Int,
        positionMs: Long,
    ): Pair<String, Int> {
        val at = cursor.coerceIn(0, text.length)
        val startsLine = at == 0 || text[at - 1] == '\n'
        val stamp = (if (startsLine) "" else "\n") + formatDurationMillis(positionMs) + " "
        return (text.substring(0, at) + stamp + text.substring(at)) to at + stamp.length
    }

    private fun labelAfter(
        text: String,
        range: IntRange,
    ): String {
        val lineEnd = text.indexOf('\n', range.last + 1).let { if (it < 0) text.length else it }
        val rest = text.substring(range.last + 1, lineEnd).trim(*labelTrim)
        return if (timestamp.containsMatchIn(rest)) rest.substringBefore(timestamp.find(rest)!!.value).trim(*labelTrim) else rest
    }
}
