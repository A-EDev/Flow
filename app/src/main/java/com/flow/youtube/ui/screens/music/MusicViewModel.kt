package com.flow.youtube.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.MusicCache
import com.flow.youtube.data.music.YouTubeMusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel : ViewModel() {
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
                Log.d("MusicViewModel", "Loading from cache: ${cachedTrending.size} trending tracks")
                _uiState.value = _uiState.value.copy(
                    trendingSongs = cachedTrending,
                    allSongs = cachedTrending,
                    isLoading = false
                )
                
                // Load cached genre data
                val genres = YouTubeMusicService.getPopularGenres()
                val genreTracks = mutableMapOf<String, List<MusicTrack>>()
                
                listOf("Popular Artists", "Editor's Picks").plus(genres).forEach { genre ->
                    val cached = MusicCache.getGenreTracks(genre, 40)
                    if (cached != null) {
                        genreTracks[genre] = cached
                    }
                }
                
                if (genreTracks.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        genreTracks = genreTracks,
                        genres = listOf("Popular Artists", "Editor's Picks") + genres
                    )
                }
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load MORE trending music from YouTube with improved filtering
                val trending = YouTubeMusicService.fetchTrendingMusic(100)
                MusicCache.cacheTrendingMusic(100, trending)
                
                // Load tracks from popular artists for better quality
                val popularArtistTracks = YouTubeMusicService.fetchPopularArtistMusic(50)
                MusicCache.cacheGenreTracks("Popular Artists", 50, popularArtistTracks)
                
                // Load tracks by ALL popular genres for maximum variety
                val genres = YouTubeMusicService.getPopularGenres()
                val genreTracks = mutableMapOf<String, List<MusicTrack>>()
                
                // Add popular artist tracks as first genre section
                if (popularArtistTracks.isNotEmpty()) {
                    genreTracks["Popular Artists"] = popularArtistTracks
                }
                
                // Load MORE tracks for each genre
                genres.forEach { genre ->
                    val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 40)
                    if (tracks.isNotEmpty()) {
                        genreTracks[genre] = tracks
                        MusicCache.cacheGenreTracks(genre, 40, tracks)
                    }
                }
                
                // Get top picks for additional variety
                val topPicks = try {
                    YouTubeMusicService.fetchTopPicks(30)
                } catch (e: Exception) {
                    emptyList()
                }
                
                if (topPicks.isNotEmpty()) {
                    genreTracks["Editor's Picks"] = topPicks
                    MusicCache.cacheGenreTracks("Editor's Picks", 30, topPicks)
                }
                
                Log.d("MusicViewModel", "Loaded ${trending.size} trending tracks, ${popularArtistTracks.size} popular artist tracks, ${genreTracks.size} genre sections with ${genreTracks.values.sumOf { it.size }} total tracks")
                
                _uiState.value = _uiState.value.copy(
                    trendingSongs = trending,
                    allSongs = trending,
                    genreTracks = genreTracks,
                    genres = listOf("Popular Artists", "Editor's Picks") + genres,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading music", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load music: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun searchMusic(query: String) {
        if (query.isBlank()) {
            // Reset to trending
            _uiState.value = _uiState.value.copy(
                allSongs = _uiState.value.trendingSongs
            )
            return
        }
        
        viewModelScope.launch {
            // Check cache first
            val cached = MusicCache.getSearchResults(query)
            if (cached != null) {
                Log.d("MusicViewModel", "Search cache hit: ${cached.size} tracks")
                _uiState.value = _uiState.value.copy(
                    allSongs = cached,
                    isSearching = false
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isSearching = true)
            
            try {
                // Search with MORE results for better variety
                val results = YouTubeMusicService.searchMusic(query, 100)
                MusicCache.cacheSearchResults(query, results)
                
                Log.d("MusicViewModel", "Search found ${results.size} music tracks")
                
                _uiState.value = _uiState.value.copy(
                    allSongs = results,
                    isSearching = false
                )
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Search error", e)
                _uiState.value = _uiState.value.copy(
                    error = "Search failed: ${e.message}",
                    isSearching = false
                )
            }
        }
    }

    fun loadGenreTracks(genre: String) {
        viewModelScope.launch {
            // Check cache first
            val cached = MusicCache.getGenreTracks(genre, 100)
            if (cached != null) {
                Log.d("MusicViewModel", "Genre cache hit: ${cached.size} tracks")
                _uiState.value = _uiState.value.copy(
                    allSongs = cached,
                    selectedGenre = genre,
                    isLoading = false
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Load MORE tracks for the selected genre
                val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 100)
                MusicCache.cacheGenreTracks(genre, 100, tracks)
                
                _uiState.value = _uiState.value.copy(
                    allSongs = tracks,
                    selectedGenre = genre,
                    isLoading = false
                )
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error loading genre tracks", e)
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load tracks: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    fun retry() {
        loadMusicContent()
    }
}

data class MusicUiState(
    val trendingSongs: List<MusicTrack> = emptyList(),
    val allSongs: List<MusicTrack> = emptyList(),
    val genreTracks: Map<String, List<MusicTrack>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null
)
