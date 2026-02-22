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
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.flow.youtube.data.lyrics.LyricsEntry
import com.flow.youtube.data.lyrics.LyricsHelper
import com.flow.youtube.data.lyrics.PreferredLyricsProvider
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.flow.first

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
    
    private val playerPreferences = PlayerPreferences(context)
    private var lyricsHelper = LyricsHelper() 
    
    private var isInitialized = false
    private var loadTrackJob: kotlinx.coroutines.Job? = null

    init {
        EnhancedMusicPlayerManager.initialize(context)
        initializeObservers()
    }
    
    private fun initializeObservers() {
        if (isInitialized) return
        isInitialized = true
        
        viewModelScope.launch {
            EnhancedMusicPlayerManager.playerEvents.collect { event ->
                when (event) {
                    is EnhancedMusicPlayerManager.PlayerEvent.RequestPlayTrack -> {
                        loadAndPlayTrack(event.track, _uiState.value.queue)
                    }
                    is EnhancedMusicPlayerManager.PlayerEvent.RequestToggleLike -> {
                        toggleLike()
                    }
                }
            }
        }
        
        viewModelScope.launch {
            EnhancedMusicPlayerManager.playerState.collect { playerState ->
                _uiState.update { it.copy(
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    duration = playerState.duration,
                    currentPosition = playerState.position
                ) }
            }
        }
        
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentPosition.collect { position ->
                _uiState.update { it.copy(currentPosition = position) }
            }
        }
            
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentTrack.collect { track ->
                _uiState.update { it.copy(
                    currentTrack = track,
                    lyrics = null,
                    syncedLyrics = emptyList(),
                    // Fix: Reset duration and position to prevent showing previous track's info
                    duration = if (track != null) track.duration * 1000L else 0L,
                    currentPosition = 0L
                ) }
                track?.let { 
                    checkIfFavorite(it.videoId)
                    fetchLyrics(it.videoId, it.artist, it.title, it.duration)
                    fetchRelatedContent(it.videoId)
                }
            }
        }

        viewModelScope.launch {
            EnhancedMusicPlayerManager.playingFrom.collect { source ->
                _uiState.update { it.copy(playingFrom = source) }
            }
        }
        
        viewModelScope.launch {
            downloadManager.downloadedTracks.collect { tracks ->
                val ids = tracks.map { it.track.videoId }.toSet()
                _uiState.update { it.copy(downloadedTrackIds = ids) }
            }
        }
            
        viewModelScope.launch {
            EnhancedMusicPlayerManager.queue.collect { queue ->
                _uiState.update { it.copy(queue = queue) }
            }
        }
            
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentQueueIndex.collect { index ->
                _uiState.update { it.copy(currentQueueIndex = index) }
            }
        }
            
        viewModelScope.launch {
            EnhancedMusicPlayerManager.shuffleEnabled.collect { enabled ->
                _uiState.update { it.copy(shuffleEnabled = enabled) }
            }
        }
            
        viewModelScope.launch {
            EnhancedMusicPlayerManager.repeatMode.collect { mode ->
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }
            
        viewModelScope.launch {
            localPlaylistRepository.getMusicPlaylistsFlow().collect { playlistInfos ->
                val playlists = playlistInfos.map { info ->
                    com.flow.youtube.data.music.Playlist(
                        id = info.id,
                        name = info.name,
                        description = info.description,
                        tracks = emptyList(), 
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
            likedVideosRepository.getLikeState(videoId).collect { state ->
                val isLiked = state == "LIKED"
                _uiState.update { it.copy(isLiked = isLiked) }
                EnhancedMusicPlayerManager.setLiked(isLiked)
            }
        }
    }

    fun loadAndPlayTrack(track: MusicTrack, queue: List<MusicTrack> = emptyList(), sourceName: String? = null) {
        loadTrackJob?.cancel()
        loadTrackJob = viewModelScope.launch(PerformanceDispatcher.networkIO) {
            supervisorScope {
                launch(PerformanceDispatcher.diskIO) {
                    playlistRepository.addToHistory(track)
                }
                
                launch(PerformanceDispatcher.diskIO) {
                    viewHistory.savePlaybackPosition(
                        videoId = track.videoId,
                        position = 0,
                        duration = track.duration.toLong() * 1000,
                        title = track.title,
                        thumbnailUrl = track.thumbnailUrl,
                        channelName = track.artist,
                        channelId = "",
                        isMusic = true
                    )
                }
            }
            
            viewHistory.savePlaybackPosition(
                videoId = track.videoId,
                position = 0,
                duration = track.duration.toLong() * 1000,
                title = track.title,
                thumbnailUrl = track.thumbnailUrl,
                channelName = track.artist,
                channelId = "", 
                isMusic = true
            )

            val finalSourceName = sourceName ?: "Radio • ${track.artist}"
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                EnhancedMusicPlayerManager.setPendingTrack(track, finalSourceName)
            }

            _uiState.update { it.copy(
                currentTrack = track,
                isLoading = true, 
                error = null,
                playingFrom = finalSourceName,
                selectedFilter = "All" 
            ) }
            
            try {
                val isCached = downloadManager.isCachedForOffline(track.videoId)
                
                val streamUrl = if (isCached) {
                    android.util.Log.d("MusicPlayerViewModel", "Track ${track.videoId} found in cache - playing offline")
                    "music://${track.videoId}"
                } else {
                    // OPTIMIZED: Use centralized resolution with caching
                    val url = EnhancedMusicPlayerManager.resolveStreamUrl(track.videoId)
                    
                    if (url != null) {
                        android.util.Log.d("MusicPlayerViewModel", "Resolved stream URL: $url")
                        url
                    } else {
                        android.util.Log.w("MusicPlayerViewModel", "Resolution failed, falling back to ResolvingDataSource")
                        "music://${track.videoId}"
                    }
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    EnhancedMusicPlayerManager.playTrack(
                        track = track,
                        audioUrl = streamUrl,
                        queue = if (queue.isNotEmpty()) queue else listOf(track)
                    )
                }
                
                supervisorScope {
                    launch(PerformanceDispatcher.networkIO) {
                        fetchRelatedContent(track.videoId)
                    }
                    
                    if (queue.size <= 1) {
                        launch(PerformanceDispatcher.networkIO) {
                            val relatedTracks = withTimeoutOrNull(8_000L) {
                                YouTubeMusicService.getRelatedMusic(track.videoId, 20)
                            } ?: emptyList()
                            
                            if (relatedTracks.isNotEmpty()) {
                                EnhancedMusicPlayerManager.updateQueue(listOf(track) + relatedTracks)
                                _uiState.update { it.copy(autoplaySuggestions = relatedTracks) }
                            }
                        }
                    }
                }
                
                _uiState.update { it.copy(
                    currentTrack = track,
                    isLoading = false
                ) }
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
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val freshRelated = withTimeoutOrNull(10_000L) {
                    YouTubeMusicService.getRelatedMusic(currentTrack.videoId, 25)
                } ?: emptyList()
                
                val filteredList = when (filter) {
                    "Discover" -> freshRelated.shuffled().take(20)
                    "Popular" -> freshRelated.sortedByDescending { it.title.length }.take(20)
                    "Deep cuts" -> freshRelated.reversed().take(20)
                    "Workout" -> freshRelated.filter { it.title.contains("remix", ignoreCase = true) || true }.shuffled()
                    else -> freshRelated
                }
                
                _uiState.update { it.copy(
                    autoplaySuggestions = filteredList,
                    isLoading = false
                ) }
                
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
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            _uiState.update { it.copy(isRelatedLoading = true) }
            try {
                val related = withTimeoutOrNull(10_000L) {
                    YouTubeMusicService.getRelatedMusic(videoId, 20)
                } ?: emptyList()
                
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
        
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val isNowFavorite = playlistRepository.toggleFavorite(currentTrack)
            _uiState.update { it.copy(isLiked = isNowFavorite) }
            
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
            val video = Video(
                id = trackToAdd.videoId,
                title = trackToAdd.title,
                channelName = trackToAdd.artist,
                channelId = "", 
                thumbnailUrl = trackToAdd.thumbnailUrl,
                duration = trackToAdd.duration,
                viewCount = 0,
                uploadDate = "",
                timestamp = System.currentTimeMillis(),
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
        
        if (_uiState.value.downloadedTrackIds.contains(trackToDownload.videoId)) {
             viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                 Toast.makeText(context, "Already downloaded", Toast.LENGTH_SHORT).show()
             }
             return
        }

        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
            }
            
            try {
                downloadManager.downloadTrack(trackToDownload)
                
            } catch (e: Exception) {
                android.util.Log.e("MusicDownload", "Download start exception", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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

    private fun cleanName(name: String): String {
        return name
            .replace(Regex("(?i)\\s*-\\s*topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(\\[]official (audio|video|music video|lyric video)[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(\\[]lyrics?[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(]feat\\.? .*?[)]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[\\[]feat\\.? .*?[\\]]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun fetchLyrics(videoId: String, artist: String, title: String, duration: Int? = null) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(
                isLyricsLoading = true, 
                lyrics = null,
                syncedLyrics = emptyList()
            ) }
            
            val cleanArtist = cleanName(artist)
            val cleanTitle = cleanName(title)
            val targetDuration = duration ?: (_uiState.value.duration.toInt() / 1000)

            try {
                val preferredProviderName = playerPreferences.preferredLyricsProvider.first()
                val preferredProvider = PreferredLyricsProvider.fromString(preferredProviderName)
                
                if (lyricsHelper.preferredProvider != preferredProvider) {
                    lyricsHelper = LyricsHelper(preferredProvider)
                }
                
                val result = lyricsHelper.getLyrics(videoId, cleanTitle, cleanArtist, targetDuration)
                
                if (result != null) {
                    val (entries, providerName) = result
                    val hasWords = entries.any { it.words != null }
                    android.util.Log.d("MusicPlayerViewModel", "Got ${entries.size} lyrics lines from $providerName (word-sync=$hasWords)")
                    
                    if (entries.size > 1 || (entries.size == 1 && entries[0].time > 0)) {
                        val plainText = entries.joinToString("\n") { it.text }
                        _uiState.update { it.copy(
                            isLyricsLoading = false,
                            lyrics = plainText,
                            syncedLyrics = entries
                        ) }
                    } else if (entries.size == 1) {
                        _uiState.update { it.copy(
                            isLyricsLoading = false,
                            lyrics = entries[0].text,
                            syncedLyrics = emptyList()
                        ) }
                    } else {
                        _uiState.update { it.copy(isLyricsLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLyricsLoading = false) }
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicPlayerViewModel", "Lyrics fetch failed", e)
                _uiState.update { it.copy(isLyricsLoading = false) }
            }
        }
    }

    /**
     * Called when the player screen opens for a track that is ALREADY playing in
     * EnhancedMusicPlayerManager (same videoId). In that case, the currentTrack
     * StateFlow doesn't re-emit, so fetchLyrics is never triggered automatically.
     *
     * - If lyrics are already loaded for this track, does nothing (cache hit).
     * - Otherwise fetches lyrics as normal.
     */
    fun ensureLyricsLoaded(track: MusicTrack) {
        val state = _uiState.value
        if (state.isLyricsLoading) return
        if (!state.syncedLyrics.isNullOrEmpty()) return
        if (!state.lyrics.isNullOrEmpty()) return
        fetchLyrics(track.videoId, track.artist, track.title, track.duration)
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
    val syncedLyrics: List<LyricsEntry> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val playingFrom: String = "Unknown Source",
    val autoplayEnabled: Boolean = true,
    val selectedFilter: String = "All",
    val relatedContent: List<MusicTrack> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet()
)

