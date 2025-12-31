package com.flow.youtube.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.MusicCache
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.data.recommendation.MusicRecommendationAlgorithm
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.data.newmusic.InnertubeMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val musicRecommendationAlgorithm: MusicRecommendationAlgorithm,
    private val subscriptionRepository: com.flow.youtube.data.local.SubscriptionRepository,
    private val playlistRepository: com.flow.youtube.data.music.PlaylistRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    init {
        loadMusicContent()
    }

    private fun loadMusicContent() {
        viewModelScope.launch {
            // Try to load from cache first for instant display
            val cachedTrending = MusicCache.getTrendingMusic(100)
            if (cachedTrending != null) {
                _uiState.value = _uiState.value.copy(
                    trendingSongs = cachedTrending,
                    allSongs = cachedTrending,
                    isLoading = false
                )
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Fetch in parallel
                val forYouJob = async {
                    musicRecommendationAlgorithm.getRecommendations(30)
                }

                val trendingJob = async { 
                    val tracks = YouTubeMusicService.fetchTrendingMusic(100)
                    MusicCache.cacheTrendingMusic(100, tracks)
                    tracks
                }
                
                val newReleasesJob = async {
                    YouTubeMusicService.fetchNewReleases(40)
                }
                
                val popularArtistsJob = async {
                    val tracks = YouTubeMusicService.fetchPopularArtistMusic(50)
                    MusicCache.cacheGenreTracks("Popular Artists", 50, tracks)
                    tracks
                }

                val historyJob = async {
                    playlistRepository.history.firstOrNull() ?: emptyList()
                }
                
                val genres = YouTubeMusicService.getPopularGenres()
                val genreJobs = genres.map { genre ->
                    genre to async { 
                        val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 30)
                        if (tracks.isNotEmpty()) MusicCache.cacheGenreTracks(genre, 30, tracks)
                        tracks
                    }
                }
                
                val trending = trendingJob.await()
                val forYou = forYouJob.await()
                val newReleases = newReleasesJob.await()
                val popularArtistTracks = popularArtistsJob.await()
                val history = historyJob.await()
                
                // Fetch Home Sections for Music Videos and Long Listens
                val homeResult = YouTube.home()
                val homeSections = homeResult.getOrNull()?.sections ?: emptyList()
                
                val musicVideos = homeSections.find { 
                    it.title.contains("Music videos", ignoreCase = true) || 
                    it.title.contains("Recommended music videos", ignoreCase = true)
                }?.items?.filterIsInstance<SongItem>()?.mapNotNull { InnertubeMusicService.convertToMusicTrack(it) } ?: emptyList()
                
                val longListens = homeSections.find { 
                    it.title.contains("Long listens", ignoreCase = true) 
                }?.items?.filterIsInstance<SongItem>()?.mapNotNull { InnertubeMusicService.convertToMusicTrack(it) } ?: emptyList()
                
                val featuredPlaylists = YouTubeMusicService.searchPlaylists("official music playlists 2025", 10)
                
                val genreTracks = mutableMapOf<String, List<MusicTrack>>()
                if (popularArtistTracks.isNotEmpty()) genreTracks["Popular Artists"] = popularArtistTracks
                
                genreJobs.forEach { (genre, job) ->
                    val tracks = job.await()
                    if (tracks.isNotEmpty()) genreTracks[genre] = tracks
                }
                
                _uiState.value = _uiState.value.copy(
                    forYouTracks = forYou,
                    trendingSongs = trending,
                    newReleases = newReleases,
                    history = history,
                    musicVideos = musicVideos,
                    longListens = longListens,
                    featuredPlaylists = featuredPlaylists,
                    allSongs = trending,
                    genreTracks = genreTracks,
                    genres = listOf("Popular Artists") + genres,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading music content", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load music: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun setFilter(filter: String?) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
        if (filter != null) {
            searchMusic(filter)
        } else {
            _uiState.value = _uiState.value.copy(allSongs = _uiState.value.trendingSongs)
        }
    }

    fun searchMusic(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(allSongs = _uiState.value.trendingSongs)
            return
        }
        
        viewModelScope.launch {
            // Check cache
            val cached = MusicCache.getSearchResults(query)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(allSongs = cached, isSearching = false)
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isSearching = true)
            try {
                val resultsJob = async { YouTubeMusicService.searchMusic(query, 60) }
                val artistsJob = async { YouTubeMusicService.searchArtists(query, 5) }
                
                val results = resultsJob.await()
                val artists = artistsJob.await()
                
                MusicCache.cacheSearchResults(query, results)
                _uiState.value = _uiState.value.copy(
                    allSongs = results,
                    searchResultsArtists = artists,
                    isSearching = false
                )
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Search error", e)
                _uiState.value = _uiState.value.copy(isSearching = false)
            }
        }
    }

    fun loadGenreTracks(genre: String) {
        viewModelScope.launch {
            // Check cache
            val cached = MusicCache.getGenreTracks(genre, 100)
            if (cached != null) {
                _uiState.value = _uiState.value.copy(
                    allSongs = cached,
                    selectedGenre = genre,
                    isLoading = false
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 60)
                MusicCache.cacheGenreTracks(genre, 60, tracks)
                _uiState.value = _uiState.value.copy(
                    allSongs = tracks,
                    selectedGenre = genre,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun retry() {
        loadMusicContent()
    }
    
    fun fetchArtistDetails(channelId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isArtistLoading = true, artistDetails = null)
            val details = YouTubeMusicService.fetchArtistDetails(channelId)
            
            // Check subscription status
            val isSubscribed = if (details != null) {
                subscriptionRepository.isSubscribed(channelId).firstOrNull() ?: false
            } else false
            
            _uiState.value = _uiState.value.copy(
                isArtistLoading = false,
                artistDetails = details?.copy(isSubscribed = isSubscribed)
            )
        }
    }
    
    fun toggleFollowArtist(artist: ArtistDetails) {
        viewModelScope.launch {
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

    fun fetchPlaylistDetails(playlistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaylistLoading = true, playlistDetails = null)
            val details = YouTubeMusicService.fetchPlaylistDetails(playlistId)
            _uiState.value = _uiState.value.copy(
                isPlaylistLoading = false,
                playlistDetails = details
            )
        }
    }

    fun loadCommunityPlaylist(genre: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPlaylistLoading = true, playlistDetails = null)
            try {
                var tracks = _uiState.value.genreTracks[genre]
                
                if (tracks == null || tracks.isEmpty()) {
                    // Fetch if not in state (e.g. new ViewModel instance)
                    tracks = YouTubeMusicService.fetchMusicByGenre(genre, 30)
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
}

data class MusicUiState(
    val forYouTracks: List<MusicTrack> = emptyList(),
    val trendingSongs: List<MusicTrack> = emptyList(),
    val newReleases: List<MusicTrack> = emptyList(),
    val musicVideos: List<MusicTrack> = emptyList(),
    val longListens: List<MusicTrack> = emptyList(),
    val history: List<MusicTrack> = emptyList(),
    val allSongs: List<MusicTrack> = emptyList(),
    val genreTracks: Map<String, List<MusicTrack>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val featuredPlaylists: List<MusicPlaylist> = emptyList(),
    val selectedGenre: String? = null,
    val selectedFilter: String? = null,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null,
    val artistDetails: ArtistDetails? = null,
    val isArtistLoading: Boolean = false,
    val playlistDetails: PlaylistDetails? = null,
    val isPlaylistLoading: Boolean = false,
    val searchResultsArtists: List<ArtistDetails> = emptyList()
)
