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
import kotlinx.coroutines.flow.update
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
                _uiState.update { it.copy(
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    duration = playerState.duration
                ) }
            }
        }
            
        // Observe current track
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentTrack.collect { track ->
                _uiState.update { it.copy(
                    currentTrack = track,
                    lyrics = null // Reset lyrics for new track
                ) }
                // Check if current track is favorite
                track?.let { 
                    checkIfFavorite(it.videoId)
                    fetchLyrics(it.artist, it.title)
                    fetchRelatedContent(it.videoId)
                }
            }
        }

        // Observe playing from
        viewModelScope.launch {
            EnhancedMusicPlayerManager.playingFrom.collect { source ->
                _uiState.update { it.copy(playingFrom = source) }
            }
        }
            
        // Observe queue
        viewModelScope.launch {
            EnhancedMusicPlayerManager.queue.collect { queue ->
                _uiState.update { it.copy(queue = queue) }
            }
        }
            
        // Observe queue index
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentQueueIndex.collect { index ->
                _uiState.update { it.copy(currentQueueIndex = index) }
            }
        }
            
        // Observe shuffle
        viewModelScope.launch {
            EnhancedMusicPlayerManager.shuffleEnabled.collect { enabled ->
                _uiState.update { it.copy(shuffleEnabled = enabled) }
            }
        }
            
        // Observe repeat mode
        viewModelScope.launch {
            EnhancedMusicPlayerManager.repeatMode.collect { mode ->
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }
            
        // Observe playlists (From Local Room DB now)
        viewModelScope.launch {
            localPlaylistRepository.getMusicPlaylistsFlow().collect { playlistInfos ->
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
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }
    
    private fun checkIfFavorite(videoId: String) {
        viewModelScope.launch {
            // Check LikedVideosRepository
            likedVideosRepository.getLikeState(videoId).collect { state ->
                _uiState.update { it.copy(isLiked = state == "LIKED") }
            }
        }
    }

    fun loadAndPlayTrack(track: MusicTrack, queue: List<MusicTrack> = emptyList(), sourceName: String? = null) {
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
                channelId = "", // Music tracks might not have channel ID readily available
                isMusic = true
            )

            val finalSourceName = sourceName ?: "Radio • ${track.artist}"

            // Set track data immediately in Manager so all observers (including FlowApp) see it
            EnhancedMusicPlayerManager.setCurrentTrack(track, finalSourceName)

            // Set track data immediately in local UI state
            _uiState.update { it.copy(
                currentTrack = track,
                isLoading = true, 
                error = null,
                playingFrom = finalSourceName,
                selectedFilter = "All" // Reset filter for new track
            ) }
            
            try {
                // Check if track is downloaded
                val localPath = downloadManager.getDownloadedTrackPath(track.videoId)
                
                val audioUrl = if (localPath != null && java.io.File(localPath).exists()) {
                    localPath
                } else {
                    // Get audio URL from YouTube if not offline
                    YouTubeMusicService.getAudioUrl(track.videoId)
                }
                
                if (audioUrl != null) {
                    // Play track with EnhancedMusicPlayerManager
                    EnhancedMusicPlayerManager.playTrack(
                        track = track,
                        audioUrl = audioUrl,
                        queue = if (queue.isNotEmpty()) queue else listOf(track)
                    )
                    
                    // Fetch related content for the RELATED tab
                    fetchRelatedContent(track.videoId)
                    
                    // Only fetch related tracks if we don't have a substantial queue already
                    // This prevents resetting the queue when navigating within a playlist/search results
                    if (queue.size <= 1) {
                        val relatedTracks = YouTubeMusicService.getRelatedMusic(track.videoId, 20)
                        if (relatedTracks.isNotEmpty()) {
                            EnhancedMusicPlayerManager.updateQueue(listOf(track) + relatedTracks)
                            _uiState.update { it.copy(autoplaySuggestions = relatedTracks) }
                        }
                    }
                    
                    _uiState.update { it.copy(
                        currentTrack = track,
                        isLoading = false
                    ) }
                } else {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Could not load audio stream"
                    ) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load track: ${e.message}"
                ) }
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

    fun toggleAutoplay() {
        _uiState.update { it.copy(autoplayEnabled = !it.autoplayEnabled) }
    }

    fun setFilter(filter: String) {
        val currentTrack = _uiState.value.currentTrack ?: return
        _uiState.update { it.copy(selectedFilter = filter, isLoading = true) }
        
        viewModelScope.launch {
            try {
                // In a real app, we'd pass the filter to the service
                // For now, we'll fetch fresh related tracks and shuffle them differently based on filter
                val freshRelated = YouTubeMusicService.getRelatedMusic(currentTrack.videoId, 25)
                
                val filteredList = when (filter) {
                    "Discover" -> freshRelated.shuffled().take(20)
                    "Popular" -> freshRelated.sortedByDescending { it.title.length }.take(20) // Mock popular
                    "Deep cuts" -> freshRelated.reversed().take(20)
                    "Workout" -> freshRelated.filter { it.title.contains("remix", ignoreCase = true) || true }.shuffled()
                    else -> freshRelated
                }
                
                _uiState.update { it.copy(
                    autoplaySuggestions = filteredList,
                    isLoading = false
                ) }
                
                // Update the queue: Current track + filtered suggestions
                EnhancedMusicPlayerManager.updateQueue(listOf(currentTrack) + filteredList)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val currentQueue = _uiState.value.queue.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val track = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, track)
            _uiState.update { it.copy(queue = currentQueue) }
            EnhancedMusicPlayerManager.updateQueue(currentQueue)
        }
    }

    fun seekTo(position: Long) {
        EnhancedMusicPlayerManager.seekTo(position)
        _uiState.update { it.copy(currentPosition = position) }
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

    fun switchMode(isVideo: Boolean) {
        val currentTrack = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                val url = if (isVideo) {
                    YouTubeMusicService.getVideoUrl(currentTrack.videoId)
                } else {
                    YouTubeMusicService.getAudioUrl(currentTrack.videoId)
                }
                
                if (url != null) {
                    EnhancedMusicPlayerManager.switchMode(url)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun fetchRelatedContent(videoId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRelatedLoading = true) }
            try {
                val related = YouTubeMusicService.getRelatedMusic(videoId, 20)
                _uiState.update { it.copy(
                    relatedContent = related,
                    isRelatedLoading = false
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRelatedLoading = false) }
            }
        }
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
            _uiState.update { it.copy(isLiked = isNowFavorite) }
            
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
            localPlaylistRepository.createPlaylist(id, name, description, false, isMusic = true)
            track?.let { addToPlaylist(id, it) }
        }
    }
    
    fun showAddToPlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showAddToPlaylistDialog = show) }
    }
    
    fun showCreatePlaylistDialog(show: Boolean) {
        _uiState.update { it.copy(showCreatePlaylistDialog = show) }
    }

    fun playNext(track: MusicTrack) {
        EnhancedMusicPlayerManager.playNext(track)
        Toast.makeText(context, "Will play next", Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: MusicTrack) {
        EnhancedMusicPlayerManager.addToQueue(track)
        Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
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

    private var lyricsJob: kotlinx.coroutines.Job? = null

    fun fetchLyrics(artist: String, title: String) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            val cleanArtist = artist.trim()
            val cleanTitle = title.trim()
            
            _uiState.update { it.copy(
                isLyricsLoading = true, 
                lyrics = null,
                syncedLyrics = emptyList()
            ) }
            
            // Small delay to avoid spamming API while skipping
            delay(500)
            
            val response = com.flow.youtube.data.music.LyricsService.getLyrics(
                cleanArtist, 
                cleanTitle, 
                _uiState.value.duration.toInt() / 1000
            )
            
            if (response != null) {
                val parsedSynced = response.syncedLyrics?.let { parseLyrics(it) } ?: emptyList()
                _uiState.update { it.copy(
                    isLyricsLoading = false,
                    lyrics = response.plainLyrics,
                    syncedLyrics = parsedSynced
                ) }
            } else {
                _uiState.update { it.copy(isLyricsLoading = false) }
            }
        }
    }

    private fun parseLyrics(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        // Support [mm:ss.xx], [mm:ss.xxx], [m:ss.xx], etc.
        // Also support multiple timestamps like [00:12.34][00:45.67] Lyrics
        val timeRegex = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\]")
        
        lrc.lines().forEach { line ->
            val timeMatches = timeRegex.findAll(line).toList()
            if (timeMatches.isNotEmpty()) {
                val content = line.replace(timeRegex, "").trim()
                if (content.isNotEmpty()) {
                    timeMatches.forEach { match ->
                        val min = match.groupValues[1].toLong()
                        val sec = match.groupValues[2].toLong()
                        val msStr = match.groupValues[3]
                        val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
                        val time = (min * 60 * 1000) + (sec * 1000) + ms
                        lines.add(LyricLine(time, content))
                    }
                }
            }
        }
        return lines.sortedBy { it.time }
    }

    fun updateProgress() {
        val position = EnhancedMusicPlayerManager.getCurrentPosition()
        val duration = EnhancedMusicPlayerManager.getDuration()
        
        _uiState.update { it.copy(
            currentPosition = position,
            duration = if (duration > 0) duration else it.duration
        ) }
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
    val autoplaySuggestions: List<MusicTrack> = emptyList(),
    val currentQueueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playlists: List<com.flow.youtube.data.music.Playlist> = emptyList(),
    val showAddToPlaylistDialog: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val lyrics: String? = null,
    val syncedLyrics: List<LyricLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val playingFrom: String = "Unknown Source",
    val autoplayEnabled: Boolean = true,
    val selectedFilter: String = "All",
    val relatedContent: List<MusicTrack> = emptyList(),
    val isRelatedLoading: Boolean = false
)

data class LyricLine(
    val time: Long,
    val content: String
)
