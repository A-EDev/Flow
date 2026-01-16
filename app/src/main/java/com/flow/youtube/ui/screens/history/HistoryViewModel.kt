package com.flow.youtube.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.local.VideoHistoryEntry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context, isMusic: Boolean = false) {
        viewHistory = ViewHistory.getInstance(context)
        
        // Load history
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val flow = if (isMusic) viewHistory.getMusicHistoryFlow() else viewHistory.getVideoHistoryFlow()
            flow.collect { history ->
                _uiState.update { 
                    it.copy(
                        historyEntries = history,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            viewHistory.clearAllHistory()
        }
    }
    
    fun removeFromHistory(videoId: String) {
        viewModelScope.launch {
            viewHistory.clearVideoHistory(videoId)
        }
    }
}

data class HistoryUiState(
    val historyEntries: List<VideoHistoryEntry> = emptyList(),
    val isLoading: Boolean = false
)
