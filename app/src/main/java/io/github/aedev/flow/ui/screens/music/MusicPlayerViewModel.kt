package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LYRICS_ALIGN_CENTER
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.lyrics.LyricsCandidate
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MusicPlayerViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playlistRepository: PlaylistRepository,
        private val downloadManager: DownloadManager,
        private val likedVideosRepository: LikedVideosRepository,
        private val viewHistory: ViewHistory,
        private val localPlaylistRepository: io.github.aedev.flow.data.local.PlaylistRepository,
        private val musicBrain: MusicBrainEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MusicPlayerUiState())
        val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()

        /**
         * Playback position is kept out of [MusicPlayerUiState] on purpose. It changes several times a
         * second, and folding it into the screen state made every position tick emit a fresh copy of a
         * 25-field object — invalidating the whole player screen to move a seek bar.
         */
        private val _currentPositionMs = MutableStateFlow(0L)
        val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

        private val lyricsHelper = LyricsHelper(context)
        private val playerPreferences = PlayerPreferences(context)

        private var isInitialized = false
        private var loadTrackJob: kotlinx.coroutines.Job? = null
        private var pendingSeekPosition: Long? = null
        private var pendingSeekStartedAtMs: Long = 0L

        init {
            EnhancedMusicPlayerManager.initialize(context)
            initializeObservers()
            viewModelScope.launch {
                playerPreferences.lyricsTextAlign.collect { align ->
                    _uiState.update { it.copy(lyricsTextAlign = align) }
                }
            }
            viewModelScope.launch {
                playerPreferences.musicEndlessRadioEnabled.collect { enabled ->
                    _uiState.update { it.copy(endlessRadioEnabled = enabled) }
                }
            }
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
                    _uiState.update {
                        it.copy(
                            isPlaying = playerState.isPlaying,
                            isBuffering = playerState.isBuffering,
                            duration = playerState.duration,
                        )
                    }
                }
            }

            viewModelScope.launch {
                EnhancedMusicPlayerManager.currentPosition.collect { position ->
                    acceptedPlaybackPosition(position)?.let { acceptedPosition ->
                        _currentPositionMs.value = acceptedPosition
                    }
                }
            }

            viewModelScope.launch {
                EnhancedMusicPlayerManager.currentTrack.collect { track ->
                    _uiState.update {
                        it.copy(
                            currentTrack = track,
                            lyrics = null,
                            syncedLyrics = emptyList(),
                            // Fix: Reset duration and position to prevent showing previous track's info
                            duration = if (track != null) track.duration * 1000L else 0L,
                        )
                    }
                    _currentPositionMs.value = 0L
                    track?.let {
                        if (!isLocalMediaId(it.videoId)) {
                            checkIfFavorite(it.videoId)
                            fetchLyrics(it.videoId, it.artist, it.title, it.duration, it.album)
                            fetchRelatedContent(it.videoId)
                        }
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
                            isRelatedLoading = false,
                        )
                    }
                }
            }

            viewModelScope.launch {
                localPlaylistRepository.getMusicPlaylistsFlow().collect { playlistInfos ->
                    val playlists =
                        playlistInfos.map { info ->
                            io.github.aedev.flow.data.music.Playlist(
                                id = info.id,
                                name = info.name,
                                description = info.description,
                                tracks = emptyList(),
                                createdAt = info.createdAt,
                                thumbnailUrl = info.thumbnailUrl,
                                customTrackCount = info.videoCount,
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

        fun playLocalMusic(
            track: MusicTrack,
            queue: List<MusicTrack>,
            localUris: Map<String, Uri>,
        ) {
            loadTrackJob?.cancel()
            loadTrackJob =
                viewModelScope.launch {
                    val activeQueue = if (queue.isNotEmpty()) queue else listOf(track)
                    _uiState.update {
                        it.copy(
                            currentTrack = track,
                            isLoading = false,
                            error = null,
                            playingFrom = context.getString(R.string.local_media_title),
                        )
                    }
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        EnhancedMusicPlayerManager.playTrack(
                            track = track,
                            audioUrl = localUris[track.videoId]?.toString() ?: "",
                            queue = activeQueue,
                            sourceName = context.getString(R.string.local_media_title),
                            localUriOverrides = localUris,
                        )
                    }
                    launch(PerformanceDispatcher.diskIO) {
                        viewHistory.savePlaybackPosition(
                            videoId = track.videoId,
                            position = 0,
                            duration = track.duration.toLong() * 1000,
                            title = track.title,
                            thumbnailUrl = track.thumbnailUrl,
                            channelName = track.artist,
                            channelId = track.channelId,
                            isMusic = true,
                            isLocal = true,
                        )
                    }
                }
        }

        private fun isLocalMediaId(id: String?): Boolean = id?.startsWith("local_") == true

        fun loadAndPlayTrack(
            track: MusicTrack,
            queue: List<MusicTrack> = emptyList(),
            sourceName: String? = null,
        ) {
            loadTrackJob?.cancel()
            // Genre-scoped surfaces tag their source; the genre becomes listen
            // context for this queue and is stripped from the display label.
            // Any non-tagged queue start clears the previous context.
            val contextGenre =
                sourceName
                    ?.trim()
                    ?.takeIf { it.startsWith(MUSIC_GENRE_SOURCE_PREFIX) }
                    ?.removePrefix(MUSIC_GENRE_SOURCE_PREFIX)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            EnhancedMusicPlayerManager.playContextGenre = contextGenre
            val displaySourceName = contextGenre ?: sourceName
            loadTrackJob =
                viewModelScope.launch {
                    val finalSourceName = resolveSourceName(displaySourceName, track)
                    val activeQueue = if (queue.isNotEmpty()) queue else listOf(track)
                    val localUriOverrides =
                        withContext(PerformanceDispatcher.diskIO) {
                            activeQueue
                                .mapNotNull { queuedTrack ->
                                    val path = downloadManager.getDownloadedTrackPath(queuedTrack.videoId) ?: return@mapNotNull null
                                    val uri =
                                        if (path.startsWith("content://")) {
                                            Uri.parse(path)
                                        } else {
                                            Uri.fromFile(java.io.File(path))
                                        }
                                    queuedTrack.videoId to uri
                                }.toMap()
                        }

                    // ─── PHASE 1: Instant start ───────────────────────────────────────────
                    _uiState.update {
                        it.copy(
                            currentTrack = track,
                            isLoading = true,
                            error = null,
                            playingFrom = finalSourceName,
                        )
                    }

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        EnhancedMusicPlayerManager.playTrack(
                            track = track,
                            audioUrl = "music://${track.videoId}",
                            queue = activeQueue,
                            sourceName = finalSourceName,
                            localUriOverrides = localUriOverrides,
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
                                videoId = track.videoId,
                                position = 0,
                                duration = track.duration.toLong() * 1000,
                                title = track.title,
                                thumbnailUrl = track.thumbnailUrl,
                                channelName = track.artist,
                                channelId = track.channelId,
                                isMusic = true,
                            )
                        }

                        launch(PerformanceDispatcher.networkIO) {
                            fetchRelatedContent(track.videoId)
                        }
                        // Single-track queues need no special automix fill: the service
                        // seeds the radio pool for every new queue context.
                    }
                }
        }

        private fun resolveSourceName(
            sourceName: String?,
            track: MusicTrack,
        ): String {
            val trimmed = sourceName?.trim().orEmpty()
            if (trimmed.isBlank()) {
                return context.getString(R.string.radio_source_template, track.artist)
            }

            val key = trimmed.lowercase(Locale.getDefault())
            val mapped =
                when (key) {
                    "listen_again" -> context.getString(R.string.section_listen_again)
                    "on_repeat" -> context.getString(R.string.section_on_repeat)
                    "rotation" -> context.getString(R.string.source_your_rotation)
                    "rediscover" -> context.getString(R.string.section_rediscover)
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

            return cleanSource(trimmed)
        }

        private fun cleanSource(value: String): String =
            value
                .replace('_', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
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

        fun moveTrack(
            fromIndex: Int,
            toIndex: Int,
        ) {
            EnhancedMusicPlayerManager.moveMediaItem(fromIndex, toIndex)
        }

        fun playNextFromQueuePosition(index: Int) {
            val current = _uiState.value.currentQueueIndex
            if (index == current) return
            val target = if (index > current) current + 1 else current
            if (index != target) EnhancedMusicPlayerManager.moveMediaItem(index, target)
        }

        fun moveQueueTrackToEnd(index: Int) {
            val lastIndex = _uiState.value.queue.size - 1
            if (index in 0 until lastIndex) EnhancedMusicPlayerManager.moveMediaItem(index, lastIndex)
        }

        fun playNextFromRadio(track: MusicTrack) {
            EnhancedMusicPlayerManager.playNext(track)
            EnhancedMusicPlayerManager.removeAutomixItem(track.videoId)
        }

        fun addRadioTrackToQueue(track: MusicTrack) {
            EnhancedMusicPlayerManager.addToQueue(track)
            EnhancedMusicPlayerManager.removeAutomixItem(track.videoId)
        }

        fun setEndlessRadioEnabled(enabled: Boolean) {
            viewModelScope.launch { playerPreferences.setMusicEndlessRadioEnabled(enabled) }
        }

        fun seekTo(position: Long) {
            val duration =
                _uiState.value.duration.takeIf { it > 0 }
                    ?: EnhancedMusicPlayerManager.getDuration().takeIf { it > 0 }
            val target = duration?.let { position.coerceIn(0L, it) } ?: position.coerceAtLeast(0L)
            pendingSeekPosition = target
            pendingSeekStartedAtMs = SystemClock.elapsedRealtime()
            EnhancedMusicPlayerManager.seekTo(target)
            _currentPositionMs.value = target
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

        // Both the currentTrack collector and the (kept-composed) player content request
        // related tracks for the same id; without this guard every advance fetched twice.
        private var relatedFetchedForId: String? = null

        fun fetchRelatedContent(videoId: String) {
            if (relatedFetchedForId == videoId) return
            relatedFetchedForId = videoId
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isRelatedLoading = true) }
                try {
                    val related =
                        withTimeoutOrNull(10_000L) {
                            YouTubeMusicService.getRelatedMusic(videoId, 20)
                        } ?: emptyList()
                    if (related.isEmpty()) relatedFetchedForId = null

                    // Related content is display-only here: the radio pool (automix)
                    // is owned by Media3MusicService and must not churn per track.
                    _uiState.update {
                        it.copy(
                            relatedContent = related,
                            isRelatedLoading = false,
                        )
                    }
                } catch (e: Exception) {
                    relatedFetchedForId = null
                    _uiState.update { it.copy(isRelatedLoading = false) }
                }
            }
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
                            isMusic = true,
                        ),
                    )
                    musicBrain.onExplicitLike(currentTrack)
                } else {
                    likedVideosRepository.removeLikeState(currentTrack.videoId)
                }
            }
        }

        /**
         * "Not interested": soft-suppresses the track's artist for two weeks. A
         * second one while still suppressed escalates to a permanent block —
         * mirrored from the desktop two-layer feedback system.
         */
        fun notInterested(track: MusicTrack) {
            val primary = track.artists.firstOrNull()
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                musicBrain.dislikeArtist(
                    primary?.id ?: track.channelId.takeIf { it.isNotBlank() },
                    primary?.name ?: track.artist,
                )
            }
        }

        /** "Don't recommend {artist}": an immediate permanent hard block, reversible in settings. */
        fun dontRecommendArtist(track: MusicTrack) {
            val primary = track.artists.firstOrNull()
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                musicBrain.blockArtist(
                    primary?.id ?: track.channelId.takeIf { it.isNotBlank() },
                    primary?.name ?: track.artist,
                )
            }
        }

        fun addToPlaylist(
            playlistId: String,
            track: MusicTrack? = null,
        ) {
            val trackToAdd = track ?: _uiState.value.currentTrack ?: return

            viewModelScope.launch {
                val video =
                    Video(
                        id = trackToAdd.videoId,
                        title = trackToAdd.title,
                        channelName = trackToAdd.artist,
                        channelId = trackToAdd.channelId,
                        thumbnailUrl = trackToAdd.thumbnailUrl,
                        duration = trackToAdd.duration,
                        viewCount = 0,
                        uploadDate = "",
                        timestamp = System.currentTimeMillis(),
                        description = trackToAdd.album,
                        isMusic = true,
                    )
                localPlaylistRepository.addVideoToPlaylist(playlistId, video)
                Toast.makeText(context, context.getString(R.string.added_to_playlist_toast), Toast.LENGTH_SHORT).show()
            }
        }

        fun createPlaylist(
            name: String,
            description: String = "",
            track: MusicTrack? = null,
        ) {
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
                        val msg = context.getString(R.string.download_error_toast, e.message ?: "")
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        private var lyricsJob: kotlinx.coroutines.Job? = null

        private fun cleanName(name: String): String =
            name
                .replace(Regex("(?i)\\s*-\\s*topic$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)\\s*[(\\[]official (audio|video|music video|lyric video)[)\\]]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)\\s*[(\\[]lyrics?[)\\]]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)\\s*[(]feat\\.? .*?[)]", RegexOption.IGNORE_CASE), "")
                .replace(Regex("(?i)\\s*[\\[]feat\\.? .*?[\\]]", RegexOption.IGNORE_CASE), "")
                .trim()

        fun fetchLyrics(
            videoId: String,
            artist: String,
            title: String,
            duration: Int? = null,
            album: String? = null,
        ) {
            lyricsJob?.cancel()
            lyricsJob =
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            isLyricsLoading = true,
                            lyrics = null,
                            syncedLyrics = emptyList(),
                            lyricsProviderName = "",
                            lyricsSyncOffsetMs = 0L,
                            lyricsCandidates = emptyList(),
                        )
                    }

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
                                "Got ${entries.size} lyrics lines from $providerName (word-sync=$hasWords, synced=$isSynced)",
                            )

                            val plainText = entries.joinToString("\n") { it.text }
                            _uiState.update {
                                it.copy(
                                    isLyricsLoading = false,
                                    lyrics = plainText.takeIf { it.isNotBlank() },
                                    syncedLyrics = if (isSynced) entries else emptyList(),
                                    lyricsProviderName = providerName,
                                )
                            }
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
            fetchLyrics(
                videoId = track.videoId,
                artist = track.artist,
                title = track.title,
                duration = track.duration,
                album = track.album,
            )
        }

        fun refreshLyrics() {
            val track = _uiState.value.currentTrack ?: return
            viewModelScope.launch {
                try {
                    lyricsHelper.forceRefresh(track.videoId)
                } catch (e: Exception) {
                    android.util.Log.w("MusicPlayerViewModel", "forceRefresh failed: ${e.message}")
                }
                fetchLyrics(
                    videoId = track.videoId,
                    artist = track.artist,
                    title = track.title,
                    duration = track.duration,
                    album = track.album,
                )
            }
        }

        private var browseLyricsJob: kotlinx.coroutines.Job? = null

        fun browseLyricsCandidates() {
            val track = _uiState.value.currentTrack ?: return
            browseLyricsJob?.cancel()
            browseLyricsJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isBrowsingLyrics = true, lyricsCandidates = emptyList()) }
                    try {
                        lyricsHelper.getAllLyrics(
                            videoId = track.videoId,
                            title = cleanName(track.title),
                            artist = cleanName(track.artist),
                            duration = track.duration,
                            album = track.album,
                        ) { candidate ->
                            _uiState.update { it.copy(lyricsCandidates = it.lyricsCandidates + candidate) }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.w("MusicPlayerViewModel", "Lyrics browse failed: ${e.message}")
                    } finally {
                        _uiState.update { it.copy(isBrowsingLyrics = false) }
                    }
                }
        }

        fun cancelLyricsBrowse() {
            browseLyricsJob?.cancel()
            _uiState.update { it.copy(isBrowsingLyrics = false) }
        }

        fun applyLyricsCandidate(candidate: LyricsCandidate) {
            val track = _uiState.value.currentTrack ?: return
            viewModelScope.launch {
                lyricsHelper.applyManualLyrics(track.videoId, candidate.entries)
                val plainText = candidate.entries.joinToString("\n") { it.text }
                _uiState.update {
                    it.copy(
                        lyrics = plainText.takeIf { text -> text.isNotBlank() },
                        syncedLyrics = if (candidate.synced) candidate.entries else emptyList(),
                        lyricsProviderName = candidate.providerName,
                        lyricsSyncOffsetMs = 0L,
                    )
                }
            }
        }

        fun applyEditedLyrics(text: String) {
            val track = _uiState.value.currentTrack ?: return
            viewModelScope.launch {
                val parsed =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        io.github.aedev.flow.data.lyrics.LyricsUtils
                            .parseLyrics(text)
                    }
                val entries =
                    parsed.ifEmpty {
                        text
                            .lines()
                            .map { line -> line.trim() }
                            .filter { line -> line.isNotBlank() }
                            .map { line -> LyricsEntry(0L, line) }
                    }
                if (entries.isEmpty()) return@launch
                val synced = lyricsHelper.entriesAreSynced(entries)
                lyricsHelper.applyManualLyrics(track.videoId, entries)
                val plainText = entries.joinToString("\n") { it.text }
                _uiState.update {
                    it.copy(
                        lyrics = plainText.takeIf { t -> t.isNotBlank() },
                        syncedLyrics = if (synced) entries else emptyList(),
                        lyricsProviderName = context.getString(io.github.aedev.flow.R.string.lyrics_source_edited),
                    )
                }
            }
        }

        fun adjustLyricsSyncOffset(deltaMs: Long) {
            _uiState.update {
                it.copy(lyricsSyncOffsetMs = (it.lyricsSyncOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L))
            }
        }

        fun resetLyricsSyncOffset() {
            _uiState.update { it.copy(lyricsSyncOffsetMs = 0L) }
        }

        fun setLyricsTextAlign(align: String) {
            viewModelScope.launch { playerPreferences.setLyricsTextAlign(align) }
        }

        override fun onCleared() {
            super.onCleared()
        }
    }

data class MusicPlayerUiState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
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
    val endlessRadioEnabled: Boolean = true,
    val relatedContent: List<MusicTrack> = emptyList(),
    val isRelatedLoading: Boolean = false,
    val downloadedTrackIds: Set<String> = emptySet(),
    val lyricsProviderName: String = "",
    val lyricsSyncOffsetMs: Long = 0L,
    val lyricsTextAlign: String = LYRICS_ALIGN_CENTER,
    val lyricsCandidates: List<LyricsCandidate> = emptyList(),
    val isBrowsingLyrics: Boolean = false,
)

private const val SEEK_POSITION_CONFIRM_TOLERANCE_MS = 1_000L
private const val SEEK_POSITION_MIN_HOLD_MS = 250L
private const val SEEK_POSITION_GUARD_MS = 1_500L
