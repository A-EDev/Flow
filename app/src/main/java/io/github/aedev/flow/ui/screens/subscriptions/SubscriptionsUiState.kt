package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.SubscriptionGroup
import io.github.aedev.flow.data.model.Video

enum class SubscriptionSortMode {
    DEFAULT,
    NAME_ASC,
    RECENTLY_UPDATED,
    ;

    companion object {
        fun fromStorage(value: String?): SubscriptionSortMode = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

data class SubscriptionsUiState(
    val subscribedChannels: List<Channel> = emptyList(),
    val recentVideos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false,
    val isFullWidthView: Boolean = false,
    val sortMode: SubscriptionSortMode = SubscriptionSortMode.DEFAULT,
    val isShortsShelfEnabled: Boolean = true,
    val notificationStates: Map<String, Boolean> = emptyMap(),
    val groups: List<SubscriptionGroup> = emptyList(),
    val selectedGroupName: String? = null,
    val refreshProcessedChannels: Int = 0,
    val refreshTotalChannels: Int = 0,
    val lastRefreshTime: Long = 0L,
    val lastRefreshText: String? = null,
    val lastRefreshVideoCount: Int = 0,
    val showLastRefreshVideoCount: Boolean = true,
    val showSubscriptionVideos: Boolean = true,
    val showSubscriptionShorts: Boolean = true,
    val showSubscriptionLive: Boolean = true,
    val excludedShortsChannelIds: Set<String> = emptySet(),
    /** Channels the last refresh could not reach at all; surfaced instead of silently showing less. */
    val failedChannelIds: Set<String> = emptySet(),
    val failedChannelReasons: Map<String, String> = emptyMap(),
) {
    /** Display names for [failedChannelIds], falling back to the raw id for an unknown channel. */
    val failedChannelNames: List<String>
        get() {
            if (failedChannelIds.isEmpty()) return emptyList()
            val namesById = subscribedChannels.associate { it.id to it.name }
            return failedChannelIds
                .map { id -> namesById[id]?.takeIf { it.isNotBlank() } ?: id }
                .sorted()
        }
}
