package com.flow.youtube.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load trending music from YouTube with improved filtering
                val trending = YouTubeMusicService.fetchTrendingMusic(50)
                
                // Load tracks from popular artists for better quality
                val popularArtistTracks = YouTubeMusicService.fetchPopularArtistMusic(30)
                
                // Load tracks by popular genres
                val genres = YouTubeMusicService.getPopularGenres().take(5)
                val genreTracks = mutableMapOf<String, List<MusicTrack>>()
                
                // Add popular artist tracks as first genre section
                if (popularArtistTracks.isNotEmpty()) {
                    genreTracks["Popular Artists"] = popularArtistTracks
                }
                
                genres.forEach { genre ->
                    val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 20)
                    if (tracks.isNotEmpty()) {
                        genreTracks[genre] = tracks
                    }
                }
                
                Log.d("MusicViewModel", "Loaded ${trending.size} trending tracks, ${popularArtistTracks.size} popular artist tracks, ${genreTracks.size} genre sections")
                
                _uiState.value = _uiState.value.copy(
                    trendingSongs = trending,
                    allSongs = trending,
                    genreTracks = genreTracks,
                    genres = listOf("Popular Artists") + genres,
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
            _uiState.value = _uiState.value.copy(isSearching = true)
            
            try {
                val results = YouTubeMusicService.searchMusic(query, 50)
                
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val tracks = YouTubeMusicService.fetchMusicByGenre(genre, 50)
                
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
