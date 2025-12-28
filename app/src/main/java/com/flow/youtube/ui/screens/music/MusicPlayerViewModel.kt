package com.flow.youtube.ui.screens.music

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideoInfo
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.music.DownloadManager
import com.flow.youtube.data.music.PlaylistRepository
import com.flow.youtube.data.model.Video
import java.util.UUID
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistRepository: PlaylistRepository,
    private val downloadManager: DownloadManager,
    private val likedVideosRepository: LikedVideosRepository,
    private val viewHistory: ViewHistory,
    private val localPlaylistRepository: com.flow.youtube.data.local.PlaylistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    
    private var isInitialized = false
    // private lateinit var playlistRepository: PlaylistRepository // Injected
    // private lateinit var downloadManager: DownloadManager // Injected

    init {
        // We initialize EnhancedMusicPlayerManager here as it needs context. 
        // Ideally this should be in Application or a separate Manager that is injected.
        // But for now, using the injected ApplicationContext is safe.
        EnhancedMusicPlayerManager.initialize(context)
        initializeObservers()
    }

    fun initialize(context: Context) {
        // No-op or call init logic if not done
        // initializeObservers() // Moved to init
    }
    
    private fun initializeObservers() {
        if (isInitialized) return
        isInitialized = true
        
        // Observe player events (Queue navigation)
        viewModelScope.launch {
            EnhancedMusicPlayerManager.playerEvents.collect { event ->
                when (event) {
                    is EnhancedMusicPlayerManager.PlayerEvent.RequestPlayTrack -> {
                        loadAndPlayTrack(event.track, _uiState.value.queue)
                    }
                }
            }
        }
        
        // Observe player state
        viewModelScope.launch {
            EnhancedMusicPlayerManager.playerState.collect { playerState ->
                _uiState.value = _uiState.value.copy(
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    duration = playerState.duration
                )
            }
        }
            
        // Observe current track
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentTrack.collect { track ->
                _uiState.value = _uiState.value.copy(currentTrack = track)
                // Check if current track is favorite
                track?.let { checkIfFavorite(it.videoId) }
            }
        }
            
        // Observe queue
        viewModelScope.launch {
            EnhancedMusicPlayerManager.queue.collect { queue ->
                _uiState.value = _uiState.value.copy(queue = queue)
            }
        }
            
        // Observe queue index
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentQueueIndex.collect { index ->
                _uiState.value = _uiState.value.copy(currentQueueIndex = index)
            }
        }
            
        // Observe shuffle
        viewModelScope.launch {
            EnhancedMusicPlayerManager.shuffleEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(shuffleEnabled = enabled)
            }
        }
            
        // Observe repeat mode
        viewModelScope.launch {
            EnhancedMusicPlayerManager.repeatMode.collect { mode ->
                _uiState.value = _uiState.value.copy(repeatMode = mode)
            }
        }
            
        // Observe playlists (From Local Room DB now)
        viewModelScope.launch {
            localPlaylistRepository.getAllPlaylistsFlow().collect { playlistInfos ->
                // Map PlaylistInfo to Music Playlist (simplified)
                val playlists = playlistInfos.map { info ->
                    com.flow.youtube.data.music.Playlist(
                        id = info.id,
                        name = info.name,
                        description = info.description,
                        tracks = emptyList(), // Tracks not needed for selection list
                        createdAt = info.createdAt,
                        thumbnailUrl = info.thumbnailUrl
                    )
                }
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }
    
    private fun checkIfFavorite(videoId: String) {
        viewModelScope.launch {
            // Check LikedVideosRepository
            likedVideosRepository.getLikeState(videoId).collect { state ->
                _uiState.value = _uiState.value.copy(isLiked = state == "LIKED")
            }
        }
    }

    fun loadAndPlayTrack(track: MusicTrack, queue: List<MusicTrack> = emptyList()) {
        viewModelScope.launch {
            // Add to history (Music specific)
            playlistRepository.addToHistory(track)
            
            // Add to main ViewHistory
            viewHistory.savePlaybackPosition(
                videoId = track.videoId,
                position = 0,
                duration = track.duration.toLong() * 1000,
                title = track.title,
                thumbnailUrl = track.thumbnailUrl,
                channelName = track.artist,
                channelId = "" // Music tracks might not have channel ID readily available
            )

            // Set track data immediately so UI displays it
            _uiState.value = _uiState.value.copy(
                currentTrack = track,
                isLoading = true, 
                error = null
            )
            
            try {
                // Get audio URL from YouTube
                val audioUrl = YouTubeMusicService.getAudioUrl(track.videoId)
                
                if (audioUrl != null) {
                    // Play track with EnhancedMusicPlayerManager
                    EnhancedMusicPlayerManager.playTrack(
                        track = track,
                        audioUrl = audioUrl,
                        queue = if (queue.isNotEmpty()) queue else listOf(track)
                    )
                    
                    // Get related music tracks for queue if no queue provided
                    if (queue.isEmpty()) {
                        val relatedTracks = YouTubeMusicService.getRelatedMusic(track.videoId, 20)
                        if (relatedTracks.isNotEmpty()) {
                            EnhancedMusicPlayerManager.addToQueue(relatedTracks)
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        currentTrack = track,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not load audio stream"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load track: ${e.message}"
                )
            }
        }
    }

    fun togglePlayPause() {
        EnhancedMusicPlayerManager.togglePlayPause()
    }

    fun play() {
        EnhancedMusicPlayerManager.play()
    }

    fun pause() {
        EnhancedMusicPlayerManager.pause()
    }

    fun seekTo(position: Long) {
        EnhancedMusicPlayerManager.seekTo(position)
        _uiState.value = _uiState.value.copy(currentPosition = position)
    }

    fun skipToNext() {
        EnhancedMusicPlayerManager.playNext()
    }

    fun skipToPrevious() {
        EnhancedMusicPlayerManager.playPrevious()
    }

    fun playFromQueue(index: Int) {
        EnhancedMusicPlayerManager.playFromQueue(index)
    }

    fun removeFromQueue(index: Int) {
        EnhancedMusicPlayerManager.removeFromQueue(index)
    }

    fun toggleShuffle() {
        EnhancedMusicPlayerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        EnhancedMusicPlayerManager.toggleRepeat()
    }

    fun toggleLike() {
        val currentTrack = _uiState.value.currentTrack ?: return
        
        viewModelScope.launch {
            // Toggle in PlaylistRepository (Music specific)
            val isNowFavorite = playlistRepository.toggleFavorite(currentTrack)
            _uiState.value = _uiState.value.copy(isLiked = isNowFavorite)
            
            // Sync with LikedVideosRepository (Main Library)
            if (isNowFavorite) {
                likedVideosRepository.likeVideo(
                    LikedVideoInfo(
                        videoId = currentTrack.videoId,
                        title = currentTrack.title,
                        thumbnail = currentTrack.thumbnailUrl,
                        channelName = currentTrack.artist,
                        isMusic = true
                    )
                )
            } else {
                likedVideosRepository.removeLikeState(currentTrack.videoId)
            }
        }
    }
    
    fun addToPlaylist(playlistId: String, track: MusicTrack? = null) {
        val trackToAdd = track ?: _uiState.value.currentTrack ?: return
        
        viewModelScope.launch {
            // Convert to Video domain object
            val video = Video(
                id = trackToAdd.videoId,
                title = trackToAdd.title,
                channelName = trackToAdd.artist,
                channelId = "", 
                thumbnailUrl = trackToAdd.thumbnailUrl,
                duration = trackToAdd.duration,
                viewCount = 0,
                uploadDate = "",
                description = trackToAdd.album,
                isMusic = true
            )
            localPlaylistRepository.addVideoToPlaylist(playlistId, video)
            Toast.makeText(context, "Added to playlist", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun createPlaylist(name: String, description: String = "", track: MusicTrack? = null) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            localPlaylistRepository.createPlaylist(id, name, description, false)
            track?.let { addToPlaylist(id, it) }
        }
    }
    
    fun showAddToPlaylistDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddToPlaylistDialog = show)
    }
    
    fun showCreatePlaylistDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreatePlaylistDialog = show)
    }
    
    fun downloadTrack(track: MusicTrack? = null) {
        val trackToDownload = track ?: _uiState.value.currentTrack ?: return
        
        viewModelScope.launch {
            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            
            // Get audio URL first
            try {
                val audioUrl = YouTubeMusicService.getAudioUrl(trackToDownload.videoId)
                if (audioUrl != null) {
                    val result = downloadManager.downloadTrack(trackToDownload, audioUrl)
                    if (result.isSuccess) {
                        Toast.makeText(context, "Saved to Library", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Could not get audio URL", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    suspend fun isTrackDownloaded(videoId: String): Boolean {
        return downloadManager.isDownloaded(videoId)
    }
    
    suspend fun isTrackFavorite(videoId: String): Boolean {
        return playlistRepository.isFavorite(videoId)
    }

    fun updateProgress() {
        val position = EnhancedMusicPlayerManager.getCurrentPosition()
        val duration = EnhancedMusicPlayerManager.getDuration()
        
        _uiState.value = _uiState.value.copy(
            currentPosition = position,
            duration = if (duration > 0) duration else _uiState.value.duration
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release the player, it's managed globally
    }
}

data class MusicPlayerUiState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queue: List<MusicTrack> = emptyList(),
    val currentQueueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlists: List<com.flow.youtube.data.music.Playlist> = emptyList(),
    val showAddToPlaylistDialog: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false
)
