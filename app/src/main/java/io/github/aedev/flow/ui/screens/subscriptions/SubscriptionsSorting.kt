package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video

internal fun SubscriptionSortMode.labelRes(): Int =
    when (this) {
        SubscriptionSortMode.DEFAULT -> R.string.subscriptions_sort_default
        SubscriptionSortMode.NAME_ASC -> R.string.subscriptions_sort_name
        SubscriptionSortMode.RECENTLY_UPDATED -> R.string.subscriptions_sort_recent
    }

internal fun sortSubscriptions(
    channels: List<Channel>,
    sortMode: SubscriptionSortMode,
    recentVideos: List<Video>,
): List<Channel> =
    when (sortMode) {
        SubscriptionSortMode.DEFAULT -> {
            channels
        }

        SubscriptionSortMode.NAME_ASC -> {
            channels.sortedBy { it.name.lowercase() }
        }

        SubscriptionSortMode.RECENTLY_UPDATED -> {
            val latestUploadByChannel =
                recentVideos
                    .groupBy { it.channelId }
                    .mapValues { (_, videos) -> videos.maxOf { it.timestamp } }
            channels.sortedByDescending { latestUploadByChannel[it.id] ?: 0L }
        }
    }

/**
 * Video channels lead the quick-access row and music channels follow, so the two kinds stay
 * grouped no matter which sort the user picked.
 */
internal fun quickAccessOrder(channels: List<Channel>): List<Channel> =
    (channels.filterNot { it.isMusic } + channels.filter { it.isMusic }).distinctBy(Channel::id)

/**
 * The feed refers to a channel by id, url, or a url that merely ends with the id, so a lookup that
 * fails still has to produce something navigable.
 */
internal fun resolveChannel(
    channels: List<Channel>,
    channelRef: String,
): Channel =
    channels.firstOrNull { channel ->
        channel.id == channelRef || channel.url == channelRef || channelRef.endsWith(channel.id)
    } ?: Channel(
        id = channelRef.substringAfterLast('/'),
        name = "",
        thumbnailUrl = "",
        subscriberCount = 0L,
        url = channelRef,
    )
