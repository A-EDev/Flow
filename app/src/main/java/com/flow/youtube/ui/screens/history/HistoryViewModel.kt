package com.flow.youtube.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.AppDatabase
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
        val videoDao = AppDatabase.getDatabase(context).videoDao()
        
        // Load history and enrich any entries that are missing metadata
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val flow = if (isMusic) viewHistory.getMusicHistoryFlow() else viewHistory.getVideoHistoryFlow()
            flow.collect { history ->
                val enriched = history.map { entry ->
                    var e = entry

                    // If any metadata is missing, do a single Room DB lookup
                    val needsEnrichment = e.thumbnailUrl.isEmpty() || e.title.isEmpty() || e.channelName.isEmpty()
                    val dbVideo = if (needsEnrichment) videoDao.getVideo(e.videoId) else null

                    // 1. Recover thumbnail — prefer DB URL, otherwise use YouTube CDN fallback
                    if (e.thumbnailUrl.isEmpty()) {
                        e = e.copy(
                            thumbnailUrl = dbVideo?.thumbnailUrl
                                ?.takeIf { it.isNotEmpty() }
                                ?: "https://i.ytimg.com/vi/${e.videoId}/hqdefault.jpg"
                        )
                    }

                    // 2. Recover title / channelName from Room DB if missing
                    if (dbVideo != null) {
                        if (e.title.isEmpty()) e = e.copy(title = dbVideo.title)
                        if (e.channelName.isEmpty()) e = e.copy(
                            channelName = dbVideo.channelName,
                            channelId = dbVideo.channelId.takeIf { it.isNotEmpty() } ?: e.channelId
                        )
                        // Upgrade the CDN fallback to the real stored thumbnail if available
                        if (dbVideo.thumbnailUrl.isNotEmpty() &&
                            e.thumbnailUrl.startsWith("https://i.ytimg.com/vi/${e.videoId}/hqdefault")) {
                            e = e.copy(thumbnailUrl = dbVideo.thumbnailUrl)
                        }
                    }

                    e
                }
                _uiState.update { 
                    it.copy(
                        historyEntries = enriched,
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
