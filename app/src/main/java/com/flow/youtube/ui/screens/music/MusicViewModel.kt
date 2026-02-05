package com.flow.youtube.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.MusicCache
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.data.recommendation.MusicRecommendationAlgorithm
import com.flow.youtube.data.recommendation.MusicSection
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.pages.MoodAndGenres
import com.flow.youtube.data.newmusic.InnertubeMusicService
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.flow.youtube.innertube.pages.HomePage
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRecommendationAlgorithm: MusicRecommendationAlgorithm,
    private val subscriptionRepository: com.flow.youtube.data.local.SubscriptionRepository,
    private val playlistRepository: com.flow.youtube.data.music.PlaylistRepository,
    private val localPlaylistRepository: com.flow.youtube.data.local.PlaylistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        loadMusicContent()
    }

    /**
     *  PERFORMANCE OPTIMIZED: Load all music content with aggressive parallel fetching
     * Uses SupervisorScope for error isolation - if one source fails, others continue
     */
    private fun loadMusicContent() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            //  INSTANT LOAD: Try to load from cache first
            val cachedTrending = MusicCache.getTrendingMusic(100)
            
            val cachedResult = try {
                musicRecommendationAlgorithm.loadMusicHome()
            } catch (e: Exception) { emptyList<MusicSection>() to null }
            
            val cachedSections = cachedResult.first
            
            if (cachedTrending != null || cachedSections.isNotEmpty()) {
                // Apply cached data immediately
                if (cachedSections.isNotEmpty()) {
                    processHomeSections(cachedSections)
                }
                
                cachedTrending?.let { trend ->
                    _uiState.update { it.copy(
                        trendingSongs = trend,
                        allSongs = if (it.selectedFilter == null) trend else it.allSongs
                    ) }
                }
                
                _uiState.update { it.copy(isLoading = false) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            
            //  PARALLEL FETCH: All content sources simultaneously with SupervisorScope
            supervisorScope {
                // Essential content - fetch in parallel
                val trendingDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(15_000L) {
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
                }
                
                // FORCE REFRESH home sections from network
                val homeSectionsDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(20_000L) { // Slightly longer timeout for full home
                        try {
                            musicRecommendationAlgorithm.refreshMusicHome()
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error refreshing home sections", e)
                            emptyList<MusicSection>() to null
                        }
                    } ?: (emptyList<MusicSection>() to null)
                }
                
                val historyDeferred = async(PerformanceDispatcher.diskIO) {
                    withTimeoutOrNull(5_000L) {
                        try {
                            playlistRepository.history.firstOrNull() ?: emptyList()
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading history", e)
                            emptyList()
                        }
                    } ?: emptyList()
                }
                
                val newReleasesDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(12_000L) {
                        try {
                            YouTubeMusicService.fetchNewReleases(40)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading new releases", e)
                            emptyList()
                        }
                    } ?: emptyList()
                }
                
                val playlistsDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(10_000L) {
                        try {
                            YouTubeMusicService.searchPlaylists("official music playlists 2025", 10)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading playlists", e)
                            emptyList()
                        }
                    } ?: emptyList()
                }
                
                val popularArtistsDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(12_000L) {
                        try {
                            val tracks = YouTubeMusicService.fetchPopularArtistMusic(50)
                            MusicCache.cacheGenreTracks("Popular Artists", 50, tracks)
                            tracks
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Error loading popular artists", e)
                            emptyList()
                        }
                    } ?: emptyList()
                }
                
                // Await all results
                val trending = trendingDeferred.await()
                val homeResult = homeSectionsDeferred.await()
                val homeSections = homeResult.first
                val homeContinuation = homeResult.second
                val history = historyDeferred.await()
                val newReleases = newReleasesDeferred.await()
                val featuredPlaylists = playlistsDeferred.await()
                val popularArtistTracks = popularArtistsDeferred.await()
                
                // Fetch Chips
                val homeChips = musicRecommendationAlgorithm.getHomeChips()
                _uiState.update { it.copy(homeChips = homeChips) }
                
                // Update trending
                trending?.let { trend ->
                    val currentState = _uiState.value
                    _uiState.value = currentState.copy(
                        trendingSongs = trend,
                        allSongs = if (currentState.selectedFilter == null) trend else currentState.allSongs,
                        isLoading = false
                    )
                }
                
                // Process fresh home sections
                if (homeSections.isNotEmpty()) {
                    processHomeSections(homeSections)
                    _uiState.update { it.copy(homeContinuation = homeContinuation) }
                } else if (_uiState.value.forYouTracks.isEmpty() && _uiState.value.dynamicSections.isEmpty()) {
                     // Fallback if we have nothing (Cache empty + Network failed)
                     // Try to get recommendations via fallback (internal engine)
                     val recs = musicRecommendationAlgorithm.getRecommendations(20)
                     if (recs.isNotEmpty()) {
                         _uiState.update { it.copy(forYouTracks = recs) }
                     }
                }
                
                // Update history
                _uiState.update { it.copy(history = history) }
                
                // Update new releases
                _uiState.update { it.copy(newReleases = newReleases) }
                
                // Update featured playlists
                _uiState.update { it.copy(featuredPlaylists = featuredPlaylists) }
                
                // Update popular artists
                if (popularArtistTracks.isNotEmpty()) {
                    val currentGenreTracks = _uiState.value.genreTracks.toMutableMap()
                    currentGenreTracks["Popular Artists"] = popularArtistTracks
                    
                    // Load genres list
                    val genres = YouTubeMusicService.getPopularGenres()
                    _uiState.update { it.copy(
                        genreTracks = currentGenreTracks,
                        genres = listOf("Popular Artists") + genres
                    ) }
                }
                
                //  PARALLEL: Load genre content in background
                launch(PerformanceDispatcher.networkIO) {
                    val genreList = listOf("Pop", "Rock", "Hip Hop", "R&B", "Electronic")
                    val genreMap = mutableMapOf<String, List<MusicTrack>>()
                    
                    supervisorScope {
                        genreList.map { genre ->
                            async(PerformanceDispatcher.networkIO) {
                                withTimeoutOrNull(8_000L) {
                                    try {
                                        val tracks = musicRecommendationAlgorithm.getGenreContent(genre)
                                        if (tracks.isNotEmpty()) {
                                            genre to tracks
                                        } else null
                                    } catch (e: Exception) { null }
                                }
                            }
                        }.forEach { deferred ->
                            deferred.await()?.let { (genre, tracks) ->
                                genreMap[genre] = tracks
                            }
                        }
                    }
                    
                    if (genreMap.isNotEmpty()) {
                        val updated = _uiState.value.genreTracks.toMutableMap()
                        updated.putAll(genreMap)
                        _uiState.update { it.copy(genreTracks = updated) }
                    }
                }
                
                // Fetch Explore & Moods
                launch(PerformanceDispatcher.networkIO) {
                    val explore = InnertubeMusicService.fetchExplore()
                    val moods = InnertubeMusicService.fetchMoodAndGenres()
                    _uiState.update { it.copy(
                        explorePage = explore,
                        moodsAndGenres = moods
                    ) }
                }
                
                // Mark loading complete
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadMorePlaylistTracks() {
        val currentPlaylist = _uiState.value.selectedPlaylist ?: return
        val continuation = currentPlaylist.continuation ?: return
        if (_uiState.value.isMoreLoading) return

        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            _uiState.update { it.copy(isMoreLoading = true) }
            try {
                val (newTracks, nextContinuation) = YouTubeMusicService.fetchPlaylistContinuation(currentPlaylist.id, continuation)
                
                _uiState.update { state ->
                    val updatedPlaylist = currentPlaylist.copy(
                        tracks = currentPlaylist.tracks + newTracks,
                        continuation = nextContinuation,
                        trackCount = currentPlaylist.trackCount + newTracks.size
                    )
                    state.copy(
                        selectedPlaylist = updatedPlaylist,
                        isMoreLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isMoreLoading = false) }
            }
        }
    }
    
    // Helper to process sections to avoid code duplication
    private suspend fun processHomeSections(sections: List<MusicSection>) {
        val quickPicks = sections.find { 
            it.title.contains("Quick picks", true) || 
            it.title.contains("Start radio", true) ||
            it.title.contains("Recommended", true) ||
            it.title.contains("Mixed for you", true)
        }?.tracks ?: musicRecommendationAlgorithm.getRecommendations(20)

        val recommended = sections.find {
            it.title.contains("Mixed for you", true) || 
            it.title.contains("Recommended", true) ||
            it.title.contains("Listen again", true)
        }?.tracks ?: musicRecommendationAlgorithm.getRecommendations(30)

        val musicVideos = sections.find { 
            it.title.contains("Music videos", true) || it.title.contains("Videos", true)
        }?.tracks ?: emptyList()

        val longListens = sections.find { 
            it.title.contains("Long listens", true) 
        }?.tracks ?: emptyList()

        val listenAgain = sections.find { 
            it.title.contains("Listen again", true) 
        }?.tracks ?: emptyList()

        _uiState.update { it.copy(
            forYouTracks = quickPicks,
            recommendedTracks = recommended,
            listenAgain = listenAgain,
            musicVideos = musicVideos,
            longListens = longListens,
            dynamicSections = sections
        ) }
    }

    fun setFilter(filter: String?) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        if (filter != null) {
            loadGenreTracks(filter)
        } else {
            _uiState.value = _uiState.value.copy(allSongs = _uiState.value.trendingSongs)
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

    /**
     *  PERFORMANCE OPTIMIZED: Search with parallel query execution
     */
    fun searchMusic(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(allSongs = _uiState.value.trendingSongs)
            return
        }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            // Check cache
            val cached = MusicCache.getSearchResults(query)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(allSongs = cached, isSearching = false)
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                //  PARALLEL: Search tracks and artists simultaneously
                supervisorScope {
                    val resultsDeferred = async(PerformanceDispatcher.networkIO) { 
                        withTimeoutOrNull(12_000L) {
                            YouTubeMusicService.searchMusic(query, 60) 
                        } ?: emptyList()
                    }
                    val artistsDeferred = async(PerformanceDispatcher.networkIO) { 
                        withTimeoutOrNull(8_000L) {
                            YouTubeMusicService.searchArtists(query, 5) 
                        } ?: emptyList()
                    }
                    
                    val results = resultsDeferred.await()
                    val artists = artistsDeferred.await()
                    
                    MusicCache.cacheSearchResults(query, results)
                    _uiState.value = _uiState.value.copy(
                        allSongs = results,
                        searchResultsArtists = artists,
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Search error", e)
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    /**
     *  PERFORMANCE OPTIMIZED: Load genre tracks with timeout
     */
    fun loadGenreTracks(genre: String) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            // Check cache
            val cached = MusicCache.getGenreTracks(genre, 100)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    allSongs = cached,
                    selectedGenre = genre,
                    isSearching = false
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val tracks = withTimeoutOrNull(12_000L) {
                    YouTubeMusicService.fetchMusicByGenre(genre, 60)
                } ?: emptyList()
                
                if (tracks.isNotEmpty()) {
                    MusicCache.cacheGenreTracks(genre, 60, tracks)
                }
                _uiState.value = _uiState.value.copy(
                    allSongs = tracks,
                    selectedGenre = genre,
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun retry() {
        loadMusicContent()
    }
    
    /**
     *  PERFORMANCE OPTIMIZED: Fetch artist details with timeout
     */
    fun fetchArtistDetails(channelId: String) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            _uiState.value = _uiState.value.copy(isArtistLoading = true, artistDetails = null)
            
            supervisorScope {
                val detailsDeferred = async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(10_000L) {
                        YouTubeMusicService.fetchArtistDetails(channelId)
                    }
                }
                
                val subscriptionDeferred = async(PerformanceDispatcher.diskIO) {
                    subscriptionRepository.isSubscribed(channelId).firstOrNull() ?: false
                }
                
                val details = detailsDeferred.await()
                val isSubscribed = subscriptionDeferred.await()
                
                _uiState.value = _uiState.value.copy(
                    isArtistLoading = false,
                    artistDetails = details?.copy(isSubscribed = isSubscribed)
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
                    com.flow.youtube.data.local.ChannelSubscription(
                        channelId = artist.channelId,
                        channelName = artist.name,
                        channelThumbnail = artist.thumbnailUrl
                    )
                )
            }
            
            // Update UI state
            val currentDetails = _uiState.value.artistDetails
            if (currentDetails?.channelId == artist.channelId) {
                _uiState.value = _uiState.value.copy(
                    artistDetails = currentDetails.copy(isSubscribed = !artist.isSubscribed)
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
            val localPlaylist = withContext(PerformanceDispatcher.diskIO) {
                localPlaylistRepository.getPlaylistInfo(playlistId)
            }
            
            if (localPlaylist != null) {
                val videos = withContext(PerformanceDispatcher.diskIO) {
                    localPlaylistRepository.getPlaylistVideosFlow(playlistId).firstOrNull() ?: emptyList()
                }
                val tracks = videos.map { video ->
                    MusicTrack(
                        videoId = video.id,
                        title = video.title,
                        artist = video.channelName,
                        thumbnailUrl = video.thumbnailUrl,
                        duration = (video.duration / 1000).toInt(),
                        sourceUrl = "" // Not needed for local playback usually
                    )
                }
                
                val details = PlaylistDetails(
                    id = localPlaylist.id,
                    title = localPlaylist.name,
                    thumbnailUrl = localPlaylist.thumbnailUrl,
                    author = "You",
                    trackCount = tracks.size,
                    description = localPlaylist.description,
                    tracks = tracks
                )
                
                _uiState.value = _uiState.value.copy(
                    isPlaylistLoading = false,
                    playlistDetails = details
                )
                return@launch
            }

            // Fallback to remote with timeout
            try {
                val details = withTimeoutOrNull(12_000L) {
                    YouTubeMusicService.fetchPlaylistDetails(playlistId)
                }
                _uiState.value = _uiState.value.copy(
                    isPlaylistLoading = false,
                    playlistDetails = details
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPlaylistLoading = false,
                    error = "Failed to load playlist"
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
                
                val playlistDetails = PlaylistDetails(
                    id = "community_$genre",
                    title = genre,
                    thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl ?: "",
                    author = "Community Playlist",
                    trackCount = tracks.size,
                    description = "Curated playlist of $genre music from our community",
                    tracks = tracks
                )
                _uiState.value = _uiState.value.copy(
                    isPlaylistLoading = false,
                    playlistDetails = playlistDetails
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
                            homeContinuation = nextContinuation
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
}

data class MusicUiState(
    val forYouTracks: List<MusicTrack> = emptyList(), // Quick Picks
    val recommendedTracks: List<MusicTrack> = emptyList(), // Recommended for you
    val listenAgain: List<MusicTrack> = emptyList(), // Listen Again
    val trendingSongs: List<MusicTrack> = emptyList(),
    val newReleases: List<MusicTrack> = emptyList(),
    val musicVideos: List<MusicTrack> = emptyList(),
    val longListens: List<MusicTrack> = emptyList(),
    val history: List<MusicTrack> = emptyList(),
    val allSongs: List<MusicTrack> = emptyList(),
    val genreTracks: Map<String, List<MusicTrack>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val featuredPlaylists: List<MusicPlaylist> = emptyList(),
    val dynamicSections: List<MusicSection> = emptyList(),
    val homeChips: List<HomePage.Chip> = emptyList(),
    val selectedHomeChip: HomePage.Chip? = null,
    val explorePage: com.flow.youtube.innertube.pages.ExplorePage? = null,
    val moodsAndGenres: List<MoodAndGenres> = emptyList(),
    val selectedGenre: String? = null,
    val selectedFilter: String? = null,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null,
    val artistDetails: ArtistDetails? = null,
    val isArtistLoading: Boolean = false,
    val playlistDetails: PlaylistDetails? = null,
    val selectedPlaylist: PlaylistDetails? = null,
    val isPlaylistLoading: Boolean = false,
    val isMoreLoading: Boolean = false,
    val searchResultsArtists: List<ArtistDetails> = emptyList(),
    val homeContinuation: String? = null
)
