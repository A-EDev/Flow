package io.github.aedev.flow.ui.screens.music.collection

import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SuggestionsState(
    val tracks: List<MusicTrack> = emptyList(),
    val isLoading: Boolean = false,
    val requested: Boolean = false,
)

/**
 * Songs to add under a playlist, from what YouTube Music relates to a few of its tracks. Fetched
 * once, when the end of the list comes into view; Refresh moves on to other tracks of the playlist.
 */
internal class MusicCollectionSuggestions(
    private val scope: CoroutineScope,
    private val fetchRelated: suspend (videoId: String) -> List<MusicTrack> = { id ->
        YouTubeMusicService.getRelatedMusic(id, PER_SEED, audioOnly = true)
    },
) {
    private val _state = MutableStateFlow(SuggestionsState())
    val state: StateFlow<SuggestionsState> = _state.asStateFlow()

    private var round = 0
    private var job: Job? = null

    fun requestOnce(playlistIds: List<String>) {
        if (_state.value.requested) return
        load(playlistIds)
    }

    fun refresh(playlistIds: List<String>) {
        round++
        load(playlistIds)
    }

    /** Takes a song off the list once it has been added to the playlist. */
    fun drop(videoId: String) {
        _state.update { state -> state.copy(tracks = state.tracks.filterNot { it.videoId == videoId }) }
    }

    private fun load(playlistIds: List<String>) {
        val seeds = suggestionSeeds(playlistIds, round)
        if (seeds.isEmpty()) {
            _state.value = SuggestionsState(requested = true)
            return
        }
        job?.cancel()
        _state.update { it.copy(isLoading = true, requested = true) }
        job =
            scope.launch(PerformanceDispatcher.networkIO) {
                val related = seeds.map { seed -> async { runCatching { fetchRelated(seed) }.getOrDefault(emptyList()) } }.awaitAll()
                _state.value = SuggestionsState(tracks = mergeSuggestions(related, playlistIds.toSet()), requested = true)
            }
    }

    companion object {
        const val SEEDS_PER_ROUND = 3
        const val PER_SEED = 10
        const val SHOWN = 10
    }
}

/** Tracks spread over the playlist, starting from its end, where the newest additions sit. */
internal fun suggestionSeeds(
    playlistIds: List<String>,
    round: Int,
): List<String> {
    val usable = playlistIds.filterNot(LocalMediaIds::isLocal).distinct().asReversed()
    if (usable.isEmpty()) return emptyList()
    return List(minOf(MusicCollectionSuggestions.SEEDS_PER_ROUND, usable.size)) { offset ->
        usable[(round * MusicCollectionSuggestions.SEEDS_PER_ROUND + offset) % usable.size]
    }.distinct()
}

/** Takes from each seed in turn so one artist's neighbourhood cannot fill the list. */
internal fun mergeSuggestions(
    related: List<List<MusicTrack>>,
    exclude: Set<String>,
): List<MusicTrack> {
    val longest = related.maxOfOrNull { it.size } ?: 0
    return (0 until longest)
        .flatMap { index -> related.mapNotNull { it.getOrNull(index) } }
        .filterNot { it.videoId in exclude }
        .distinctBy { it.videoId }
        .take(MusicCollectionSuggestions.SHOWN)
}
