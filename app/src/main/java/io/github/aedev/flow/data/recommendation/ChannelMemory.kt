/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import kotlin.math.ln
import kotlin.math.pow

/** Tuning for the channel memory lane, in one place. */
internal object ChannelMemoryParams {
    const val MIN_REAL_WATCHES = 2
    const val HALF_LIFE_DAYS = 30.0
    const val MAX_CHANNELS = 20
    const val SCORE_FLOOR = 0.5
    const val LIKE_WEIGHT = 1.0
    const val REFRESH_AFTER_MS = 6L * 60L * 60L * 1000L
    const val MAX_REFRESH_PER_LOAD = 10
    const val REFRESH_CONCURRENCY = 3
    const val REFRESH_DEADLINE_MS = 15_000L
    const val UPLOAD_WINDOW_MS = 14L * 24L * 60L * 60L * 1000L
    const val UPLOADS_PER_CHANNEL = 10
    const val HISTORY_LOOKBACK = 400
    const val MAX_ENTRIES = 200
    const val NOT_INTERESTED_WINDOW_MS = 14L * 24L * 60L * 60L * 1000L
}

/** One cached upload of a remembered channel. */
data class RememberedUpload(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val durationSec: Int,
    val publishedAt: Long,
    val uploadDate: String,
    val viewCount: Long,
)

/**
 * What the brain keeps about a channel: when its uploads were last fetched and what they were, and
 * the viewer's explicit verdicts on it. How much the viewer watches it is read from watch history.
 */
data class ChannelMemoryEntry(
    val name: String = "",
    val lastFetchedAt: Long = 0L,
    val uploads: List<RememberedUpload> = emptyList(),
    val rejectedAt: Long = 0L,
    val forgottenAt: Long = 0L,
)

data class ChannelMemoryState(
    val entries: Map<String, ChannelMemoryEntry> = emptyMap(),
    /** Watches before this time no longer count, after "Clear channel memory". */
    val clearedAt: Long = 0L,
)

data class ChannelEngagement(
    val channelId: String,
    val name: String,
    val realWatches: Int,
    val watchedSeconds: Long,
    val likes: Int,
    val lastRealWatchAt: Long,
)

data class RememberedChannel(
    val channelId: String,
    val name: String,
    val score: Double,
)

/** Who must never be remembered, whatever the viewer watched. */
data class ChannelMemoryExclusions(
    val subscribed: Set<String> = emptySet(),
    val blocked: Set<String> = emptySet(),
    /** "Not interested" on a video of the channel: channelId to when. */
    val notInterestedAt: Map<String, Long> = emptyMap(),
)

/**
 * Channels the viewer keeps watching without subscribing. Pure: every input is passed in, so the
 * rules are tested without a brain, a database or the network.
 */
internal object ChannelMemory {
    /** Real watches, watched time and likes per channel, counting only what came after a reset. */
    fun engagements(
        history: List<VideoHistoryEntry>,
        likes: List<LikedVideoInfo>,
        state: ChannelMemoryState,
    ): Map<String, ChannelEngagement> {
        fun since(channelId: String) = maxOf(state.clearedAt, state.entries[channelId]?.forgottenAt ?: 0L)

        val watches =
            history
                .asSequence()
                .filter { isYouTubeChannel(it.channelId) && !it.isShort && !it.isLocal && !it.isMusic }
                .filter { it.timestamp > since(it.channelId) }
                .filter { GraphSeedSelector.isRealWatch(it.duration / 1000L, it.progressPercentage.toDouble()) }
                .groupBy { it.channelId }
        val likesByChannel =
            likes
                .filter { !it.isMusic && isYouTubeChannel(it.channelId) }
                .filter { it.likedAt > since(it.channelId!!) }
                .groupingBy { it.channelId!! }
                .eachCount()

        return watches.mapValues { (channelId, entries) ->
            ChannelEngagement(
                channelId = channelId,
                name = entries.maxBy { it.timestamp }.channelName,
                realWatches = entries.size,
                watchedSeconds = entries.sumOf { it.position / 1000L },
                likes = likesByChannel[channelId] ?: 0,
                lastRealWatchAt = entries.maxOf { it.timestamp },
            )
        }
    }

    // Downloads and recovered files can carry a placeholder channel such as "local", which would
    // merge unrelated videos into one fake channel.
    private fun isYouTubeChannel(channelId: String?): Boolean = channelId != null && channelId.startsWith("UC")

    fun qualifies(engagement: ChannelEngagement): Boolean =
        engagement.realWatches >= ChannelMemoryParams.MIN_REAL_WATCHES ||
            (engagement.realWatches >= 1 && engagement.likes >= 1)

    /** Engagement, halving every [ChannelMemoryParams.HALF_LIFE_DAYS] since the last real watch. */
    fun score(
        engagement: ChannelEngagement,
        now: Long,
    ): Double {
        val raw =
            engagement.realWatches +
                ChannelMemoryParams.LIKE_WEIGHT * engagement.likes +
                ln(1.0 + engagement.watchedSeconds / 1800.0)
        val ageDays = (now - engagement.lastRealWatchAt).coerceAtLeast(0L) / 86_400_000.0
        return raw * 0.5.pow(ageDays / ChannelMemoryParams.HALF_LIFE_DAYS)
    }

