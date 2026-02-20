package com.flow.youtube.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.model.toVideo
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.data.shorts.ShortsRepository
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: com.flow.youtube.data.local.PlayerPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    
    private var viewHistory: ViewHistory? = null
    
    private val sessionWatchedTopics = mutableListOf<String>()
    
    init {
        loadFlowFeed(forceRefresh = true)
        loadHomeShorts()
    }
    

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        viewHistory = ViewHistory.getInstance(context)
        
        viewModelScope.launch {
            FlowNeuroEngine.initialize(context)
        }

        viewModelScope.launch {
            playerPreferences.homeShortsShelfEnabled.collect { enabled ->
                if (!enabled) {
                    _uiState.update { it.copy(shorts = emptyList()) }
                } else if (_uiState.value.shorts.isEmpty()) {
                    loadHomeShorts()
                }
            }
        }

        viewModelScope.launch {
            playerPreferences.continueWatchingEnabled.collect { enabled ->
                if (!enabled) {
                    _uiState.update { it.copy(continueWatchingVideos = emptyList()) }
                } else {
                    loadContinueWatching()
                }
            }
        }
    }

    private fun loadContinueWatching() {
        viewModelScope.launch {
            viewHistory?.getVideoHistoryFlow()?.collect { history ->
                val inProgress = history
                    .filter { it.progressPercentage in 3f..90f }
                    .sortedByDescending { it.timestamp }
                    .take(20)
                _uiState.update { it.copy(continueWatchingVideos = inProgress) }
            }
        }
    }
    

    private fun loadHomeShorts() {
        viewModelScope.launch {
            if (!playerPreferences.homeShortsShelfEnabled.first()) return@launch
            try {
                val shorts = shortsRepository.getHomeFeedShorts().map { it.toVideo() }
                if (shorts.isNotEmpty()) {
                    _uiState.update { it.copy(shorts = shorts) }
                }
            } catch (e: Exception) {
            }
        }
    }
    

    private fun updateVideosAndShorts(newVideos: List<Video>, append: Boolean = false) {
        val (newShorts, regularVideos) = newVideos.partition { 
            it.isShort || (it.duration in 1..80) || (it.duration == 0 && !it.isLive)
        }
        
        _uiState.update { state ->
            val updatedVideos = if (append) (state.videos + regularVideos) else regularVideos
            state.copy(
                videos = updatedVideos.distinctBy { it.id },
                shorts = (state.shorts + newShorts).distinctBy { it.id }
            )
        }
    }

    
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                discoveryQueries.clear()
                discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                currentQueryIndex = 0
                
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = "US"

                val results = supervisorScope {
                    val deferredSubs = async {
                        if (userSubs.isNotEmpty()) {
                            runCatching { 
                                repository.getSubscriptionFeed(userSubs.toList())
                            }.getOrElse { emptyList() }
                        } else emptyList()
                    }

                    val deferredDiscovery = async {
                        val queries = discoveryQueries.take(3)
                        queries.map { query ->
                            async { 
                                runCatching { 
                                    repository.searchVideos(query).first
                                }.getOrElse { emptyList() }
                            }
                        }.awaitAll().flatten()
                    }
                    
                    val deferredViral = async {
                        runCatching {
                             repository.getTrendingVideos(region).first
                        }.getOrElse { emptyList() }
                    }

                    Triple(deferredSubs.await(), deferredDiscovery.await(), deferredViral.await())
                }
                
                currentQueryIndex = 3
                
                val (rawSubs, rawDiscovery, rawViral) = results
                
                // Extract shorts from all sources for the shelf
                val feedShorts = (rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                if (feedShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + feedShorts).distinctBy { it.id })
                    }
                }
                
                // Filter to regular videos for the main feed
                val subsPool = rawSubs.filterValid()
                val discoveryPool = rawDiscovery.filterValid()
                val viralPool = rawViral.filterValid()
                
                val bestSubs = FlowNeuroEngine.rank(subsPool, userSubs).take(10)
                val bestDiscovery = FlowNeuroEngine.rank(discoveryPool, userSubs)
                    .filter { video ->
                        val isOld = video.uploadDate.contains("year") && 
                                    (video.uploadDate.filter { it.isDigit() }.toIntOrNull() ?: 0) > 4
                        
                        val isClassic = video.viewCount > 5_000_000 
                        
                        !isOld || isClassic
                    }
                    .take(15) 
                val bestViral = FlowNeuroEngine.rank(viralPool, userSubs).take(10)

                val finalMix = mutableListOf<Video>()
                val usedChannelIds = mutableSetOf<String>()
                
                val qSubs = java.util.ArrayDeque(bestSubs)
                val qDisc = java.util.ArrayDeque(bestDiscovery)
                val qViral = java.util.ArrayDeque(bestViral)
                
                while (qSubs.isNotEmpty() || qDisc.isNotEmpty() || qViral.isNotEmpty()) {
                    addUnique(qDisc.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qSubs.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qDisc.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qViral.pollFirst(), finalMix, usedChannelIds)
                }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                _uiState.update { it.copy(
                    videos = finalMix, 
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = System.currentTimeMillis()
                )}
                
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Failed to load feed") }
                 loadTrendingFallback() 
            }
        }
    }
    

    fun loadMoreVideos() {
        if (isLoadingMore) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {

                if (currentQueryIndex >= discoveryQueries.size) {
                    discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                }
                
                val queryA = discoveryQueries.getOrNull(currentQueryIndex++)
                val queryB = discoveryQueries.getOrNull(currentQueryIndex++)
                
                val searchQueries = listOfNotNull(queryA, queryB)
                
                val finalQueries = if (searchQueries.isEmpty()) listOf("Viral") else searchQueries

                val rawVideos = finalQueries.map { q ->
                   async { 
                       runCatching {
                           repository.searchVideos(q).first
                       }.getOrElse { emptyList() }
                   }
                }.awaitAll().flatten()
                
                // Extract shorts for shelf
                val moreShorts = rawVideos.extractShorts()
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + moreShorts).distinctBy { it.id })
                    }
                }
                
                val newVideos = rawVideos.filterValid()

                
                if (newVideos.isNotEmpty()) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedBatch = FlowNeuroEngine.rank(newVideos, userSubs)
                                        .shuffled()
                                        .distinctBy { it.channelId } 
                    
                    _uiState.update { state ->
                        val currentIds = state.videos.map { it.id }.toHashSet()
                        val uniqueNew = rankedBatch.filter { !currentIds.contains(it.id) }
                        state.copy(
                            videos = state.videos + uniqueNew,
                            isLoadingMore = false,
                            hasMorePages = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoadingMore = false) }
            } finally {
                isLoadingMore = false
            }
        }
    }
    

    fun loadTrendingVideos(region: String = "US") {
        if (_uiState.value.isLoading && _uiState.value.videos.isEmpty()) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage
                
                updateVideosAndShorts(videos, append = false)
                
                _uiState.update { it.copy(
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    isFlowFeed = false 
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                ) }
            }
        }
    }

    private suspend fun loadTrendingFallback() {
        val region = playerPreferences.trendingRegion.first()
        val (videos, nextPage) = repository.getTrendingVideos(region, null)
        currentPage = nextPage
        
        updateVideosAndShorts(videos, append = false)
        _uiState.update { it.copy(
            isLoading = false,
            hasMorePages = nextPage != null,
            isFlowFeed = false,
            error = null
        )}
    }
    
    fun refreshFeed() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadFlowFeed(forceRefresh = true)
    }
    
    fun retry() {
        loadFlowFeed(forceRefresh = true)
    }


    private fun addUnique(
        video: Video?, 
        targetList: MutableList<Video>, 
        usedChannels: MutableSet<String>
    ) {
        if (video == null) return
        

        if (!usedChannels.contains(video.channelId)) {
            targetList.add(video)
            usedChannels.add(video.channelId)
        }
    }
    
    private fun List<Video>.filterValid(): List<Video> {
        return this.filter { 
            // Keep regular videos (>80s or live streams)
            // Shorts are handled separately by loadHomeShorts()
            !it.isShort && 
            ((it.duration > 80) || (it.duration == 0 && it.isLive)) 
        }
    }
    
    /**
     * Filter that extracts shorts from a video list for the shelf.
     * Complements filterValid() by capturing what it discards.
     */
    private fun List<Video>.extractShorts(): List<Video> {
        return this.filter { 
            it.isShort || (it.duration in 1..80 && !it.isLive)
        }
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val continueWatchingVideos: List<com.flow.youtube.data.local.VideoHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false,
    val lastRefreshTime: Long = 0L
)