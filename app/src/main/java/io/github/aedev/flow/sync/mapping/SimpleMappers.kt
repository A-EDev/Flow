package io.github.aedev.flow.sync.mapping

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalLikeMeta
import io.github.aedev.flow.sync.canonical.CanonicalSubscribedChannel
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory
import io.github.aedev.flow.sync.identity.Hlc

/**
 * Pure entity ⇄ canonical mappers for the simpler collections.
 * Unit conversions live here: Android watch-history is **milliseconds**, canonical is seconds +
 * a 0..1 progress fraction. HLC for these records is a degenerate `(timestamp, 0, node)` derived
 * from the natural per-record timestamp.
 */

object WatchHistoryMapper {
    fun toCanonical(
        e: WatchHistoryEntity,
        node: String,
    ): CanonicalWatchHistory {
        val durationMs = e.duration
        val progress = if (durationMs > 0) (e.position.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0
        return CanonicalWatchHistory(
            videoId = e.videoId,
            title = e.title,
            channelName = e.channelName,
            channelId = e.channelId,
            thumbnailUrl = e.thumbnailUrl,
            watchedAtMs = e.timestamp,
            progress = progress,
            durationSeconds = if (durationMs > 0) durationMs / 1000 else 0,
            isMusic = e.isMusic,
            isShort = e.isShort,
            hlc = Hlc(e.timestamp, 0, node).encode(),
            deleted = false,
        )
    }

    fun toEntity(c: CanonicalWatchHistory): WatchHistoryEntity {
        val durationMs = c.durationSeconds * 1000
        val positionMs = (c.progress.coerceIn(0.0, 1.0) * durationMs).toLong()
        return WatchHistoryEntity(
            videoId = c.videoId,
            position = positionMs,
            duration = durationMs,
            timestamp = c.watchedAtMs,
            title = c.title,
            thumbnailUrl = c.thumbnailUrl,
            channelName = c.channelName,
            channelId = c.channelId,
            isMusic = c.isMusic,
            isShort = c.isShort,
            isLocal = false, // synced rows are never device-local files
        )
    }
}

object SubscribedChannelsMapper {
    fun toCanonical(
        s: ChannelSubscription,
        node: String,
    ) = CanonicalSubscribedChannel(
        channelId = s.channelId,
        name = s.channelName,
        avatarUrl = s.channelThumbnail,
        subscribedAtMs = s.subscribedAt,
        isMusic = s.isMusic,
        hlc = Hlc(s.subscribedAt, 0, node).encode(),
        deleted = false,
    )

    /** An unsubscribe, stamped with when it happened so it can out-rank the peer's subscribe. */
    fun tombstone(
        channelId: String,
        unsubscribedAtMs: Long,
        node: String,
    ) = CanonicalSubscribedChannel(
        channelId = channelId,
        subscribedAtMs = 0,
        hlc = Hlc(unsubscribedAtMs, 0, node).encode(),
        deleted = true,
    )

    fun toSubscription(c: CanonicalSubscribedChannel) =
        ChannelSubscription(
            channelId = c.channelId,
            channelName = c.name,
            channelThumbnail = c.avatarUrl,
            subscribedAt = c.subscribedAtMs,
            isMusic = c.isMusic,
            // lastVideoId / lastCheckTime / lastFeedFetchAt / isNotificationEnabled stay device-local:
            // they describe this device's feed bookkeeping, not the subscription itself.
        )
}

object SubscriptionsMapper {
    private const val DELIM = ","

    fun toCanonical(
        e: SubscriptionGroupEntity,
        hlc: String,
    ): CanonicalSubscriptionGroup {
        val ids =
            if (e.channelIds.isBlank()) {
                emptyList()
            } else {
                e.channelIds
                    .split(DELIM)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
        return CanonicalSubscriptionGroup(
            name = e.name,
            channelIds = ids.toSortedSet().toList(),
            sortOrder = e.sortOrder,
            hlc = hlc,
            deleted = false,
        )
    }

    fun toEntity(c: CanonicalSubscriptionGroup): SubscriptionGroupEntity =
        SubscriptionGroupEntity(
            name = c.name,
            channelIds = c.channelIds.filter { it.isNotBlank() }.joinToString(DELIM),
            sortOrder = c.sortOrder,
        )
}

object LikesMapper {
    /** Android exports only LIKED items (the order list is liked-only); state is always `liked`. */
    fun likedToCanonical(
        info: LikedVideoInfo,
        node: String,
    ): CanonicalLike =
        CanonicalLike(
            kind = if (info.isMusic) CanonicalLike.KIND_MUSIC else CanonicalLike.KIND_VIDEO,
            id = info.videoId,
            state = CanonicalLike.STATE_LIKED,
            updatedAtMs = info.likedAt,
            hlc = Hlc(info.likedAt, 0, node).encode(),
            meta =
                CanonicalLikeMeta(
                    title = info.title,
                    artist = info.channelName,
                    thumbnailUrl = info.thumbnail,
                ),
            title = info.title,
            channelName = info.channelName,
            thumbnailUrl = info.thumbnail,
        )

    fun toLikedInfo(c: CanonicalLike): LikedVideoInfo =
        LikedVideoInfo(
            videoId = c.id,
            title = c.meta.title.ifBlank { c.title },
            thumbnail = c.meta.thumbnailUrl.ifBlank { c.thumbnailUrl },
            channelName = c.meta.artist.ifBlank { c.channelName },
            likedAt = if (c.updatedAtMs > 0) c.updatedAtMs else System.currentTimeMillis(),
            isMusic = c.kind == CanonicalLike.KIND_MUSIC,
        )
}