    /**
     * A rejection excludes the channel until the viewer comes back to it: a real watch after the
     * rejection means they still want it.
     */
    private fun isRejected(
        engagement: ChannelEngagement,
        entry: ChannelMemoryEntry?,
        exclusions: ChannelMemoryExclusions,
        now: Long,
    ): Boolean {
        val rejectedAt = entry?.rejectedAt ?: 0L
        val notInterestedAt =
            exclusions.notInterestedAt[engagement.channelId]
                ?.takeIf { now - it < ChannelMemoryParams.NOT_INTERESTED_WINDOW_MS }
                ?: 0L
        return maxOf(rejectedAt, notInterestedAt) > engagement.lastRealWatchAt
    }

    fun remembered(
        engagements: Map<String, ChannelEngagement>,
        state: ChannelMemoryState,
        exclusions: ChannelMemoryExclusions,
        now: Long,
    ): List<RememberedChannel> =
        engagements.values
            .asSequence()
            .filter(::qualifies)
            .filter { it.channelId !in exclusions.subscribed && it.channelId !in exclusions.blocked }
            .filterNot { isRejected(it, state.entries[it.channelId], exclusions, now) }
            .map { RememberedChannel(it.channelId, it.name, score(it, now)) }
            .filter { it.score >= ChannelMemoryParams.SCORE_FLOOR }
            .sortedByDescending { it.score }
            .take(ChannelMemoryParams.MAX_CHANNELS)
            .toList()

    /** The best channels whose uploads are older than the refresh window, at most a load's worth. */
    fun refreshQueue(
        remembered: List<RememberedChannel>,
        state: ChannelMemoryState,
        now: Long,
    ): List<RememberedChannel> =
        remembered
            .filter { now - (state.entries[it.channelId]?.lastFetchedAt ?: 0L) >= ChannelMemoryParams.REFRESH_AFTER_MS }
            .take(ChannelMemoryParams.MAX_REFRESH_PER_LOAD)

    /** Cached uploads of [remembered] channels from the last two weeks, as feed candidates. */
    fun uploads(
        remembered: List<RememberedChannel>,
        state: ChannelMemoryState,
        now: Long,
    ): List<Video> =
        remembered.flatMap { channel ->
            state.entries[channel.channelId]
                ?.uploads
                .orEmpty()
                .filter { now - it.publishedAt in 0..ChannelMemoryParams.UPLOAD_WINDOW_MS }
                .map { it.toVideo(channel) }
        }

    fun recordUploads(
        state: ChannelMemoryState,
        channelId: String,
        name: String,
        videos: List<Video>,
        now: Long,
    ): ChannelMemoryState {
        val uploads =
            videos
                .asSequence()
                .filter { !it.isShort && !it.isUpcoming && !it.isLive && it.membersOnlyText == null }
                .filter { it.timestamp > 0L && now - it.timestamp in 0..ChannelMemoryParams.UPLOAD_WINDOW_MS }
                .sortedByDescending { it.timestamp }
                .take(ChannelMemoryParams.UPLOADS_PER_CHANNEL)
                .map {
                    RememberedUpload(it.id, it.title, it.thumbnailUrl, it.duration, it.timestamp, it.uploadDate, it.viewCount)
                }.toList()
        val entry = (state.entries[channelId] ?: ChannelMemoryEntry()).copy(name = name, lastFetchedAt = now, uploads = uploads)
        return state.withEntry(channelId, entry)
    }

    fun recordRejection(
        state: ChannelMemoryState,
        channelId: String,
        name: String,
        now: Long,
    ): ChannelMemoryState {
        if (channelId.isBlank()) return state
        val entry = state.entries[channelId] ?: ChannelMemoryEntry(name = name)
        return state.withEntry(channelId, entry.copy(rejectedAt = now, uploads = emptyList()))
    }

    fun forget(
        state: ChannelMemoryState,
        channelId: String,
        now: Long,
    ): ChannelMemoryState {
        val entry = state.entries[channelId] ?: ChannelMemoryEntry()
        return state.withEntry(channelId, entry.copy(forgottenAt = now, uploads = emptyList()))
    }

    fun clear(
        state: ChannelMemoryState,
        now: Long,
    ): ChannelMemoryState =
        ChannelMemoryState(
            entries = state.entries.filterValues { it.rejectedAt > 0L }.mapValues { it.value.copy(uploads = emptyList()) },
            clearedAt = now,
        )

    private fun ChannelMemoryState.withEntry(
        channelId: String,
        entry: ChannelMemoryEntry,
    ): ChannelMemoryState {
        val updated = entries + (channelId to entry)
        if (updated.size <= ChannelMemoryParams.MAX_ENTRIES) return copy(entries = updated)
        val kept =
            updated.entries
                .sortedByDescending { maxOf(it.value.lastFetchedAt, it.value.rejectedAt, it.value.forgottenAt) }
                .take(ChannelMemoryParams.MAX_ENTRIES)
                .associate { it.key to it.value }
        return copy(entries = kept)
    }

    private fun RememberedUpload.toVideo(channel: RememberedChannel) =
        Video(
            id = videoId,
            title = title,
            channelName = channel.name,
            channelId = channel.channelId,
            thumbnailUrl = thumbnailUrl,
            duration = durationSec,
            viewCount = viewCount,
            uploadDate = uploadDate,
            timestamp = publishedAt,
        )
}
