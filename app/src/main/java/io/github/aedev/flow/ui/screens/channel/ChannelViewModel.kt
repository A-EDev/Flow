package io.github.aedev.flow.ui.screens.channel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.SubscriptionGroup
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.distinctByNonBlankKey
import io.github.aedev.flow.data.model.toUiModel
import io.github.aedev.flow.data.paging.ChannelShortsPagingSource
import io.github.aedev.flow.data.paging.ChannelTabPagingSource
import io.github.aedev.flow.data.shorts.ShortsContentFilter
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelItem
import io.github.aedev.flow.innertube.pages.channel.ChannelOwner
import io.github.aedev.flow.innertube.pages.channel.ChannelSortOption
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.CommunityPost
import io.github.aedev.flow.ui.youtubeChannelBrowseId
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelViewModel
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
        private val subscriptionRepository: SubscriptionRepository,
        private val shortsContentFilter: ShortsContentFilter,
        private val subscriptionGroupDao: SubscriptionGroupDao,
    ) : ViewModel() {
        val subscriptionGroups: StateFlow<List<SubscriptionGroup>> =
            subscriptionGroupDao
                .getAllGroups()
                .map { entities -> entities.map { it.toUiModel() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(GROUPS_SUBSCRIPTION_TIMEOUT_MS), emptyList())

        fun setChannelInGroup(
            groupName: String,
            channelId: String,
            inGroup: Boolean,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val group = subscriptionGroupDao.getAllGroupsOnce().firstOrNull { it.name == groupName } ?: return@launch
                val members = group.toUiModel().channelIds.toMutableSet()
                if (inGroup) members.add(channelId) else members.remove(channelId)
                subscriptionGroupDao.updateGroup(group.copy(channelIds = members.joinToString(",")))
            }
        }

        fun createGroupWithChannel(
            groupName: String,
            channelId: String,
        ) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val name = groupName.trim()
                if (name.isEmpty() || subscriptionGroupDao.exists(name)) return@launch
                val order = subscriptionGroupDao.getAllGroupsOnce().size
                subscriptionGroupDao.insertGroup(
                    SubscriptionGroupEntity(name = name, channelIds = channelId, sortOrder = order),
                )
            }
        }

        private val _uiState = MutableStateFlow(ChannelUiState())
        val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()
        private val communityController = ChannelCommunityController(viewModelScope)
        internal val communityUiState: StateFlow<ChannelCommunityUiState> = communityController.state

        // Paging flow for channel videos with infinite scroll
        private val _shortsPagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
        val shortsPagingFlow: StateFlow<Flow<PagingData<Video>>?> = _shortsPagingFlow.asStateFlow()

        /**
         * The Shorts tab's sort bar, as YouTube sent it — labels already localised, order as shown
         * on the web. Empty until the first page lands, and on a channel whose Shorts tab offers no
         * sorting, in which case the screen shows no chips (#547).
         */
        private val _shortsSorts = MutableStateFlow<List<String>>(emptyList())
        val shortsSorts: StateFlow<List<String>> = _shortsSorts.asStateFlow()

        private val _selectedShortsSort = MutableStateFlow(0)
        val selectedShortsSort: StateFlow<Int> = _selectedShortsSort.asStateFlow()

        private var shortsSortTokens: List<String> = emptyList()
        private var shortsChannelId: String = ""

        /**
         * Rebuilds the grid in the chosen order. The queue reads the same index off the nav route,
         * so swipe order follows what the grid is showing rather than diverging from it.
         */
        fun selectShortsSort(index: Int) {
            if (index == _selectedShortsSort.value || index !in shortsSortTokens.indices) return
            _selectedShortsSort.value = index
            buildShortsPager(shortsChannelId, shortsSortTokens.getOrNull(index).takeIf { index != 0 })
        }

        private fun buildShortsPager(
            channelId: String,
            sortToken: String?,
        ) {
            if (channelId.isBlank()) return
            _shortsPagingFlow.value =
                Pager(
                    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                    pagingSourceFactory = {
                        ChannelShortsPagingSource(
                            channelId = channelId,
                            sortToken = sortToken,
                            onPageLoaded = { sorts, _ ->
                                if (sorts.isNotEmpty()) {
                                    shortsSortTokens = sorts.map { it.token }
                                    _shortsSorts.value = sorts.map { it.label }
                                }
                            },
                        )
                    },
                ).flow.cachedIn(viewModelScope)
        }

        private val _playlistsPagingFlow = MutableStateFlow<Flow<PagingData<io.github.aedev.flow.data.model.Playlist>>?>(null)
        val playlistsPagingFlow: StateFlow<Flow<PagingData<io.github.aedev.flow.data.model.Playlist>>?> = _playlistsPagingFlow.asStateFlow()

        private fun channelOwner(): ChannelOwner {
            val state = _uiState.value
            return ChannelOwner(
                id = state.channelId.orEmpty(),
                name = state.header?.title.orEmpty(),
                avatarUrl = state.header?.avatarUrl.orEmpty(),
            )
        }

        // Eagerly loaded full video lists (all pages) for filter support
        private val _videosAll = MutableStateFlow<List<Video>>(emptyList())
        val videosAll: StateFlow<List<Video>> = _videosAll.asStateFlow()

        private val _liveAll = MutableStateFlow<List<Video>>(emptyList())
        val liveAll: StateFlow<List<Video>> = _liveAll.asStateFlow()

        private val _isLoadingAllVideos = MutableStateFlow(false)
        val isLoadingAllVideos: StateFlow<Boolean> = _isLoadingAllVideos.asStateFlow()

        var listScrollIndex: Int = 0
            private set
        var listScrollOffset: Int = 0
            private set

        fun saveScrollPosition(
            index: Int,
            offset: Int,
        ) {
            listScrollIndex = index
            listScrollOffset = offset
        }

        private enum class TabKind { Videos, Live }

        private var videosJob: Job? = null
        private var liveJob: Job? = null
        private var videosSortTokens: List<String> = emptyList()
        private var liveSortTokens: List<String> = emptyList()

        /**
         * The Videos tab's sort bar, straight from YouTube. Replaces the old client-side
         * Latest/Popular/Oldest: sorting an accumulated list can only order the pages already
         * fetched, so "Oldest" on a large channel really meant "oldest of the first few hundred".
         */
        private val _videosSorts = MutableStateFlow<List<String>>(emptyList())
        val videosSorts: StateFlow<List<String>> = _videosSorts.asStateFlow()

        private val _selectedVideosSort = MutableStateFlow(0)
        val selectedVideosSort: StateFlow<Int> = _selectedVideosSort.asStateFlow()

        private val _liveSorts = MutableStateFlow<List<String>>(emptyList())
        val liveSorts: StateFlow<List<String>> = _liveSorts.asStateFlow()

        private val _selectedLiveSort = MutableStateFlow(0)
        val selectedLiveSort: StateFlow<Int> = _selectedLiveSort.asStateFlow()

        fun selectVideosSort(index: Int) {
            if (index == _selectedVideosSort.value || index !in videosSortTokens.indices) return
            _selectedVideosSort.value = index
            videosJob?.cancel()
            videosJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    loadSortedTab(TabKind.Videos, videosSortTokens.getOrNull(index).takeIf { index != 0 })
                }
        }

        fun selectLiveSort(index: Int) {
            if (index == _selectedLiveSort.value || index !in liveSortTokens.indices) return
            _selectedLiveSort.value = index
            liveJob?.cancel()
            liveJob =
                viewModelScope.launch(PerformanceDispatcher.networkIO) {
                    loadSortedTab(TabKind.Live, liveSortTokens.getOrNull(index).takeIf { index != 0 })
                }
        }

        companion object {
            private const val TAG = "ChannelViewModel"
            private const val GROUPS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

            /** Delay between page fetches — keeps request pattern human-like, avoids 429s */
            private const val PAGE_DELAY_MS = 800L

            /** Safety cap: stops loading beyond this many pages (~1500 videos) */
            private const val MAX_PAGES = 50
        }

        /**
         *  PERFORMANCE OPTIMIZED: Load channel with timeout protection
         */
        fun loadChannel(channelUrl: String) {
            val browseId = youtubeChannelBrowseId(channelUrl)
            if (browseId == null) {
                _uiState.update { it.copy(error = appContext.getString(R.string.error_invalid_channel_url), isLoading = false) }
                return
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isLoading = true, error = null) }

                YouTube.channel(browseId).fold(
                    onSuccess = { page ->
                        val header = page.header
                        val channelId = header.id.ifBlank { browseId }
                        _uiState.update {
                            it.copy(
                                channelId = channelId,
                                header = header,
                                tabs = page.tabs,
                                isLoading = false,
                            )
                        }
                        communityController.reset(channelId, header.title, header.avatarUrl)
                        if (_uiState.value.selectedTab == ChannelTabKind.Posts) {
                            communityController.ensurePostsLoaded()
                        }
                        loadSubscriptionState(channelId)
                        loadChannelTabs()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load channel", error)
                        _uiState.update {
                            it.copy(
                                error = error.message ?: appContext.getString(R.string.error_failed_to_load_channel),
                                isLoading = false,
                            )
                        }
                    },
                )
            }
        }

        private suspend fun loadChannelTabs() {
            val state = _uiState.value
            val channelId = state.channelId ?: return

            if (state.hasTab(ChannelTabKind.Videos)) {
                videosJob?.cancel()
                videosJob =
                    viewModelScope.launch(PerformanceDispatcher.networkIO) {
                        loadSortedTab(TabKind.Videos, sortToken = null)
                    }
            }

            if (state.hasTab(ChannelTabKind.Shorts) && shortsContentFilter.isEnabled()) {
                shortsChannelId = channelId
                _selectedShortsSort.value = 0
                buildShortsPager(shortsChannelId, sortToken = null)
            }

            if (state.hasTab(ChannelTabKind.Live)) {
                liveJob?.cancel()
                liveJob =
                    viewModelScope.launch(PerformanceDispatcher.networkIO) {
                        loadSortedTab(TabKind.Live, sortToken = null)
                    }
            }

            state.tabParams(ChannelTabKind.Playlists)?.takeIf { state.hasTab(ChannelTabKind.Playlists) }?.let { params ->
                val owner = channelOwner()
                _playlistsPagingFlow.value =
                    Pager(
                        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                        pagingSourceFactory = {
                            ChannelTabPagingSource(
                                browseId = channelId,
                                params = params,
                                kind = ChannelTabKind.Playlists,
                                owner = owner,
                            )
                        },
                    ).flow
                        .map { data -> data.filter { it is ChannelItem.PlaylistItem }.map { (it as ChannelItem.PlaylistItem).playlist } }
                        .cachedIn(viewModelScope)
            }
        }

        private fun loadSubscriptionState(channelId: String) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                subscriptionRepository.getSubscription(channelId).collect { subscription ->
                    _uiState.update {
                        it.copy(
                            isSubscribed = subscription != null,
                            isNotificationsEnabled = subscription?.isNotificationEnabled ?: false,
                        )
                    }
                }
            }
        }

        fun toggleSubscription() {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                val header = state.header ?: return@launch
                val channelName = header.title
                val channelThumbnail = header.avatarUrl

                if (state.isSubscribed) {
                    // Unsubscribe
                    subscriptionRepository.unsubscribe(channelId)
                } else {
                    // Subscribe
                    val subscription =
                        ChannelSubscription(
                            channelId = channelId,
                            channelName = channelName,
                            channelThumbnail = channelThumbnail,
                            subscribedAt = System.currentTimeMillis(),
                        )
                    subscriptionRepository.subscribe(subscription)
                }
            }
        }

        fun unsubscribe() {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                subscriptionRepository.unsubscribe(channelId)
            }
        }

        fun setNotificationState(enabled: Boolean) {
            viewModelScope.launch(PerformanceDispatcher.diskIO) {
                val state = _uiState.value
                val channelId = state.channelId ?: return@launch
                subscriptionRepository.updateNotificationState(channelId, enabled)
            }
        }

        fun selectTab(kind: ChannelTabKind) {
            _uiState.update { it.copy(selectedTab = kind) }
            if (kind == ChannelTabKind.Posts) communityController.ensurePostsLoaded()
        }

        fun openCommunityPostComments(post: CommunityPost) = communityController.openComments(post)

        fun closeCommunityPostComments() = communityController.closeComments()

        fun retryCommunityPosts() = communityController.retryPosts()

        fun loadMoreCommunityPosts() = communityController.loadMorePosts()

        fun loadMoreCommunityPostComments() = communityController.loadMoreComments()

        fun loadCommunityCommentReplies(comment: Comment) = communityController.loadReplies(comment, append = false)

        fun loadMoreCommunityCommentReplies(comment: Comment) = communityController.loadReplies(comment, append = true)

        // ── Channel search ────────────────────────────────────────────────────────

        fun setSearchActive(active: Boolean) {
            _uiState.update {
                it.copy(
                    searchActive = active,
                    searchQuery = if (!active) "" else it.searchQuery,
                    searchResults = if (!active) emptyList() else it.searchResults,
                    searchErrorLog = null,
                )
            }
        }

        fun searchInChannel(query: String) {
            val channelId = _uiState.value.channelId ?: return
            val header = _uiState.value.header ?: return
            val trimmed = query.trim()

            _uiState.update {
                it.copy(
                    searchQuery = query,
                    searchErrorLog = null,
                )
            }

            if (trimmed.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                return
            }

            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                _uiState.update { it.copy(isSearching = true) }
                try {
                    val result =
                        YouTube.channelSearch(
                            channelId = channelId,
                            channelName = header.title,
                            channelThumbnailUrl = header.avatarUrl,
                            query = trimmed,
                        )
                    result.fold(
                        onSuccess = { page ->
                            _uiState.update {
                                it.copy(
                                    searchResults = page.videos.distinctByNonBlankKey(Video::id),
                                    searchContinuation = page.continuation,
                                    isSearching = false,
                                    searchErrorLog = null,
                                )
                            }
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Channel search failed", e)
                            _uiState.update {
                                it.copy(
                                    isSearching = false,
                                    searchErrorLog =
                                        buildChannelRequestErrorLog(
                                            operation = "channel_search",
                                            channelId = channelId,
                                            query = trimmed,
                                            error = e,
                                        ),
                                )
                            }
                        },
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Channel search error", e)
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchErrorLog =
                                buildChannelRequestErrorLog(
                                    operation = "channel_search",
                                    channelId = channelId,
                                    query = trimmed,
                                    error = e,
                                ),
                        )
                    }
                }
            }
        }

        /**
         * Loads a channel tab in the order YouTube itself would show it, paging until the tab runs
         * out or [MAX_PAGES] is hit.
         *
         * [sortToken] is a chip from the tab's own sort bar, so the list arrives sorted rather than
         * being re-ordered here. That is the difference that matters: a client-side sort can only
         * order what has already been fetched, which quietly turned "Oldest" into "oldest of the
         * pages loaded so far" on any channel bigger than the page cap.
         */
        private suspend fun loadSortedTab(
            kind: TabKind,
            sortToken: String?,
        ) {
            val channelId = _uiState.value.channelId ?: return
            val channelName =
                _uiState.value.header
                    ?.title
                    .orEmpty()
            val avatar =
                _uiState.value.header
                    ?.avatarUrl
                    .orEmpty()
            val target = if (kind == TabKind.Videos) _videosAll else _liveAll
            val isLive = kind == TabKind.Live

            _isLoadingAllVideos.value = true
            target.value = emptyList()
            try {
                val first =
                    when {
                        sortToken != null -> {
                            continueTab(kind, sortToken, channelId, channelName, avatar)
                        }

                        isLive -> {
                            YouTube.channelLiveStreams(channelId, channelName, avatar)
                        }

                        else -> {
                            YouTube.channelVideos(channelId, channelName, avatar)
                        }
                    }.getOrNull() ?: return

                publishSorts(kind, first.sorts)

                val accumulated = mutableListOf<Video>()
                val seen = mutableSetOf<String>()

                fun absorb(videos: List<Video>) {
                    videos.forEach { video -> if (seen.add(video.id)) accumulated += video }
                    target.value = accumulated.toList()
                }

                absorb(first.videos)

                var continuation = first.continuation
                var pagesLoaded = 1
                while (continuation != null && pagesLoaded < MAX_PAGES) {
                    // Throttle subsequent pages — keeps the request pattern human-like
                    // and avoids triggering YouTube's burst rate-limiting (429s)
                    delay(PAGE_DELAY_MS)
                    val more =
                        continueTab(kind, continuation, channelId, channelName, avatar).getOrNull() ?: break
                    if (more.videos.isEmpty() && more.continuation == null) break
                    absorb(more.videos)
                    continuation = more.continuation
                    pagesLoaded++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Rate-limited or network error — user keeps whatever loaded so far
                Log.w(TAG, "Page loading stopped after rate limit or error", e)
            } finally {
                _isLoadingAllVideos.value = false
            }
        }

        /**
         * Live streams have their own continuation entry point. Paging them through the plain video
         * one loses the live marker, so every past broadcast past the first page would render as an
         * ordinary upload.
         */
        private suspend fun continueTab(
            kind: TabKind,
            continuation: String,
            channelId: String,
            channelName: String,
            avatar: String,
        ) = if (kind == TabKind.Live) {
            YouTube.channelLiveStreamsContinuation(continuation, channelId, channelName, avatar)
        } else {
            YouTube.channelVideosContinuation(continuation, channelId, channelName, avatar)
        }

        private fun publishSorts(
            kind: TabKind,
            sorts: List<ChannelSortOption>,
        ) {
            if (sorts.isEmpty()) return
            if (kind == TabKind.Videos) {
                videosSortTokens = sorts.map { it.token }
                _videosSorts.value = sorts.map { it.label }
            } else {
                liveSortTokens = sorts.map { it.token }
                _liveSorts.value = sorts.map { it.label }
            }
        }
    }
