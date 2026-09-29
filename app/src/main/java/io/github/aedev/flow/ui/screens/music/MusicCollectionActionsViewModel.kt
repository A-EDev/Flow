package io.github.aedev.flow.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val FETCH_TIMEOUT_MS = 12_000L

/** How an album or playlist starts from its menu. */
enum class CollectionPlayMode { Play, Shuffle, Radio }

/**
 * An album or playlist menu's actions: play it, shuffle it or start a radio from it, and keep it in
 * the library. Tracks are fetched once, when an action needs them, never on open.
 */
@HiltViewModel
class MusicCollectionActionsViewModel
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        private val _isSaved = MutableStateFlow(false)
        val isSaved: StateFlow<Boolean> = _isSaved.asStateFlow()

        fun loadSaved(collectionId: String) {
            _isSaved.value = false
            viewModelScope.launch { _isSaved.value = playlistRepository.isExternalPlaylistSaved(collectionId) }
        }

        /** Saves the collection with its tracks, or takes it out of the library; [announce] confirms either way. */
        fun toggleSaved(
            item: MusicCollectionActionItem,
            announce: (Int) -> Unit,
        ) {
            val save = !_isSaved.value
            viewModelScope.launch {
                runAction(announce) {
                    if (save) {
                        playlistRepository.saveMusicCollection(fetch(item) ?: error("No tracks for ${item.id}"))
                    } else {
                        playlistRepository.unsaveExternalPlaylist(item.id)
                    }
                    _isSaved.value = save
                    announce(if (save) R.string.toast_saved_playlist_to_music_library else R.string.toast_removed_playlist_from_library)
                }
            }
        }

        /** Fetches the collection's tracks and hands them to [start] in the order [mode] asks for. */
        fun play(
            item: MusicCollectionActionItem,
            mode: CollectionPlayMode,
            announce: (Int) -> Unit,
            start: (first: MusicTrack, queue: List<MusicTrack>, asRadio: Boolean) -> Unit,
        ) {
            viewModelScope.launch {
                runAction(announce) {
                    val tracks = fetch(item)?.tracks.orEmpty()
                    check(tracks.isNotEmpty()) { "No tracks for ${item.id}" }
                    val queue = if (mode == CollectionPlayMode.Shuffle) tracks.shuffled() else tracks
                    start(queue.first(), if (mode == CollectionPlayMode.Radio) emptyList() else queue, mode == CollectionPlayMode.Radio)
                }
            }
        }

        private suspend fun fetch(item: MusicCollectionActionItem): PlaylistDetails? =
            withTimeoutOrNull(FETCH_TIMEOUT_MS) { YouTubeMusicService.fetchPlaylistDetails(item.id) }

        private suspend fun runAction(
            announce: (Int) -> Unit,
            block: suspend () -> Unit,
        ) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                announce(R.string.quick_action_failed)
            }
        }
    }
