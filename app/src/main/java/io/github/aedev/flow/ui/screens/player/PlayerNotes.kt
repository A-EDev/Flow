package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NotesRepository
import io.github.aedev.flow.data.notes.toNoteSubject
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The note attached to whatever video is playing.
 *
 * Held beside [VideoPlayerViewModel] rather than inside it — the view model is already at its size
 * ceiling, and a note has nothing to do with playback.
 */
internal class PlayerNotes(
    private val notesRepository: NotesRepository,
    playerPreferences: PlayerPreferences,
    private val scope: CoroutineScope,
) {
    val enabled: StateFlow<Boolean> =
        playerPreferences.effectiveVideoNotesEnabled
            .stateIn(scope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), false)

    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()

    private var job: Job? = null

    fun observe(videoId: String) {
        job?.cancel()
        _note.value = null
        if (videoId.isBlank()) return
        job =
            scope.launch(PerformanceDispatcher.diskIO) {
                notesRepository.observe(NoteKind.Video, videoId).collect { _note.value = it?.text }
            }
    }

    /** [video] is saved with the note when it is the one the note is about. */
    fun save(
        videoId: String,
        text: String,
        video: Video?,
    ) {
        if (videoId.isBlank()) return
        val subject = video?.takeIf { it.id == videoId && it.title.isNotBlank() }?.toNoteSubject()
        scope.launch(PerformanceDispatcher.diskIO) {
            notesRepository.save(NoteKind.Video, videoId, text, subject)
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
