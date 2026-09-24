package io.github.aedev.flow.data.engagement

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight singleton event bus for feed-visible state changes.
 * Emitted by QuickActionsViewModel, observed by HomeViewModel / ShortsViewModel
 * to instantly strip blocked/disliked content from the cached feed.
 */
object FeedInvalidationBus {
    sealed class Event {
        data class ChannelBlocked(
            val channelId: String,
            val videoId: String,
        ) : Event()

        data class NotInterested(
            val videoId: String,
            val channelId: String,
        ) : Event()

        data class MarkedWatched(
            val videoId: String,
        ) : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }
}
