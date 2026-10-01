package io.github.aedev.flow.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.playlist.sortedFor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val SHELF_LIMIT = 20

/** The viewer's own music playlists for the music home, in the order chosen on the playlists page. */
@HiltViewModel
class MusicHomePlaylistsViewModel
    @Inject
    constructor(
        playlistRepository: PlaylistRepository,
        preferences: PlayerPreferences,
    ) : ViewModel() {
        val playlists: StateFlow<List<MusicPlaylist>> =
            combine(playlistRepository.getMusicPlaylistsFlow(), preferences.playlistListOrder) { playlists, order ->
                playlists.sortedFor(order).take(SHELF_LIMIT).map { playlist ->
                    MusicPlaylist(
                        id = playlist.id,
                        title = playlist.name,
                        thumbnailUrl = playlist.thumbnailUrl,
                        trackCount = playlist.videoCount,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }
