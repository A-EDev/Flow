package io.github.aedev.flow.ui.screens.music

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.MusicCache
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.data.recommendation.MusicRecommendationAlgorithm
import io.github.aedev.flow.data.recommendation.MusicSection
import io.github.aedev.flow.data.recommendation.music.MusicQuickPicks
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.BrowseEndpoint
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.pages.ArtistItemsPage
import io.github.aedev.flow.innertube.pages.HomePage
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class MusicViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val musicRecommendationAlgorithm: MusicRecommendationAlgorithm,
        private val subscriptionRepository: io.github.aedev.flow.data.local.SubscriptionRepository,
        private val playlistRepository: io.github.aedev.flow.data.music.PlaylistRepository,
        private val localPlaylistRepository: io.github.aedev.flow.data.local.PlaylistRepository,
        private val downloadManager: DownloadManager,
        private val musicBrain: io.github.aedev.flow.data.recommendation.music.MusicBrainEngine,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MusicUiState())
        val uiState: StateFlow<MusicUiState> =
            _uiState
                .map(MusicUiState::withUniqueLazyContent)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = _uiState.value.withUniqueLazyContent(),
                )

        private fun MusicTrack.isAudioMusicCandidate(): Boolean {
            val usableDuration = duration == 0 || duration in 30..1200
            return itemType == MusicItemType.SONG && !isVideoSong && videoId.isNotBlank() && usableDuration
        }

        private fun List<MusicTrack>.audioMusicOnly(): List<MusicTrack> = filter { it.isAudioMusicCandidate() }.distinctBy { it.videoId }

        init {
            loadMusicContent()

            viewModelScope.launch {
                downloadManager.downloadedTracks.collect { tracks ->
                    _uiState.update { state ->
                        state.copy(downloadedTrackIds = tracks.map { it.track.videoId }.toSet())
                    }
                }
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                var lastTrackId: String? = null
                EnhancedMusicPlayerManager.currentTrack.collectLatest { activeTrack ->
                    if (activeTrack != null && !activeTrack.videoId.isNullOrBlank()) {
                        if (activeTrack.videoId != lastTrackId) {
                            lastTrackId = activeTrack.videoId
                            rebuildQuickPicks(activeTrack)
                            refreshOnRepeat()
                        }
                    }
                }
            }
        }

        /** True once the multi-lane composer has produced a shelf — YT-home and history fallbacks must not overwrite it. */
        @Volatile
        private var quickPicksComposed = false

        /**
         * Desktop-style Quick Picks: one related lane per seed (current track +
         * distinct-artist history) ranked on the comfort surface, plus a charts
         * discovery lane, round-robin interleaved so fresh content always lands.
         */
        private suspend fun rebuildQuickPicks(current: MusicTrack?) {
            try {
                val history =
                    playlistRepository.history
                        .firstOrNull()
                        .orEmpty()
                        .audioMusicOnly()
                val seeds = MusicQuickPicks.selectSeeds(current, history)
                val relatedLanes =
                    kotlinx.coroutines.coroutineScope {
                        seeds
                            .map { seed ->
                                async(PerformanceDispatcher.networkIO) {
                                    runCatching {
                                        YouTubeMusicService
                                            .getRelatedMusic(seed.videoId, MusicQuickPicks.LANE_SIZE, audioOnly = true)
                                            .audioMusicOnly()
                                    }.onFailure { Log.w("MusicViewModel", "Quick Picks lane '${seed.title}' failed: $it") }
                                        .getOrDefault(emptyList())
                                }
                            }.awaitAll()
                    }
                val personalizedLanes =
                    relatedLanes
                        .filter { it.isNotEmpty() }
                        .map { musicBrain.rankTracks(it, "quick_picks") }
                val discoveryLane =
                    _uiState.value.trendingSongs
                        .audioMusicOnly()
                        .takeIf { it.isNotEmpty() }
                        ?.let { musicBrain.rankTracks(it, "discover") }
                val lanes = personalizedLanes + listOfNotNull(discoveryLane)
                if (lanes.isEmpty()) return

                val excluded = seeds.map { it.videoId }.toSet()
                val mixed = MusicQuickPicks.interleave(lanes, MusicQuickPicks.TARGET, excluded)
                Log.d(
                    "MusicViewModel",
                    "Quick Picks lanes=[${relatedLanes.joinToString { it.size.toString() }}] " +
                        "charts=${discoveryLane.orEmpty().size} mixed=${mixed.size}",
                )
                if (mixed.size >= 4) {
                    quickPicksComposed = true
                    _uiState.update { it.copy(forYouTracks = mixed) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error composing Quick Picks", e)
            }
        }

        private suspend fun refreshOnRepeat() {
            try {
                val onRepeat = musicBrain.heavyRotationTracks(16).audioMusicOnly()
                if (onRepeat.size >= 2) {
                    _uiState.update { it.copy(onRepeatTracks = onRepeat) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading On Repeat", e)
            }
        }

        /**
         *  PERFORMANCE OPTIMIZED: Load all music content progressively
         *  Each section loads independently to show content as fast as possible
         */
        private fun loadMusicContent(force: Boolean = false) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val cachedTrending = MusicCache.getTrendingMusic(100)
                val cachedResult =
                    try {
                        musicRecommendationAlgorithm.loadMusicHome()
                    } catch (e: Exception) {
                        emptyList<MusicSection>() to null
                    }

                val cachedSections = cachedResult.first

                if (cachedTrending != null || cachedSections.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        // Apply cached data immediately
                        if (cachedSections.isNotEmpty()) {
                            processHomeSections(cachedSections)
                            cachedResult.second?.let { continuation ->
                                _uiState.update { it.copy(homeContinuation = continuation) }
                            }
                        }

                        cachedTrending?.let { trend ->
                            _uiState.update {
                                it.copy(
                                    trendingSongs = trend,
                                    allSongs = if (it.selectedFilter == null) trend else it.allSongs,
                                )
                            }
                        }

                        _uiState.update { it.copy(isLoading = false) }
                    }
                } else {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
            }

            // On Repeat — served entirely from the local music brain, zero network.
            // Watch history holds one row per track, so backfill cannot seed relistens;
            // the shelf earns items only from live sessions and refreshes per track change.
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                refreshOnRepeat()
            }

            // 1. CRITICAL: Trending / Charts (Fastest & Most Important)
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val trending =
                    withTimeoutOrNull(8_000L) {
                        try {
                            // Try to get charts first for high quality trending data
                            val charts = InnertubeMusicService.fetchCharts()
                            if (charts.isNotEmpty()) {
                                MusicCache.cacheTrendingMusic(100, charts)
                                charts
                            } else {
                                val trending = YouTubeMusicService.fetchTrendingMusic(100)
                                MusicCache.cacheTrendingMusic(100, trending)
                                trending
                            }
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading trending/charts", e)
                            null
                        }
                    }

                trending?.let { trend ->
                    _uiState.update {
                        it.copy(
                            trendingSongs = trend,
                            allSongs = if (it.selectedFilter == null) trend else it.allSongs,
                            isLoading = false,
                        )
                    }
                }

                // First composition of the multi-lane Quick Picks: seeds from the
                // current track (if any) and history, discovery lane from the charts.
                rebuildQuickPicks(EnhancedMusicPlayerManager.currentTrack.value)
            }

            // 2. IMPORTANT: Home Sections (Dynamic Content)
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                var skippedFreshCache = false
                val homeResult =
                    withTimeoutOrNull(10_000L) {
                        // Reduced timeout
                        try {
                            if (force) {
                                musicRecommendationAlgorithm.refreshMusicHome()
                            } else {
                                // Cache inside its 4 h TTL: the cached pass already rendered it.
                                musicRecommendationAlgorithm.refreshMusicHomeIfStale()
                                    ?: (emptyList<MusicSection>() to null).also { skippedFreshCache = true }
                            }
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error refreshing home sections", e)
                            emptyList<MusicSection>() to null
                        }
                    } ?: (emptyList<MusicSection>() to null)

                val homeSections = homeResult.first
                val homeContinuation = homeResult.second

                // Fetch Chips
                val homeChips = musicRecommendationAlgorithm.getHomeChips()
                _uiState.update { it.copy(homeChips = homeChips) }

                if (homeSections.isNotEmpty()) {
                    processHomeSections(homeSections)
                    _uiState.update { it.copy(homeContinuation = homeContinuation) }
                } else if (!skippedFreshCache && !quickPicksComposed &&
                    _uiState.value.forYouTracks.isEmpty() && _uiState.value.dynamicSections.isEmpty()
                ) {
                    val recs = musicRecommendationAlgorithm.getRecommendations(24).audioMusicOnly()
                    if (recs.isNotEmpty()) {
                        val ranked = musicBrain.rankTracks(recs, "quick_picks")
                        _uiState.update { it.copy(forYouTracks = ranked) }
                    }
                }
                if (_uiState.value.trendingSongs.isNotEmpty() || homeSections.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }

            // 3. SECONDARY: History (Disk IO)
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val history =
                    withTimeoutOrNull(5_000L) {
                        try {
                            playlistRepository.history.firstOrNull() ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    } ?: emptyList()

                if (history.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            history = history,
                            // Raw history is only an emergency placeholder — never over a composed shelf.
                            forYouTracks =
                                if (it.forYouTracks.isEmpty() && !quickPicksComposed) {
                                    history.audioMusicOnly().take(24)
                                } else {
                                    it.forYouTracks
                                },
                            isLoading = false,
                        )
                    }
                }
            }

            // 4. CONTENT: New Releases (Albums & Tracks)
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                withTimeoutOrNull(10_000L) {
                    try {
                        // Fetch Album Releases (New Feature)
                        val albums = InnertubeMusicService.fetchNewReleases()
                        if (albums.isNotEmpty()) {
                            _uiState.update { it.copy(topAlbums = albums) }
                        }

                        val newReleases = YouTubeMusicService.fetchNewReleases(40)
                        if (newReleases.isNotEmpty()) {
                            _uiState.update { it.copy(newReleases = newReleases) }
                        }
                        Unit
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error loading new releases", e)
                    }
                }
            }

            // 5. CONTENT: Moods & Genres (New Section)
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                withTimeoutOrNull(8_000L) {
                    try {
                        val moods = InnertubeMusicService.fetchMoodAndGenres()
                        if (moods.isNotEmpty()) {
                            _uiState.update { it.copy(moodsAndGenres = moods) }
                        }
                        Unit
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error loading moods", e)
                    }
                }
            }

            // 6. CONTENT: Featured Playlists
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                withTimeoutOrNull(12_000L) {
                    try {
                        val history =
                            try {
                                playlistRepository.history.firstOrNull() ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }

                        val query =
                            if (history.isNotEmpty()) {
                                val topArtists =
                                    history
                                        .groupBy { it.artist }
                                        .map { it.key to it.value.size }
                                        .sortedByDescending { it.second }
                                        .take(3)
                                        .map { it.first }
                                        .filter { !it.isNullOrBlank() }
                                        .shuffled()

                                val selectedArtist = topArtists.firstOrNull()
                                if (selectedArtist != null) {
                                    "$selectedArtist playlist"
                                } else {
                                    "curated music playlists 2026"
                                }
                            } else {
                                "curated music playlists 2026"
                            }

                        Log.d("MusicViewModel", "Personalized playlists query: $query")
                        val playlists = YouTubeMusicService.searchPlaylists(query, 10)
                        if (playlists.isNotEmpty()) {
                            _uiState.update { it.copy(featuredPlaylists = playlists) }
                        } else {
                            val fallback = YouTubeMusicService.searchPlaylists("curated music playlists 2026", 10)
                            if (fallback.isNotEmpty()) {
                                _uiState.update { it.copy(featuredPlaylists = fallback) }
                            }
                        }
                        Unit
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error loading playlists", e)
                    }
                }
            }

            // 7. BACKGROUND: Popular Artists & Genre Content
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                withTimeoutOrNull(12_000L) {
                    try {
                        val tracks = YouTubeMusicService.fetchPopularArtistMusic(50)
                        if (tracks.isNotEmpty()) {
                            MusicCache.cacheGenreTracks("Popular Artists", 50, tracks)
                            val currentGenreTracks = _uiState.value.genreTracks.toMutableMap()
                            currentGenreTracks["Popular Artists"] = tracks

                            val genres = YouTubeMusicService.getPopularGenres()
                            _uiState.update {
                                it.copy(
                                    genreTracks = currentGenreTracks,
                                    genres = listOf("Popular Artists") + genres,
                                )
                            }
                        }
                        Unit
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error loading popular artists", e)
                    }
                }

                // Load specific genres in background
                val genreList = listOf("Pop", "Rock", "Hip Hop", "R&B", "Electronic")
                val genreMap = mutableMapOf<String, List<MusicTrack>>()

                supervisorScope {
                    genreList
                        .map { genre ->
                            async(PerformanceDispatcher.networkIO) {
                                withTimeoutOrNull(8_000L) {
                                    try {
                                        val tracks = musicRecommendationAlgorithm.getGenreContent(genre)
                                        if (tracks.isNotEmpty()) {
                                            genre to tracks
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }
                        }.forEach { deferred ->
                            deferred.await()?.let { (genre, tracks) ->
                                genreMap[genre] = tracks
                            }
                        }
                }

                if (genreMap.isNotEmpty()) {
                    _uiState.update {
                        val updated = it.genreTracks.toMutableMap()
                        updated.putAll(genreMap)
                        it.copy(genreTracks = updated)
                    }
                }
            }

            // 8. BACKGROUND: Explore Page
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val explore = InnertubeMusicService.fetchExplore()
                if (explore != null) {
                    _uiState.update { it.copy(explorePage = explore) }
                }
            }

            // 9. DYNAMIC CONTENT: Similar To & Vibes
            loadDynamicContent()

            // 10. DAILY DISCOVER: seed-based carousel
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                loadDailyDiscover()
            }

            // 11. COMMUNITY: human-curated playlists based on listening history
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                loadCommunityPlaylists()
            }
        }

        private suspend fun loadCommunityPlaylists() {
            try {
                val history =
                    withContext(PerformanceDispatcher.diskIO) {
                        playlistRepository.history.firstOrNull() ?: emptyList()
                    }.audioMusicOnly()

                val artistSeeds =
                    history
                        .groupBy { it.artist }
                        .map { it.key to it.value.size }
                        .filter { it.first.isNotBlank() }
                        .sortedByDescending { it.second }
                        .take(8)
                        .map { it.first }

                val trackSeeds =
                    history
                        .distinctBy { it.videoId }
                        .take(6)

                if (artistSeeds.isEmpty() && trackSeeds.isEmpty()) return

                fun MusicPlaylist.isCommunityPlaylistCandidate(): Boolean {
                    val normalizedAuthor = author.trim()
                    return normalizedAuthor.isNotBlank() &&
                        !normalizedAuthor.equals("YouTube", true) &&
                        !normalizedAuthor.equals("YouTube Music", true) &&
                        !id.startsWith("RD") &&
                        !id.startsWith("OLAK")
                }

                val communityQueries =
                    buildList {
                        artistSeeds.forEach { artist ->
                            add("$artist playlist")
                            add("$artist fan playlist")
                            add("$artist mix")
                        }
                        trackSeeds.forEach { track ->
                            val artist = track.artist.takeIf { it.isNotBlank() } ?: return@forEach
                            add("${track.title} $artist playlist")
                        }
                    }.distinct().take(24)

                val candidates =
                    supervisorScope {
                        communityQueries
                            .map { query ->
                                async(PerformanceDispatcher.networkIO) {
                                    try {
                                        YouTubeMusicService
                                            .searchPlaylists(query, 6)
                                            .filter { it.isCommunityPlaylistCandidate() }
                                    } catch (e: Exception) {
                                        emptyList()
                                    }
                                }
                            }.awaitAll()
                            .flatten()
                            .distinctBy { it.id }
                            .shuffled()
                            .take(24)
                    }

                val communityItems =
                    supervisorScope {
                        candidates
                            .map { playlist ->
                                async(PerformanceDispatcher.networkIO) {
                                    try {
                                        val details = YouTubeMusicService.fetchPlaylistDetails(playlist.id)
                                        val tracks =
                                            details
                                                ?.tracks
                                                .orEmpty()
                                                .audioMusicOnly()
                                                .take(4)
                                        if (tracks.isNotEmpty()) {
                                            CommunityMusicPlaylist(
                                                playlist =
                                                    playlist.copy(
                                                        trackCount = details?.trackCount ?: playlist.trackCount,
                                                        thumbnailUrl = playlist.thumbnailUrl.ifBlank { tracks.first().thumbnailUrl },
                                                    ),
                                                tracks = tracks,
                                            )
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()
                            .take(8)
                    }

                if (communityItems.isNotEmpty()) {
                    _uiState.update { it.copy(communityPlaylists = communityItems) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading community playlists", e)
            }
        }

        private fun loadDynamicContent() {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                val history = playlistRepository.history.firstOrNull() ?: emptyList()
                val similarSections = mutableListOf<MusicSection>()

                if (history.isNotEmpty()) {
                    // 1. Similar to random top artists (take top 10 artists by play count, pick 2)
                    val topArtists =
                        history
                            .groupBy { it.artist }
                            .mapValues { it.value.size }
                            .toList()
                            .sortedByDescending { it.second }
                            .take(10)
                            .shuffled()
                            .take(2)

                    // OPTIMIZED: Parallel fetch for similar artists
                    val similarArtistSections =
                        topArtists
                            .map { (artistName, _) ->
                                async(PerformanceDispatcher.networkIO) {
                                    val artistTrack = history.find { it.artist == artistName }
                                    if (artistTrack != null && !artistTrack.channelId.isNullOrBlank()) {
                                        try {
                                            val related =
                                                InnertubeMusicService
                                                    .getRelatedMusic(artistTrack.videoId, audioOnly = true)
                                                    .audioMusicOnly()
                                            if (related.isNotEmpty()) {
                                                MusicSection(
                                                    title = artistName,
                                                    label = context.getString(R.string.similar_to),
                                                    thumbnailUrl = artistTrack.thumbnailUrl,
                                                    seedId = artistTrack.channelId,
                                                    isArtistSeed = true,
                                                    tracks = musicBrain.rankTracks(related, "similar").take(12),
                                                )
                                            } else {
                                                null
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MusicViewModel", "Error loading similar to artist $artistName", e)
                                            null
                                        }
                                    } else {
                                        null
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()

                    similarSections.addAll(similarArtistSections)

                    // 2. Similar to most recent song (if not already picked)
                    val recentTrack = history.firstOrNull()
                    if (recentTrack != null && similarSections.none { it.title == recentTrack.title || it.title == recentTrack.artist }) {
                        if (!recentTrack.videoId.isNullOrBlank()) {
                            try {
                                val related =
                                    InnertubeMusicService
                                        .getRelatedMusic(recentTrack.videoId, audioOnly = true)
                                        .audioMusicOnly()
                                if (related.isNotEmpty()) {
                                    similarSections.add(
                                        MusicSection(
                                            title = recentTrack.title,
                                            label = context.getString(R.string.similar_to),
                                            thumbnailUrl = recentTrack.thumbnailUrl,
                                            seedId = recentTrack.videoId,
                                            isArtistSeed = false,
                                            tracks = musicBrain.rankTracks(related, "similar").take(12),
                                        ),
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("MusicViewModel", "Error loading similar to song ${recentTrack.title}", e)
                            }
                        }
                    }
                }

                // B. Random Vibe Playlists
                val vibes = listOf("Focus", "Relaxing", "Energize", "Commute", "Party", "Romance", "Sad", "Sleep", "Workout")
                val vibe = vibes.random()

                try {
                    val playlists = YouTubeMusicService.searchPlaylists("$vibe music playlists", 10)
                    if (playlists.isNotEmpty()) {
                        val playlistTracks =
                            playlists.map { playlist ->
                                MusicTrack(
                                    videoId = playlist.id,
                                    title = playlist.title,
                                    artist = playlist.author,
                                    thumbnailUrl = playlist.thumbnailUrl,
                                    duration = 0,
                                    itemType = io.github.aedev.flow.ui.screens.music.MusicItemType.PLAYLIST,
                                )
                            }
                        similarSections.add(
                            MusicSection(
                                title = context.getString(R.string.section_vibe_vibes, vibe),
                                subtitle = context.getString(R.string.subtitle_community_playlists),
                                tracks = playlistTracks,
                            ),
                        )
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error loading vibe playlists", e)
                }

                if (similarSections.isNotEmpty()) {
                    _uiState.update { it.copy(similarToSections = similarSections) }
                }
            }
        }

        fun loadMorePlaylistTracks() {
            val currentPlaylist = _uiState.value.selectedPlaylist ?: _uiState.value.playlistDetails ?: return
            val continuation = currentPlaylist.continuation ?: return
            if (_uiState.value.isMoreLoading) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isMoreLoading = true) }
                try {
                    val (newTracks, nextContinuation) = YouTubeMusicService.fetchPlaylistContinuation(currentPlaylist.id, continuation)

                    _uiState.update { state ->
                        val updatedPlaylist =
                            currentPlaylist.copy(
                                tracks = currentPlaylist.tracks + newTracks,
                                continuation = nextContinuation,
                                trackCount = currentPlaylist.trackCount + newTracks.size,
                            )
                        state.copy(
                            selectedPlaylist = updatedPlaylist,
                            playlistDetails = updatedPlaylist,
                            isMoreLoading = false,
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(isMoreLoading = false) }
                }
            }
        }

        fun loadArtistItems(
            browseId: String,
            params: String?,
        ) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isArtistItemsLoading = true, artistItemsPage = null) }
                YouTube
                    .artistItems(BrowseEndpoint(browseId, params))
                    .onSuccess { page ->
                        _uiState.update { it.copy(artistItemsPage = page, isArtistItemsLoading = false) }
                    }.onFailure {
                        _uiState.update { it.copy(isArtistItemsLoading = false) }
                    }
            }
        }

        fun loadMoreArtistItems() {
            val continuation = _uiState.value.artistItemsPage?.continuation ?: return
            if (_uiState.value.isMoreLoading) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isMoreLoading = true) }
                YouTube
                    .artistItemsContinuation(continuation)
                    .onSuccess { page ->
                        _uiState.update {
                            it.copy(
                                isMoreLoading = false,
                                artistItemsPage =
                                    it.artistItemsPage?.copy(
                                        items = it.artistItemsPage.items + page.items,
                                        continuation = page.continuation,
                                    ),
                            )
                        }
                    }.onFailure {
                        _uiState.update { it.copy(isMoreLoading = false) }
                    }
            }
        }

        // Helper to process sections to avoid code duplication
        private suspend fun processHomeSections(sections: List<MusicSection>) {
            val quickPicks =
                sections
                    .find {
                        it.title.contains("Quick picks", true) ||
                            it.title.contains("Start radio", true) ||
                            it.title.contains("Recommended", true) ||
                            it.title.contains("Mixed for you", true)
                    }?.tracks
                    ?.audioMusicOnly()
                    .orEmpty()

            val recommended =
                sections
                    .find {
                        it.title.contains("Mixed for you", true) ||
                            it.title.contains("Recommended", true) ||
                            it.title.contains("Listen again", true)
                    }?.tracks
                    ?.audioMusicOnly()
                    .orEmpty()

            val musicVideosForYou =
                sections
                    .find {
                        it.title.contains("Music videos for you", true)
                    }?.tracks ?: emptyList()

            val musicVideos =
                sections
                    .find {
                        it.title.contains("Music videos", true) || it.title.contains("Videos", true)
                    }?.tracks ?: musicVideosForYou

            val livePerformances =
                sections
                    .find {
                        it.title.contains("Live performances", true) ||
                            (it.title.contains("Live", true) && it.title.contains("performance", true))
                    }?.tracks ?: emptyList()

            val longListens =
                sections
                    .find {
                        it.title.contains("Long listens", true)
                    }?.tracks ?: emptyList()

            val listenAgain =
                sections
                    .find {
                        it.title.contains("Listen again", true)
                    }?.tracks
                    ?.audioMusicOnly() ?: emptyList()

            _uiState.update { currentState ->
                currentState.copy(
                    forYouTracks =
                        if (quickPicksComposed) {
                            currentState.forYouTracks
                        } else {
                            quickPicks.ifEmpty { currentState.forYouTracks }
                        },
                    recommendedTracks = recommended.ifEmpty { currentState.recommendedTracks },
                    listenAgain = listenAgain,
                    musicVideos = musicVideos,
                    musicVideosForYou = musicVideosForYou,
                    livePerformances = livePerformances,
                    longListens = longListens,
                    dynamicSections = sections,
                )
            }
        }

        fun setHomeChip(chip: HomePage.Chip?) {
            _uiState.update { it.copy(selectedHomeChip = chip) }
            if (chip != null && chip.endpoint != null) {
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    _uiState.update { it.copy(isLoading = true) }
                    try {
                        val response = YouTube.home(params = chip.endpoint.params).getOrNull()
                        response?.let { home ->
                            processHomeSections(musicRecommendationAlgorithm.parseHomeSections(home))
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Error filtering by chip", e)
                    } finally {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            } else {
                loadMusicContent()
            }
        }

        fun retry() {
            loadMusicContent()
        }

        fun refresh() {
            _uiState.update { it.copy(isLoading = true) }
            loadMusicContent(force = true)
        }

        /**
         *  PERFORMANCE OPTIMIZED: Fetch artist details with timeout
         */
        fun fetchArtistDetails(channelId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.value = _uiState.value.copy(isArtistLoading = true, artistDetails = null)

                supervisorScope {
                    val detailsDeferred =
                        async(PerformanceDispatcher.networkIO) {
                            withTimeoutOrNull(10_000L) {
                                YouTubeMusicService.fetchArtistDetails(channelId)
                            }
                        }

                    val subscriptionDeferred =
                        async(PerformanceDispatcher.diskIO) {
                            subscriptionRepository.isSubscribed(channelId).firstOrNull() ?: false
                        }

                    val details = detailsDeferred.await()
                    val isSubscribed = subscriptionDeferred.await()

                    _uiState.value =
                        _uiState.value.copy(
                            isArtistLoading = false,
                            artistDetails = details?.copy(isSubscribed = isSubscribed),
                        )
                }
            }
        }

        fun toggleFollowArtist(artist: ArtistDetails) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                if (artist.isSubscribed) {
                    subscriptionRepository.unsubscribe(artist.channelId)
                } else {
                    subscriptionRepository.subscribe(
                        io.github.aedev.flow.data.local.ChannelSubscription(
                            channelId = artist.channelId,
                            channelName = artist.name,
                            channelThumbnail = artist.thumbnailUrl,
                            isMusic = true,
                        ),
                    )
                }

                // Update UI state
                val currentDetails = _uiState.value.artistDetails
                if (currentDetails?.channelId == artist.channelId) {
                    _uiState.value =
                        _uiState.value.copy(
                            artistDetails = currentDetails.copy(isSubscribed = !artist.isSubscribed),
                        )
                }
            }
        }

        fun clearArtistDetails() {
            _uiState.value = _uiState.value.copy(artistDetails = null)
        }

        /**
         *  PERFORMANCE OPTIMIZED: Fetch playlist details with timeout
         */
        fun fetchPlaylistDetails(playlistId: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.value = _uiState.value.copy(isPlaylistLoading = true, playlistDetails = null)

                // Try local first (fast path)
                val localPlaylist =
                    withContext(PerformanceDispatcher.diskIO) {
                        localPlaylistRepository.getPlaylistInfo(playlistId)
                    }

                if (localPlaylist != null) {
                    val videos =
                        withContext(PerformanceDispatcher.diskIO) {
                            localPlaylistRepository.getPlaylistVideosFlow(playlistId).firstOrNull() ?: emptyList()
                        }
                    val tracks =
                        videos.map { video ->
                            MusicTrack(
                                videoId = video.id,
                                title = video.title,
                                artist = video.channelName,
                                thumbnailUrl = video.thumbnailUrl,
                                duration = (video.duration / 1000).toInt(),
                                sourceUrl = "", // Not needed for local playback usually
                            )
                        }

                    val details =
                        PlaylistDetails(
                            id = localPlaylist.id,
                            title = localPlaylist.name,
                            thumbnailUrl = localPlaylist.thumbnailUrl,
                            author = context.getString(R.string.you),
                            trackCount = tracks.size,
                            description = localPlaylist.description,
                            tracks = tracks,
                        )

                    _uiState.value =
                        _uiState.value.copy(
                            isPlaylistLoading = false,
                            playlistDetails = details,
                            selectedPlaylist = details,
                        )
                    return@launch
                }

                // Fallback to remote with timeout
                try {
                    val details =
                        withTimeoutOrNull(12_000L) {
                            YouTubeMusicService.fetchPlaylistDetails(playlistId)
                        }
                    _uiState.value =
                        _uiState.value.copy(
                            isPlaylistLoading = false,
                            playlistDetails = details,
                            selectedPlaylist = details,
                        )
                } catch (e: Exception) {
                    _uiState.value =
                        _uiState.value.copy(
                            isPlaylistLoading = false,
                            error = context.getString(R.string.error_failed_to_load_playlist),
                        )
                }
            }
        }

        fun loadCommunityPlaylist(genre: String) {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.value = _uiState.value.copy(isPlaylistLoading = true, playlistDetails = null)
                try {
                    var tracks = _uiState.value.genreTracks[genre]

                    if (tracks == null || tracks.isEmpty()) {
                        // Fetch if not in state (e.g. new ViewModel instance)
                        tracks = withTimeoutOrNull(10_000L) {
                            YouTubeMusicService.fetchMusicByGenre(genre, 30)
                        } ?: emptyList()
                    }

                    val playlistDetails =
                        PlaylistDetails(
                            id = "community_$genre",
                            title = genre,
                            thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl ?: "",
                            author = context.getString(R.string.playlist_author_community),
                            trackCount = tracks.size,
                            description = context.getString(R.string.playlist_description_community, genre),
                            tracks = tracks,
                        )
                    _uiState.value =
                        _uiState.value.copy(
                            isPlaylistLoading = false,
                            playlistDetails = playlistDetails,
                        )
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error loading community playlist", e)
                    _uiState.value = _uiState.value.copy(isPlaylistLoading = false)
                }
            }
        }

        fun clearPlaylistDetails() {
            _uiState.value = _uiState.value.copy(playlistDetails = null)
        }

        fun loadMoreHomeContent() {
            val currentContinuation = _uiState.value.homeContinuation ?: return
            if (_uiState.value.isMoreLoading) return

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isMoreLoading = true) }

                try {
                    val result = musicRecommendationAlgorithm.loadHomeContinuation(currentContinuation)
                    val newSections = result.first
                    val nextContinuation = result.second

                    if (newSections.isNotEmpty()) {
                        val currentSections = _uiState.value.dynamicSections.toMutableList()
                        currentSections.addAll(newSections)
                        _uiState.update {
                            it.copy(
                                dynamicSections = currentSections,
                                homeContinuation = nextContinuation,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(homeContinuation = null) }
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Error loading more home content", e)
                } finally {
                    _uiState.update { it.copy(isMoreLoading = false) }
                }
            }
        }

        private suspend fun loadDailyDiscover() {
            try {
                val seeds =
                    withContext(PerformanceDispatcher.diskIO) {
                        val history = playlistRepository.history.firstOrNull() ?: emptyList()
                        history
                            .audioMusicOnly()
                            .shuffled()
                            .take(5)
                    }

                if (seeds.isEmpty()) return

                val items = java.util.Collections.synchronizedList(mutableListOf<DailyDiscoverItem>())

                kotlinx.coroutines.coroutineScope {
                    seeds
                        .map { seed ->
                            launch(PerformanceDispatcher.networkIO) {
                                try {
                                    val related =
                                        YouTubeMusicService
                                            .getRelatedMusic(seed.videoId, 16, audioOnly = true)
                                            .audioMusicOnly()
                                    val recommendation =
                                        musicBrain
                                            .rankTracks(related.filter { it.videoId != seed.videoId }, "discover")
                                            .firstOrNull { it.isAudioMusicCandidate() }
                                    if (recommendation != null) {
                                        items.add(DailyDiscoverItem(seed, recommendation))
                                    }
                                } catch (e: Exception) {
                                    Log.e("MusicViewModel", "Error fetching Daily Discover for seed ${seed.title}", e)
                                }
                            }
                        }.forEach { it.join() }
                }

                if (items.isNotEmpty()) {
                    val finalDiscover =
                        items
                            .toList()
                            .filter { it.seed.isAudioMusicCandidate() && it.recommendation.isAudioMusicCandidate() }
                            .distinctBy { it.recommendation.videoId }
                            .shuffled()
                    _uiState.update { it.copy(dailyDiscover = finalDiscover) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error in loadDailyDiscover", e)
            }
        }
    }

data class MusicUiState(
    val sessionSeed: Long = System.currentTimeMillis(),
    val dailyDiscover: List<DailyDiscoverItem> = emptyList(),
    val onRepeatTracks: List<MusicTrack> = emptyList(), // On Repeat (local music brain)
    val forYouTracks: List<MusicTrack> = emptyList(), // Quick Picks
    val recommendedTracks: List<MusicTrack> = emptyList(), // Recommended for you
    val listenAgain: List<MusicTrack> = emptyList(), // Listen Again
    val trendingSongs: List<MusicTrack> = emptyList(),
    val newReleases: List<MusicTrack> = emptyList(),
    val musicVideos: List<MusicTrack> = emptyList(),
    val musicVideosForYou: List<MusicTrack> = emptyList(),
    val livePerformances: List<MusicTrack> = emptyList(),
    val communityPlaylists: List<CommunityMusicPlaylist> = emptyList(),
    val longListens: List<MusicTrack> = emptyList(),
    val history: List<MusicTrack> = emptyList(),
    val allSongs: List<MusicTrack> = emptyList(),
    val genreTracks: Map<String, List<MusicTrack>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val featuredPlaylists: List<MusicPlaylist> = emptyList(),
    val topAlbums: List<MusicPlaylist> = emptyList(),
    val dynamicSections: List<MusicSection> = emptyList(),
    val homeChips: List<HomePage.Chip> = emptyList(),
    val selectedHomeChip: HomePage.Chip? = null,
    val explorePage: io.github.aedev.flow.innertube.pages.ExplorePage? = null,
    val moodsAndGenres: List<MoodAndGenres> = emptyList(),
    val selectedGenre: String? = null,
    val selectedFilter: String? = null,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null,
    val downloadedTrackIds: Set<String> = emptySet(),
    val artistDetails: ArtistDetails? = null,
    val isArtistLoading: Boolean = false,
    val playlistDetails: PlaylistDetails? = null,
    val selectedPlaylist: PlaylistDetails? = null,
    val isPlaylistLoading: Boolean = false,
    val isMoreLoading: Boolean = false,
    val searchResultsArtists: List<ArtistDetails> = emptyList(),
    val homeContinuation: String? = null,
    val artistItemsPage: io.github.aedev.flow.innertube.pages.ArtistItemsPage? = null,
    val isArtistItemsLoading: Boolean = false,
    val similarToSections: List<MusicSection> = emptyList(),
)
