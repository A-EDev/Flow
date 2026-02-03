package com.flow.youtube.ui.screens.music

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val _playlistDownloadProgress = MutableStateFlow<Float>(0f)
    val playlistDownloadProgress = _playlistDownloadProgress.asStateFlow()

    private val _currentDownloadingTrack = MutableStateFlow<String?>(null)
    val currentDownloadingTrack = _currentDownloadingTrack.asStateFlow()

    private val _isDownloadingPlaylist = MutableStateFlow(false)
    val isDownloadingPlaylist = _isDownloadingPlaylist.asStateFlow()

    fun downloadPlaylist(playlist: PlaylistInfo) {
        viewModelScope.launch {
            if (_isDownloadingPlaylist.value) return@launch

            _isDownloadingPlaylist.value = true
            Toast.makeText(context, "Starting download for ${playlist.name}...", Toast.LENGTH_SHORT).show()
            
            try {
                val videos = playlistRepository.getPlaylistVideosFlow(playlist.id).first()
                val totalTracks = videos.size
                
                if (totalTracks == 0) {
                     Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
                     _isDownloadingPlaylist.value = false
                     return@launch
                }

                var successCount = 0
                var processedCount = 0

                videos.forEach { video ->
                    _currentDownloadingTrack.value = video.title
                    
                    try {
                        val musicTrack = MusicTrack(
                            videoId = video.id,
                            title = video.title,
                            artist = video.channelName,
                            thumbnailUrl = video.thumbnailUrl,
                            duration = video.duration,
                            sourceUrl = ""
                        )
                        
                        val result = downloadManager.downloadTrack(musicTrack)
                        if (result.isSuccess) successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    processedCount++
                    _playlistDownloadProgress.value = processedCount.toFloat() / totalTracks
                }
                
                if (successCount > 0) {
                    Toast.makeText(context, "Downloaded $successCount tracks from ${playlist.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to download playlist", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error downloading playlist", e)
                Toast.makeText(context, "Error downloading playlist", Toast.LENGTH_SHORT).show()
            } finally {
                 _isDownloadingPlaylist.value = false
                 _currentDownloadingTrack.value = null
                 _playlistDownloadProgress.value = 0f
            }
        }
    }

    fun downloadPlaylistTracks(playlistDetails: PlaylistDetails) {
         viewModelScope.launch {
            if (_isDownloadingPlaylist.value) return@launch

            _isDownloadingPlaylist.value = true
            Toast.makeText(context, "Starting download for ${playlistDetails.title}...", Toast.LENGTH_SHORT).show()
            
            try {
                val tracks = playlistDetails.tracks
                val totalTracks = tracks.size

                if (totalTracks == 0) {
                     _isDownloadingPlaylist.value = false
                     return@launch
                }

                var successCount = 0
                val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
                
                val semaphore = kotlinx.coroutines.sync.Semaphore(3)
                
                tracks.map { track ->
                    async {
                        val isSuccess = semaphore.withPermit {
                            _currentDownloadingTrack.value = track.title
                            var currentTrack = track
                            
                            try {
                                if (currentTrack.duration == 0) {
                                    try {
                                        val duration = YouTubeMusicService.fetchVideoDuration(track.videoId)
                                        if (duration > 0) {
                                            currentTrack = currentTrack.copy(duration = duration)
                                        }
                                    } catch (e: Exception) {
                                    }
                                }

                                val result = downloadManager.downloadTrack(currentTrack)
                                return@withPermit result.isSuccess
                            } catch (e: Exception) {
                                Log.e("MusicViewModel", "Failed to download track ${track.title}", e)
                            }
                            false
                        }
                        
                        val currentProcessed = processedCount.incrementAndGet()
                        _playlistDownloadProgress.value = currentProcessed.toFloat() / totalTracks
                        
                        isSuccess
                    }
                }.awaitAll().count { it }
                
                successCount = tracks.size 

                
                if (successCount > 0) {
                    Toast.makeText(context, "Downloaded $successCount tracks", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                 Log.e("MusicViewModel", "Error downloading playlist details", e)
                 Toast.makeText(context, "Error downloading playlist", Toast.LENGTH_SHORT).show()
            } finally {
                 _isDownloadingPlaylist.value = false
                 _currentDownloadingTrack.value = null
                 _playlistDownloadProgress.value = 0f
            }
        }
    }
}

data class MusicPlaylistsUiState(
    val playlists: List<PlaylistInfo> = emptyList(),
    val isLoading: Boolean = false
)
