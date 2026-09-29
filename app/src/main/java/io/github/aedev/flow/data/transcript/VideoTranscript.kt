package io.github.aedev.flow.data.transcript

import io.github.aedev.flow.player.renderer.subtitle.parseSrv3Document
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One line of a transcript: what was said, when, and for how long. */
data class TranscriptCue(
    val startMs: Long,
    val text: String,
    val durationMs: Long = 0L,
) {
    val endMs: Long get() = startMs + durationMs
}

/** Lines shorter than this are joined with the next one, so a transcript reads in sentences. */
private const val TARGET_SEGMENT_MS = 7_000L
private const val TARGET_SEGMENT_CHARS = 120

private const val VTT_TIME_SEPARATOR = "-->"
private val VTT_TIMESTAMP = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
private val VTT_TAG = Regex("<[^>]*>")
private val WHITESPACE = Regex("\\s+")

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads a caption track into transcript lines.
 *
 * json3 is what a transcript wants: an auto-generated track repeats every line as its rolling
 * window grows, and json3 is the only form that marks those repeats — each one carries `aAppend`,
 * so they can be dropped instead of guessed at. srv3 and vtt are still read, for a track that
 * answers in neither of the other two.
 */
object VideoTranscript {
    fun parse(body: String): List<TranscriptCue> {
        val trimmed = body.trimStart()
        val cues =
            when {
                trimmed.startsWith("{") -> parseJson3(body)
                trimmed.startsWith("<") -> parseSrv3(body)
                else -> parseVtt(body)
            }
        return groupIntoSegments(cues)
    }

    private fun parseJson3(body: String): List<TranscriptCue> =
        runCatching {
            json
                .parseToJsonElement(body)
                .jsonObject["events"]
                ?.jsonArray
                .orEmpty()
                .mapNotNull { element ->
                    val event = element as? JsonObject ?: return@mapNotNull null
                    // A rolling caption re-sends the line it is extending; only the first copy is new.
                    if (event["aAppend"]?.jsonPrimitive?.intOrNull == 1) return@mapNotNull null
                    val start = event["tStartMs"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    val text =
                        event["segs"]
                            ?.jsonArray
                            .orEmpty()
                            .joinToString(separator = "") { seg ->
                                seg.jsonObject["utf8"]
                                    ?.jsonPrimitive
                                    ?.contentOrNull
                                    .orEmpty()
                            }.normalise() ?: return@mapNotNull null
                    val duration = event["dDurationMs"]?.jsonPrimitive?.longOrNull ?: 0L
                    TranscriptCue(start, text, duration)
                }
        }.getOrDefault(emptyList())

    private fun parseSrv3(body: String): List<TranscriptCue> =
        runCatching {
            parseSrv3Document(body).mapNotNull { paragraph ->
                val text = paragraph.runs.joinToString(separator = "") { it.text }.normalise()
                text?.let { TranscriptCue(paragraph.startMs, it, paragraph.durationMs) }
            }
        }.getOrDefault(emptyList())

    private fun parseVtt(body: String): List<TranscriptCue> {
        val cues = mutableListOf<TranscriptCue>()
        var pendingStart: Long? = null
        var pendingEnd: Long? = null
        val pendingText = StringBuilder()

        fun flush() {
            val start = pendingStart ?: return
            pendingText.toString().normalise()?.let { text ->
                cues += TranscriptCue(start, text, ((pendingEnd ?: start) - start).coerceAtLeast(0L))
            }
            pendingStart = null
            pendingEnd = null
            pendingText.setLength(0)
        }

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.contains(VTT_TIME_SEPARATOR) -> {
                    flush()
                    pendingStart = VTT_TIMESTAMP.find(line.substringBefore(VTT_TIME_SEPARATOR))?.toMillis()
                    pendingEnd = VTT_TIMESTAMP.find(line.substringAfter(VTT_TIME_SEPARATOR))?.toMillis()
                }

                line.isEmpty() -> {
                    flush()
                }

                pendingStart != null -> {
                    if (pendingText.isNotEmpty()) pendingText.append(' ')
                    pendingText.append(line)
                }
            }
        }
        flush()
        return dropRollingRepeats(cues)
    }

    /**
     * Drops the growing repeats a rolling track emits.
     *
     * Without `aAppend` to go on, a repeat is recognised by its text: a cue that merely extends the
     * one before it replaces it, because the longer form is the finished line.
     */
    private fun dropRollingRepeats(cues: List<TranscriptCue>): List<TranscriptCue> {
        val result = mutableListOf<TranscriptCue>()
        cues.forEach { cue ->
            val previous = result.lastOrNull()
            when {
                previous == null -> {
                    result += cue
                }

                cue.text == previous.text -> {
                    Unit
                }

                // The longer form is the finished line, but it began when the shorter one did.
                cue.text.startsWith(previous.text) -> {
                    result[result.lastIndex] =
                        cue.copy(
                            startMs = previous.startMs,
                            durationMs = (cue.endMs - previous.startMs).coerceAtLeast(0L),
                        )
                }

                previous.text.startsWith(cue.text) -> {
                    Unit
                }

                else -> {
                    result += cue
                }
            }
        }
        return result
    }

    /**
     * Joins the short lines a caption track is cut into back into readable ones.
     *
     * Captions are timed to fit two lines on screen, which is far shorter than a transcript reads
     * at; consecutive lines are merged until they cover a sentence's worth of time or characters.
     */
    private fun groupIntoSegments(cues: List<TranscriptCue>): List<TranscriptCue> {
        if (cues.isEmpty()) return emptyList()
        val segments = mutableListOf<TranscriptCue>()
        var start = cues.first().startMs
        var end = cues.first().endMs
        val text = StringBuilder()

        fun flush() {
            text.toString().normalise()?.let { segments += TranscriptCue(start, it, (end - start).coerceAtLeast(0L)) }
            text.setLength(0)
        }

        cues.forEach { cue ->
            if (text.isEmpty()) {
                start = cue.startMs
            } else if (cue.startMs - start >= TARGET_SEGMENT_MS || text.length >= TARGET_SEGMENT_CHARS) {
                flush()
                start = cue.startMs
            }
            if (text.isNotEmpty()) text.append(' ')
            text.append(cue.text)
            end = maxOf(cue.endMs, cue.startMs)
        }
        flush()
        return segments
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
            .replace(WHITESPACE, " ")
            .trim()
            .takeIf { it.isNotBlank() }
}
