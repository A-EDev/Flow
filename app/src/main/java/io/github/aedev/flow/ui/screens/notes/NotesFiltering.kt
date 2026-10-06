package io.github.aedev.flow.ui.screens.notes

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.utils.filterBySearch
import io.github.aedev.flow.utils.foldForSearch
import io.github.aedev.flow.utils.youtubeWatchUrl

internal enum class NotesFilter { All, Videos, Channels }

internal enum class NotesSort {
    Recent,
    Oldest,
    Title,

    /** The order the viewer dragged the notes into; notes never placed follow, newest edit first. */
    Custom,
    ;

    companion object {
        fun fromName(name: String?): NotesSort = entries.firstOrNull { it.name == name } ?: Recent
    }
}

/** The notes the page shows: the chosen kind, matching every word of [query], in [sort] order. */
internal fun List<Note>.visibleNotes(
    query: String,
    filter: NotesFilter,
    sort: NotesSort,
): List<Note> {
    val kind =
        when (filter) {
            NotesFilter.All -> this
            NotesFilter.Videos -> filter { it.kind == NoteKind.Video }
            NotesFilter.Channels -> filter { it.kind == NoteKind.Channel }
        }
    val matching =
        kind.filterBySearch(
            query,
        ) { note -> listOfNotNull(note.text, note.subject?.title, note.subject?.channelName).joinToString(" ") }
    return when (sort) {
        NotesSort.Recent -> matching.sortedByDescending { it.updatedAt }
        NotesSort.Oldest -> matching.sortedBy { it.updatedAt }
        NotesSort.Title -> matching.sortedBy { (it.subject?.title ?: it.text).foldForSearch() }
        NotesSort.Custom -> matching.sortedWith(compareBy<Note, Int?>(nullsLast()) { it.position }.thenByDescending { it.updatedAt })
    }
}

/** [notes] as plain text to send: each one's title and link, then the note itself. */
internal fun shareText(notes: List<Note>): String =
    notes.joinToString("\n\n") { note ->
        val link =
            when (note.kind) {
                NoteKind.Video -> youtubeWatchUrl(note.targetId)
                NoteKind.Channel -> "https://www.youtube.com/channel/${note.targetId}"
            }
        listOfNotNull(note.subject?.title, link, note.text).joinToString("\n")
    }

/** A stable key for one note, for list keys and the selected note. */
internal val Note.key: String get() = kind.idFor(targetId)

/** The video a note is about, as much of it as the note saved, for the player to open. */
internal fun Note.toVideo(): Video =
    Video(
        id = targetId,
        title = subject?.title.orEmpty(),
        channelName = subject?.channelName.orEmpty(),
        channelId = subject?.channelId.orEmpty(),
        thumbnailUrl = subject?.thumbnailUrl.orEmpty(),
        duration = subject?.durationSeconds ?: 0,
        viewCount = 0,
        uploadDate = "",
    )
