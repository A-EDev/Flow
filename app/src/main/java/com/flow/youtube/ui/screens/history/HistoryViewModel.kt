package com.flow.youtube.ui.screens.history

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.AppDatabase
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.local.VideoHistoryEntry
import com.flow.youtube.data.local.entity.WatchHistoryEntity
import com.flow.youtube.data.local.entity.VideoEntity
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class HistoryViewModel : ViewModel() {

    private lateinit var viewHistory: ViewHistory
    private val youTubeRepository = YouTubeRepository.getInstance()
    private val isEnriching = AtomicBoolean(false)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun initialize(context: Context, isMusic: Boolean = false) {
        viewHistory = ViewHistory.getInstance(context)
        val videoDao = AppDatabase.getDatabase(context).videoDao()
        val watchHistoryDao = AppDatabase.getDatabase(context).watchHistoryDao()

        // Load history and enrich any entries that are missing metadata
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val flow = if (isMusic) viewHistory.getMusicHistoryFlow() else viewHistory.getVideoHistoryFlow()
            flow.collect { history ->
                val enriched = history.map { entry ->
                    var e = entry

                    val needsEnrichment = e.title.isEmpty() || e.channelName.isEmpty()
                    val dbVideo = if (needsEnrichment) videoDao.getVideo(e.videoId) else null

                    if (e.thumbnailUrl.isEmpty()) {
                        e = e.copy(
                            thumbnailUrl = dbVideo?.thumbnailUrl
                                ?.takeIf { it.isNotEmpty() }
                                ?: "https://i.ytimg.com/vi/${e.videoId}/hqdefault.jpg"
                        )
                    }

                    if (dbVideo != null) {
                        if (e.title.isEmpty() && dbVideo.title.isNotEmpty())
                            e = e.copy(title = dbVideo.title)
                        if (e.channelName.isEmpty() && dbVideo.channelName.isNotEmpty())
                            e = e.copy(channelName = dbVideo.channelName, channelId = dbVideo.channelId)
                        if (dbVideo.thumbnailUrl.isNotEmpty() &&
                            e.thumbnailUrl.startsWith("https://i.ytimg.com/vi/${e.videoId}/hqdefault"))
                            e = e.copy(thumbnailUrl = dbVideo.thumbnailUrl)
                    }
                    e
                }

                _uiState.update {
                    it.copy(historyEntries = enriched, isLoading = false)
                }

                val stubs = enriched.filter { it.title.isEmpty() || it.channelName.isEmpty() }.take(30)
                if (stubs.isNotEmpty()) {
                    enrichFromApi(stubs, videoDao, watchHistoryDao)
                }
            }
        }
    }

    private fun enrichFromApi(
        stubs: List<VideoHistoryEntry>,
        videoDao: com.flow.youtube.data.local.dao.VideoDao,
        watchHistoryDao: com.flow.youtube.data.local.dao.WatchHistoryDao
    ) {
        if (!isEnriching.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stubs.chunked(5).forEach { chunk ->
                    chunk.forEach { stub ->
                        try {
                            val video = youTubeRepository.getVideo(stub.videoId) ?: return@forEach
                            val e = VideoEntity.fromDomain(video)
                            videoDao.insertVideoOrIgnore(e) 
                            videoDao.updateVideoMetadata(
                                id = e.id,
                                title = e.title,
                                channelName = e.channelName,
                                channelId = e.channelId,
                                thumbnailUrl = e.thumbnailUrl,
                                duration = e.duration,
                                viewCount = e.viewCount,
                                uploadDate = e.uploadDate,
                                description = e.description,
                                channelThumbnailUrl = e.channelThumbnailUrl
                            )
                            watchHistoryDao.upsert(
                                WatchHistoryEntity(
                                    videoId      = stub.videoId,
                                    position     = stub.position,
                                    duration     = video.duration * 1000L,
                                    timestamp    = stub.timestamp,
                                    title        = video.title,
                                    thumbnailUrl = video.thumbnailUrl.ifEmpty {
                                        "https://i.ytimg.com/vi/${stub.videoId}/hqdefault.jpg"
                                    },
                                    channelName  = video.channelName,
                                    channelId    = video.channelId,
                                    isMusic      = stub.isMusic
                                )
                            )
                        } catch (_: Exception) { /* skip individual failures */ }
                    }
                    delay(300L)
                }
            } finally {
                isEnriching.set(false)
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

