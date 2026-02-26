package com.flow.youtube.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.music.DownloadManager as MusicDownloadManager
import com.flow.youtube.data.video.VideoDownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val videoDownloadManager: VideoDownloadManager,
    private val musicDownloadManager: MusicDownloadManager
) : ViewModel() {
    
    private lateinit var likedVideosRepository: LikedVideosRepository
    private lateinit var viewHistory: ViewHistory
    private var playlistRepository: PlaylistRepository? = null
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        playlistRepository = PlaylistRepository(context)
        
        // Load liked videos count (excluding music)
        viewModelScope.launch {
            likedVideosRepository.getLikedVideosFlow().collect { likedVideos ->
                _uiState.update { it.copy(likedVideosCount = likedVideos.size) }
            }
        }
        
        viewModelScope.launch {
            viewHistory.getVideoCount().collect { count ->
                _uiState.update { it.copy(watchHistoryCount = count) }
            }
        }

        // Load playlists count and watch-later count
        playlistRepository?.let { repo ->
            viewModelScope.launch {
                repo.getAllPlaylistsFlow().collect { playlists ->
                    _uiState.update { it.copy(playlistsCount = playlists.size) }
                }
            }

            viewModelScope.launch {
                repo.getVideoOnlyWatchLaterFlow().collect { videos ->
                    _uiState.update { it.copy(watchLaterCount = videos.size) }
                }
            }

            viewModelScope.launch {
                repo.getVideoOnlySavedShortsFlow().collect { videos ->
                    _uiState.update { it.copy(savedShortsCount = videos.size) }
                }
            }
        }

        // Observe downloaded video count (completed downloads only)
        viewModelScope.launch {
            videoDownloadManager.downloadedVideos.collect { videos ->
                _uiState.update { it.copy(downloadedVideosCount = videos.size) }
            }
        }

        // Observe downloaded music count
        viewModelScope.launch {
            musicDownloadManager.downloadedTracks.collect { tracks ->
                _uiState.update { it.copy(downloadedSongsCount = tracks.size) }
            }
        }
    }
}

data class LibraryUiState(
    val likedVideosCount: Int = 0,
    val watchHistoryCount: Int = 0,
    val playlistsCount: Int = 0,
    val watchLaterCount: Int = 0,
    val savedShortsCount: Int = 0,
    val downloadedVideosCount: Int = 0,
    val downloadedSongsCount: Int = 0
)
