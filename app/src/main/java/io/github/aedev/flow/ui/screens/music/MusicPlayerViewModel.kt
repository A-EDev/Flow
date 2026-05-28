package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import java.util.UUID
import java.util.Locale
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.utils.PerformanceDispatcher
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
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.flow.first
import kotlin.math.abs

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
    private val localPlaylistRepository: io.github.aedev.flow.data.local.PlaylistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    
    private val playerPreferences = PlayerPreferences(context)
    private val lyricsHelper = LyricsHelper(context)
    
    private var isInitialized = false
    private var loadTrackJob: kotlinx.coroutines.Job? = null
    private var pendingSeekPosition: Long? = null
    private var pendingSeekStartedAtMs: Long = 0L

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
                val acceptedPosition = acceptedPlaybackPosition(playerState.position)
                _uiState.update { it.copy(
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    duration = playerState.duration,
                    currentPosition = acceptedPosition ?: it.currentPosition
                ) }
            }
        }
        
        viewModelScope.launch {
            EnhancedMusicPlayerManager.currentPosition.collect { position ->
                acceptedPlaybackPosition(position)?.let { acceptedPosition ->
                    _uiState.update { it.copy(currentPosition = acceptedPosition) }
                }
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
                    fetchLyrics(it.videoId, it.artist, it.title, it.duration, it.album)
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
            EnhancedMusicPlayerManager.automixItems.collect { automix ->
                _uiState.update {
                    it.copy(
                        autoplaySuggestions = automix,
                        isRelatedLoading = false
                    )
                }
            }
        }
            
        viewModelScope.launch {
            localPlaylistRepository.getMusicPlaylistsFlow().collect { playlistInfos ->
                val playlists = playlistInfos.map { info ->
                    io.github.aedev.flow.data.music.Playlist(
                        id = info.id,
                        name = info.name,
                        description = info.description,
                        tracks = emptyList(), 
                        createdAt = info.createdAt,
                        thumbnailUrl = info.thumbnailUrl,
                        customTrackCount = info.videoCount
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
        loadTrackJob = viewModelScope.launch {
            val finalSourceName = resolveSourceName(sourceName, track)
            val activeQueue = if (queue.isNotEmpty()) queue else listOf(track)
            val localUriOverrides = withContext(PerformanceDispatcher.diskIO) {
                activeQueue.mapNotNull { queuedTrack ->
                    val path = downloadManager.getDownloadedTrackPath(queuedTrack.videoId) ?: return@mapNotNull null
                    val uri = if (path.startsWith("content://")) {
                        Uri.parse(path)
                    } else {
                        Uri.fromFile(java.io.File(path))
                    }
                    queuedTrack.videoId to uri
                }.toMap()
            }

            // ─── PHASE 1: Instant start ───────────────────────────────────────────
            _uiState.update { it.copy(
                currentTrack = track,
                isLoading = true,
                error = null,
                playingFrom = finalSourceName,
                selectedFilter = FILTER_ALL
            ) }

            withContext(kotlinx.coroutines.Dispatchers.Main) {
                EnhancedMusicPlayerManager.playTrack(
                    track = track,
                    audioUrl = "music://${track.videoId}",
                    queue = activeQueue,
                    sourceName = finalSourceName,
                    localUriOverrides = localUriOverrides
                )
            }

            // Player is now buffering — clear loading indicator so artwork etc. show
            _uiState.update { it.copy(isLoading = false) }

            // ─── PHASE 2: Background — does NOT block audio ───────────────────────
            supervisorScope {
                launch(PerformanceDispatcher.networkIO) {
                    if (!localUriOverrides.containsKey(track.videoId) && !downloadManager.isCachedForOffline(track.videoId)) {
                        EnhancedMusicPlayerManager.resolveStreamUrl(track.videoId)
                    }
                }

                launch(PerformanceDispatcher.diskIO) {
                    playlistRepository.addToHistory(track)
                    viewHistory.savePlaybackPosition(
                        videoId    = track.videoId,
                        position   = 0,
                        duration   = track.duration.toLong() * 1000,
                        title      = track.title,
                        thumbnailUrl = track.thumbnailUrl,
                        channelName = track.artist,
                        channelId  = "",
                        isMusic    = true
                    )
                }

                launch(PerformanceDispatcher.networkIO) {
                    fetchRelatedContent(track.videoId)
                }

                if (queue.size <= 1) {
                    launch(PerformanceDispatcher.networkIO) {
                        val relatedTracks = withTimeoutOrNull(8_000L) {
                            YouTubeMusicService.getRelatedMusic(track.videoId, 20)
                        } ?: emptyList()

                        if (relatedTracks.isNotEmpty()) {
                            EnhancedMusicPlayerManager.updateAutomixItems(relatedTracks)
                        }
                    }
                }
            }
        }
    }

    private fun resolveSourceName(sourceName: String?, track: MusicTrack): String {
        val trimmed = sourceName?.trim().orEmpty()
        if (trimmed.isBlank()) {
            return context.getString(R.string.radio_source_template, track.artist)
        }

        val key = trimmed.lowercase(Locale.getDefault())
        val mapped = when (key) {
            "listen_again" -> context.getString(R.string.section_listen_again)
            "daily_discover" -> context.getString(R.string.section_daily_discover)
            "quick_picks" -> context.getString(R.string.section_quick_picks)
            "speed_dial", "speed_dial_shuffle" -> context.getString(R.string.section_speed_dial)
            "recommended" -> context.getString(R.string.section_recommended)
            "recently_played" -> context.getString(R.string.section_recently_played)
            "music_videos" -> context.getString(R.string.section_music_videos)
            "music_videos_for_you" -> context.getString(R.string.section_music_videos_for_you)
            "live_performances" -> context.getString(R.string.section_live_performances)
            "new_releases" -> context.getString(R.string.section_new_releases)
            "popular_artists" -> context.getString(R.string.section_popular_artists)
            "mixed_for_you" -> context.getString(R.string.section_mixed_for_you)
            "moods_and_genres" -> context.getString(R.string.section_moods_and_genres)
            "mood_and_genres" -> context.getString(R.string.section_mood_and_genres)
            "from_the_community" -> context.getString(R.string.section_from_the_community)
            "top_albums" -> context.getString(R.string.section_top_albums)
            "top_picks" -> context.getString(R.string.top_picks_for_you)
            "trending" -> context.getString(R.string.trending)
            else -> null
        }

        if (mapped != null) return mapped

        if (key.startsWith("genre_")) {
            val genre = trimmed.substringAfter("genre_", "").replace('_', ' ').trim()
            if (genre.isNotBlank()) return genre
        }

        return CleanSource(trimmed)
    }

    private fun CleanSource(value: String): String {
        return value
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
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
        _uiState.update { it.copy(selectedFilter = filter, isRelatedLoading = true) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val freshRelated = withTimeoutOrNull(10_000L) {
                    YouTubeMusicService.getRelatedMusic(currentTrack.videoId, 25)
                } ?: emptyList()
                
                val filteredList = when (filter) {
                    FILTER_DISCOVER -> freshRelated.shuffled().take(20)
                    FILTER_POPULAR -> freshRelated.sortedByDescending { it.duration }.take(20)
                    FILTER_DEEP_CUTS -> freshRelated.reversed().take(20)
                    FILTER_WORKOUT -> freshRelated.filter {
                        it.title.contains("remix", ignoreCase = true) ||
                            it.title.contains("workout", ignoreCase = true) ||
                            it.title.contains("mix", ignoreCase = true)
                    }.ifEmpty { freshRelated.shuffled() }.take(20)
                    else -> freshRelated
                }
                
                _uiState.update { it.copy(
                    autoplaySuggestions = filteredList,
                    isRelatedLoading = false
                ) }
                
                EnhancedMusicPlayerManager.updateAutomixItems(filteredList)
            } catch (e: Exception) {
                _uiState.update { it.copy(isRelatedLoading = false) }
            }
        }
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        EnhancedMusicPlayerManager.moveMediaItem(fromIndex, toIndex)
    }

    fun seekTo(position: Long) {
        val duration = _uiState.value.duration.takeIf { it > 0 }
            ?: EnhancedMusicPlayerManager.getDuration().takeIf { it > 0 }
        val target = duration?.let { position.coerceIn(0L, it) } ?: position.coerceAtLeast(0L)
        pendingSeekPosition = target
        pendingSeekStartedAtMs = SystemClock.elapsedRealtime()
        EnhancedMusicPlayerManager.seekTo(target)
        _uiState.update { it.copy(currentPosition = target) }
    }

    private fun acceptedPlaybackPosition(position: Long): Long? {
        val pending = pendingSeekPosition ?: return position
        val elapsedMs = SystemClock.elapsedRealtime() - pendingSeekStartedAtMs
        val seekHasLanded = abs(position - pending) <= SEEK_POSITION_CONFIRM_TOLERANCE_MS
        val guardExpired = elapsedMs >= SEEK_POSITION_GUARD_MS

        if (seekHasLanded) {
            if (elapsedMs >= SEEK_POSITION_MIN_HOLD_MS) {
                pendingSeekPosition = null
            }
            return position
        }

        if (guardExpired) {
            pendingSeekPosition = null
            return position
        }

        return null
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
                EnhancedMusicPlayerManager.updateAutomixItems(related)
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
            Toast.makeText(context, context.getString(R.string.added_to_playlist_toast), Toast.LENGTH_SHORT).show()
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
        EnhancedMusicPlayerManager.removeAutomixItem(track.videoId)
        Toast.makeText(context, context.getString(R.string.play_next_toast), Toast.LENGTH_SHORT).show()
    }

    fun addToQueue(track: MusicTrack) {
        EnhancedMusicPlayerManager.addToQueue(track)
        EnhancedMusicPlayerManager.removeAutomixItem(track.videoId)
        Toast.makeText(context, context.getString(R.string.added_to_queue_toast), Toast.LENGTH_SHORT).show()
    }
    
    fun downloadTrack(track: MusicTrack? = null) {
        val trackToDownload = track ?: _uiState.value.currentTrack ?: return
        
        if (_uiState.value.downloadedTrackIds.contains(trackToDownload.videoId)) {
             viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                 Toast.makeText(context, context.getString(R.string.already_downloaded_toast), Toast.LENGTH_SHORT).show()
             }
             return
        }

        viewModelScope.launch {
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.download_started_toast), Toast.LENGTH_SHORT).show()
            }
            
            try {
                downloadManager.downloadTrack(trackToDownload)
                
            } catch (e: Exception) {
                android.util.Log.e("MusicDownload", "Download start exception", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.download_error_toast, e.message ?: ""), Toast.LENGTH_SHORT).show()
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

    fun fetchLyrics(videoId: String, artist: String, title: String, duration: Int? = null, album: String? = null) {
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch {
            _uiState.update { it.copy(
                isLyricsLoading = true,
                lyrics = null,
                syncedLyrics = emptyList(),
                lyricsProviderName = ""
            ) }
            
            val cleanArtist = cleanName(artist)
            val cleanTitle = cleanName(title)
            val targetDuration = duration ?: (_uiState.value.duration.toInt() / 1000)

            try {
                val result = lyricsHelper.getLyrics(videoId, cleanTitle, cleanArtist, targetDuration, album)

                if (result != null) {
                    val (entries, providerName) = result
                    val hasWords = entries.any { it.words != null }
                    val isSynced = lyricsHelper.entriesAreSynced(entries)
                    android.util.Log.d(
                        "MusicPlayerViewModel",
                        "Got ${entries.size} lyrics lines from $providerName (word-sync=$hasWords, synced=$isSynced)"
                    )

                    val plainText = entries.joinToString("\n") { it.text }
                    _uiState.update { it.copy(
                        isLyricsLoading = false,
                        lyrics = plainText.takeIf { it.isNotBlank() },
                        syncedLyrics = if (isSynced) entries else emptyList(),
                        lyricsProviderName = providerName
                    ) }
                } else {
                    _uiState.update { it.copy(isLyricsLoading = false) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        fetchLyrics(track.videoId, track.artist, track.title, track.duration, track.album)
    }

    fun refreshLyrics() {
        val track = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            try {
                lyricsHelper.forceRefresh(track.videoId)
            } catch (e: Exception) {
                android.util.Log.w("MusicPlayerViewModel", "forceRefresh failed: ${e.message}")
            }
            fetchLyrics(track.videoId, track.artist, track.title, track.duration, track.album)
        }
    }

    fun updateProgress() {
        val position = EnhancedMusicPlayerManager.getCurrentPosition()
        val duration = EnhancedMusicPlayerManager.getDuration()
        val acceptedPosition = acceptedPlaybackPosition(position)
        
        _uiState.update { it.copy(
            currentPosition = acceptedPosition ?: it.currentPosition,
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
    val playlists: List<io.github.aedev.flow.data.music.Playlist> = emptyList(),
    val showAddToPlaylistDialog: Boolean = false,
    val showCreatePlaylistDialog: Boolean = false,
    val lyrics: String? = null,
    val syncedLyrics: List<LyricsEntry> = emptyList(),
    val isLyricsLoading: Boolean = false,
    val playingFrom: String = "",
    val autoplayEnabled: Boolean = true,
    val selectedFilter: String = FILTER_ALL,
    val relatedContent: List<MusicTrack> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val lyricsProviderName: String = ""
)

const val FILTER_ALL = "ALL"
const val FILTER_DISCOVER = "DISCOVER"
const val FILTER_POPULAR = "POPULAR"
const val FILTER_DEEP_CUTS = "DEEP_CUTS"
const val FILTER_WORKOUT = "WORKOUT"

private const val SEEK_POSITION_CONFIRM_TOLERANCE_MS = 1_000L
private const val SEEK_POSITION_MIN_HOLD_MS = 250L
private const val SEEK_POSITION_GUARD_MS = 1_500L

