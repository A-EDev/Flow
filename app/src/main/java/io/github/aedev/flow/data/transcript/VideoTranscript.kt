package io.github.aedev.flow.data.transcript

import io.github.aedev.flow.player.renderer.subtitle.parseSrv3Document

/** One line of a transcript: what was said, and when. */
data class TranscriptCue(
    val startMs: Long,
    val text: String,
)

private const val VTT_TIME_SEPARATOR = "-->"
private val VTT_TIMESTAMP = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
private val VTT_TAG = Regex("<[^>]*>")

/**
 * Reads a caption track into transcript lines.
 *
 * The player already resolves every track to a timed-text URL, and asks for srv3 on authored
 * tracks and vtt on the auto-generated ones. Both shapes are read here rather than re-requesting
 * the track in a third format, which is what made captions render nothing the last time a `fmt`
 * was chosen at the wrong layer.
 */
object VideoTranscript {
    fun parse(body: String): List<TranscriptCue> =
        if (body.trimStart().startsWith("<")) parseSrv3(body) else parseVtt(body)

    private fun parseSrv3(body: String): List<TranscriptCue> =
        runCatching {
            parseSrv3Document(body).mapNotNull { paragraph ->
                val text = paragraph.runs.joinToString(separator = "") { it.text }.normalise()
                text?.let { TranscriptCue(startMs = paragraph.startMs, text = it) }
            }
        }.getOrDefault(emptyList())

    private fun parseVtt(body: String): List<TranscriptCue> {
        val cues = mutableListOf<TranscriptCue>()
        var pendingStart: Long? = null
        val pendingText = StringBuilder()

        fun flush() {
            val start = pendingStart ?: return
            pendingText.toString().normalise()?.let { cues += TranscriptCue(start, it) }
            pendingStart = null
            pendingText.setLength(0)
        }

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.contains(VTT_TIME_SEPARATOR) -> {
                    flush()
                    pendingStart = VTT_TIMESTAMP.find(line.substringBefore(VTT_TIME_SEPARATOR))?.toMillis()
                }

                line.isEmpty() -> flush()

                pendingStart != null -> {
                    if (pendingText.isNotEmpty()) pendingText.append(' ')
                    pendingText.append(line)
                }
            }
        }
        flush()
        // An auto-generated track repeats each line as it rolls up, so the same words arrive two or
        // three times over consecutive cues; a transcript wants each line once.
        return cues.distinctBy { it.text }
    }

    private fun MatchResult.toMillis(): Long {
        val hours = groupValues[1].toLongOrNull() ?: 0L
        val minutes = groupValues[2].toLongOrNull() ?: 0L
        val seconds = groupValues[3].toLongOrNull() ?: 0L
        val fraction = groupValues[4].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 3_600L + minutes * 60L + seconds) * 1_000L) + fraction
    }

    private fun String.normalise(): String? =
        VTT_TAG
            .replace(this, "")
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
}
