package com.flow.youtube.ui.screens.music

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.music.DownloadManager
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.ui.screens.playlists.PlaylistInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class MusicPlaylistsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicPlaylistsUiState())
    val uiState: StateFlow<MusicPlaylistsUiState> = _uiState.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            playlistRepository.getMusicPlaylistsFlow().collect { playlists ->
                _uiState.update { 
                    it.copy(
                        playlists = playlists,
                        isLoading = false
                    ) 
                }
            }
        }
    }

    fun createPlaylist(name: String, description: String, isPrivate: Boolean) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(
                playlistId = id,
                name = name,
                description = description,
                isPrivate = isPrivate,
                isMusic = true
            )
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistName(playlistId, newName)
            Toast.makeText(context, "Playlist renamed", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadPlaylist(playlist: PlaylistInfo) {
        viewModelScope.launch {
            Toast.makeText(context, "Starting download for ${playlist.name}...", Toast.LENGTH_SHORT).show()
            val videos = playlistRepository.getPlaylistVideosFlow(playlist.id).first()
            
            var successCount = 0
            videos.forEach { video ->
                try {
                    val musicTrack = MusicTrack(
                        videoId = video.id,
                        title = video.title,
                        artist = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        duration = video.duration,
                        sourceUrl = ""
                    )
                    
                    val audioUrl = YouTubeMusicService.getAudioUrl(video.id)
                    if (audioUrl != null) {
                        val result = downloadManager.downloadTrack(musicTrack, audioUrl)
                        if (result.isSuccess) successCount++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (successCount > 0) {
                Toast.makeText(context, "Downloaded $successCount tracks from ${playlist.name}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to download playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class MusicPlaylistsUiState(
    val playlists: List<PlaylistInfo> = emptyList(),
    val isLoading: Boolean = false
)
