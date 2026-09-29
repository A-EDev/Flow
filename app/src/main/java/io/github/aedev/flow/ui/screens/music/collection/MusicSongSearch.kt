package io.github.aedev.flow.ui.screens.music.collection

import android.util.Log
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 350L
private const val RESULT_LIMIT = 30

/** What the add-songs search shows: the query, whether it is running, and what it found. */
data class SongSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<MusicTrack> = emptyList(),
)

/**
 * The catalogue search behind "Add songs". Each keystroke replaces the last search after a short
 * pause, so a slow early query can never overwrite the results of a later one.
 */
class MusicSongSearch(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SongSearchState())
    val state: StateFlow<SongSearchState> = _state.asStateFlow()
    private var job: Job? = null

    fun search(query: String) {
        job?.cancel()
        _state.value =
            SongSearchState(
                query = query,
                isSearching = query.isNotBlank(),
                results = if (query.isBlank()) emptyList() else _state.value.results,
            )
        if (query.isBlank()) return
        job =
            scope.launch(PerformanceDispatcher.networkIO) {
                delay(TYPING_PAUSE_MS)
                val results =
                    runCatching { YouTubeMusicService.searchMusic(query, limit = RESULT_LIMIT) }
                        .onFailure { Log.w("MusicSongSearch", "Search failed", it) }
                        .getOrDefault(emptyList())
                _state.value = SongSearchState(query = query, isSearching = false, results = results)
            }
    }

    fun clear() = search("")
}
