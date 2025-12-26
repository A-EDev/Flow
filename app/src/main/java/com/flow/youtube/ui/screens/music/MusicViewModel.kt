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
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor() : ViewModel() {
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
                
                val genres = YouTubeMusicService.getPopularGenres()
                val genreJobs = genres.map { genre ->
                    genre to async { 
                        val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 30)
                        if (tracks.isNotEmpty()) MusicCache.cacheGenreTracks(genre, 30, tracks)
                        tracks
                    }
                }
                
                val trending = trendingJob.await()
                val newReleases = newReleasesJob.await()
                val popularArtistTracks = popularArtistsJob.await()
                
                val genreTracks = mutableMapOf<String, List<MusicTrack>>()
                if (popularArtistTracks.isNotEmpty()) genreTracks["Popular Artists"] = popularArtistTracks
                
                genreJobs.forEach { (genre, job) ->
                    val tracks = job.await()
                    if (tracks.isNotEmpty()) genreTracks[genre] = tracks
                }
                
                _uiState.value = _uiState.value.copy(
                    trendingSongs = trending,
                    newReleases = newReleases,
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
                val results = YouTubeMusicService.searchMusic(query, 60)
                MusicCache.cacheSearchResults(query, results)
                _uiState.value = _uiState.value.copy(
                    allSongs = results,
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
}

data class MusicUiState(
    val trendingSongs: List<MusicTrack> = emptyList(),
    val newReleases: List<MusicTrack> = emptyList(),
    val allSongs: List<MusicTrack> = emptyList(),
    val genreTracks: Map<String, List<MusicTrack>> = emptyMap(),
    val genres: List<String> = emptyList(),
    val selectedGenre: String? = null,
    val isLoading: Boolean = true,
    val isSearching: Boolean = false,
    val error: String? = null
)
