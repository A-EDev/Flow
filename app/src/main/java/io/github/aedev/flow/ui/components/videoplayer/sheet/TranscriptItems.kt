package io.github.aedev.flow.ui.components.videoplayer.sheet

import io.github.aedev.flow.data.transcript.TranscriptCue

/** A chapter as the transcript reads it: where it starts, and what it is called. */
data class TranscriptChapter(
    val startMs: Long,
    val title: String,
)

/** One row of the transcript: a line that was spoken, or the heading of the chapter it falls in. */
internal sealed interface TranscriptItem {
    val key: String

    data class Heading(
        val chapter: TranscriptChapter,
    ) : TranscriptItem {
        override val key: String get() = "h${chapter.startMs}"
    }

    data class Line(
        val cue: TranscriptCue,
    ) : TranscriptItem {
        override val key: String get() = "l${cue.startMs}"
    }
}

/**
 * Heads each run of lines with the chapter it belongs to.
 *
 * A heading is emitted the first time a line of that chapter appears, so a chapter nothing was said
 * in — or one a search has filtered every line out of — never leaves a heading standing over
 * somebody else's lines.
 */
internal fun transcriptItems(
    cues: List<TranscriptCue>,
    chapters: List<TranscriptChapter>,
): List<TranscriptItem> {
    val ordered = chapters.filter { it.title.isNotBlank() }.sortedBy { it.startMs }
    if (ordered.isEmpty()) return cues.map { TranscriptItem.Line(it) }

    val items = ArrayList<TranscriptItem>(cues.size + ordered.size)
    var cursor = -1
    var heading: TranscriptChapter? = null
    cues.forEach { cue ->
        while (cursor + 1 < ordered.size && ordered[cursor + 1].startMs <= cue.startMs) cursor++
        val chapter = ordered.getOrNull(cursor)
        if (chapter != null && chapter != heading) {
            items += TranscriptItem.Heading(chapter)
            heading = chapter
        }
        items += TranscriptItem.Line(cue)
    }
    return items
}
