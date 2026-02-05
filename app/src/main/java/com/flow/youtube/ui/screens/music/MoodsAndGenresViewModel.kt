package com.flow.youtube.ui.screens.music

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.pages.MoodAndGenres
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodsAndGenresViewModel @Inject constructor() : ViewModel() {
    
    private val _moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)
    val moodAndGenres: StateFlow<List<MoodAndGenres>?> = _moodAndGenres.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    init {
        loadMoodAndGenres()
    }
    
    private fun loadMoodAndGenres() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                YouTube.moodAndGenres()
                    .onSuccess { result ->
                        Log.d("MoodsGenresVM", "Loaded ${result.size} mood/genre categories")
                        _moodAndGenres.value = result
                        _isLoading.value = false
                    }
                    .onFailure { exception ->
                        Log.e("MoodsGenresVM", "Failed to load moods and genres", exception)
                        _error.value = exception.message ?: "Failed to load moods & genres"
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                Log.e("MoodsGenresVM", "Exception loading moods and genres", e)
                _error.value = e.message ?: "An error occurred"
                _isLoading.value = false
            }
        }
    }
    
    fun retry() {
        loadMoodAndGenres()
    }
}
