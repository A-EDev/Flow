package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.paging.ChannelSearch
import io.github.aedev.flow.data.recommendation.ChannelMemoryRepository
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.NeighbourChannel
import io.github.aedev.flow.data.recommendation.NeighbourChannelsRepository
import io.github.aedev.flow.data.recommendation.RememberedChannel
import io.github.aedev.flow.data.repository.ChannelAvatarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 400L
private const val MAX_QUICK_SEARCHES = 6

// Quick searches name a subject; a word every subject uses ("buy") finds channels of anything (#907).
private const val QUICK_SEARCH_MIN_SPECIFICITY = 0.5

private data class LocalSuggestions(
    val topics: List<String>,
    val remembered: List<RememberedChannel>,
    val neighbours: List<NeighbourChannel>,
)

@Immutable
internal data class DiscoverChannelsState(
    val quickSearches: List<String> = emptyList(),
    /** Channels watched without subscribing, read from the device only; null until read. */
    val suggestions: List<RememberedChannel>? = null,
    /** Channels several of the viewer's own sit next to, read from the device only (#907). */
    val neighbours: List<NeighbourChannel> = emptyList(),
    /** Avatars of the suggestions, local ones first and fetched ones as they arrive. */
    val suggestionAvatars: Map<String, String> = emptyMap(),
    /** Search results for the typed query; null while a search runs. */
    val results: List<Channel>? = emptyList(),
    val subscribedIds: Set<String> = emptySet(),
    val notifyingIds: Set<String> = emptySet(),
)

/**
 * The channel search onboarding offers, kept in Settings. Suggestions come from local data; the
 * network is used once per settled query and for suggestion avatars the device does not have.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class DiscoverChannelsViewModel
    @Inject
    constructor(
        private val channelSearch: ChannelSearch,
        private val subscriptions: SubscriptionRepository,
        private val channelMemory: ChannelMemoryRepository,
        private val neighbourChannels: NeighbourChannelsRepository,
        private val avatars: ChannelAvatarRepository,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val local =
            flow {
                val brain = FlowNeuroEngine.getBrainSnapshot()
                val topics =
                    (
                        brain.preferredTopics +
                            brain.globalVector.topics.entries
                                .sortedByDescending { it.value }
                                .map { it.key }
                    ).filterNot { it in brain.blockedTopics || FlowNeuroEngine.specificity(it) < QUICK_SEARCH_MIN_SPECIFICITY }
                        .distinct()
                        .take(MAX_QUICK_SEARCHES)
                val followed = subscriptions.getAllSubscriptions().first().associate { it.channelId to it.channelName }
                emit(
                    LocalSuggestions(
                        topics,
                        runCatching { channelMemory.remembered() }.getOrDefault(emptyList()),
                        runCatching { neighbourChannels.proven(followed) }.getOrDefault(emptyList()),
                    ),
                )
            }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

        private val suggestionAvatars =
            local.flatMapLatest { local ->
                val ids = local.remembered.map { it.channelId } + local.neighbours.map { it.channelId }
                flow {
                    emit(avatars.local(ids))
                    emit(avatars.all(ids))
                }
            }

        private val results =
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { typed ->
                    if (typed.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        flow {
                            emit(null)
                            emit(channelSearch.search(typed.trim()))
                        }
                    }
                }

        val state: StateFlow<DiscoverChannelsState> =
            combine(local, suggestionAvatars.onStart { emit(emptyMap()) }, results, subscriptions.getAllSubscriptions()) {
                local,
                avatarsById,
                found,
                subscribed,
                ->
                DiscoverChannelsState(
                    quickSearches = local.topics,
                    suggestions = local.remembered,
                    neighbours = local.neighbours,
                    suggestionAvatars = avatarsById,
                    results = found,
                    subscribedIds = subscribed.mapTo(HashSet()) { it.channelId },
                    notifyingIds = subscribed.filter { it.isNotificationEnabled }.mapTo(HashSet()) { it.channelId },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverChannelsState())

        fun onQueryChange(query: String) {
            _query.value = query
        }

        fun toggleSubscription(
            channelId: String,
            name: String,
            thumbnailUrl: String,
        ) {
            val subscribed = channelId in state.value.subscribedIds
            viewModelScope.launch {
                if (subscribed) {
                    subscriptions.unsubscribe(channelId)
                } else {
                    subscriptions.subscribe(ChannelSubscription(channelId = channelId, channelName = name, channelThumbnail = thumbnailUrl))
                }
            }
        }

        fun setNotifications(
            channelId: String,
            enabled: Boolean,
        ) {
            viewModelScope.launch { subscriptions.updateNotificationState(channelId, enabled) }
        }
    }
