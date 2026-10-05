package io.github.aedev.flow.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class NotesUiState(
    /** Null until the notes have been read once. */
    val all: List<Note>? = null,
    val visible: List<Note> = emptyList(),
    val query: String = "",
    val filter: NotesFilter = NotesFilter.All,
    val sort: NotesSort = NotesSort.Recent,
)

@HiltViewModel
internal class NotesViewModel
    @Inject
    constructor(
        private val notesRepository: NotesRepository,
        private val backfill: NoteBackfill,
    ) : ViewModel() {
        private val query = MutableStateFlow("")
        private val filter = MutableStateFlow(NotesFilter.All)
        private val sort = MutableStateFlow(NotesSort.Recent)

        private val notes =
            notesRepository.observeAll().onEach { list -> viewModelScope.launch { backfill.fill(list) } }

        val uiState: StateFlow<NotesUiState> =
            combine(notes, query, filter, sort) { all, query, filter, sort ->
                NotesUiState(all = all, visible = all.visibleNotes(query, filter, sort), query = query, filter = filter, sort = sort)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), NotesUiState())

        fun setQuery(value: String) {
            query.value = value
        }

        fun setFilter(value: NotesFilter) {
            filter.value = value
        }

        fun setSort(value: NotesSort) {
            sort.value = value
        }

        fun save(
            note: Note,
            text: String,
        ) {
            viewModelScope.launch { notesRepository.save(note.kind, note.targetId, text, note.subject) }
        }

        fun delete(note: Note) {
            viewModelScope.launch { notesRepository.delete(note.kind, note.targetId) }
        }

        /** Puts a deleted note back exactly as it was, edit time included. */
        fun restore(note: Note) {
            viewModelScope.launch { notesRepository.restore(listOf(note)) }
        }

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
        }
    }
