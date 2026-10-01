/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.ChannelUploadsClient
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Channels the viewer keeps watching without subscribing, and their latest uploads. Reads watch
 * history, likes and subscriptions; fetches only on [refresh], which Home calls after first paint.
 */
@Singleton
class ChannelMemoryRepository
    @Inject
    constructor(
        private val viewHistory: ViewHistory,
        private val likedVideos: LikedVideosRepository,
        private val subscriptions: SubscriptionRepository,
        private val uploadsClient: ChannelUploadsClient,
    ) {
        suspend fun remembered(now: Long = System.currentTimeMillis()): List<RememberedChannel> =
            withContext(PerformanceDispatcher.diskIO) {
                val brain = FlowNeuroEngine.getBrainSnapshot()
                rememberedFrom(brain, now)
            }

        /** The cached uploads of remembered channels; local only, safe for the first paint. */
        suspend fun storedUploads(now: Long = System.currentTimeMillis()): List<Video> =
            withContext(PerformanceDispatcher.diskIO) {
                val brain = FlowNeuroEngine.getBrainSnapshot()
                ChannelMemory.uploads(rememberedFrom(brain, now), brain.channelMemory, now)
            }

        /**
         * Fetches the Videos tab of remembered channels not fetched in the last six hours, at most
         * ten, and returns the uploads that arrived. Channels a deadline cut off are tried next load.
         */
        suspend fun refresh(now: Long = System.currentTimeMillis()): List<Video> {
            val brain = FlowNeuroEngine.getBrainSnapshot()
            val remembered = withContext(PerformanceDispatcher.diskIO) { rememberedFrom(brain, now) }
            val queue = ChannelMemory.refreshQueue(remembered, brain.channelMemory, now)
            if (queue.isEmpty()) return emptyList()

            val fetched = ConcurrentHashMap<RememberedChannel, List<Video>>()
            val permits = Semaphore(ChannelMemoryParams.REFRESH_CONCURRENCY)
            withTimeoutOrNull(ChannelMemoryParams.REFRESH_DEADLINE_MS) {
                coroutineScope {
                    queue
                        .map { channel ->
                            async(PerformanceDispatcher.networkIO) {
                                permits.withPermit {
                                    uploadsClient
                                        .latest(
                                            FeedItemOwner(id = channel.channelId, name = channel.name),
                                            ChannelMemoryParams.UPLOADS_PER_CHANNEL,
                                        ).getOrNull()
                                        ?.let { fetched[channel] = it }
                                }
                            }
                        }.awaitAll()
                }
            }
            if (fetched.isEmpty()) return emptyList()

            // Saved like learning, not bookkeeping: these uploads cost a network round to fetch again.
            FlowNeuroEngine.updateChannelMemory(bookkeeping = false) { state ->
                fetched.entries.fold(state) { acc, (channel, videos) ->
                    ChannelMemory.recordUploads(acc, channel.channelId, channel.name, videos, now)
                }
            }
            val updated = FlowNeuroEngine.getBrainSnapshot().channelMemory
            return ChannelMemory.uploads(fetched.keys.toList(), updated, now)
        }

        suspend fun forget(channelId: String) =
            FlowNeuroEngine.updateChannelMemory(bookkeeping = false) { ChannelMemory.forget(it, channelId, System.currentTimeMillis()) }

        suspend fun clear() =
            FlowNeuroEngine.updateChannelMemory(bookkeeping = false) { ChannelMemory.clear(it, System.currentTimeMillis()) }

        private suspend fun rememberedFrom(
            brain: UserBrain,
            now: Long,
        ): List<RememberedChannel> {
            val history = viewHistory.getRecentVideoHistory(ChannelMemoryParams.HISTORY_LOOKBACK, includeShorts = false)
            val likes = likedVideos.getLikedVideosFlow().first()
            val subscribed = subscriptions.getAllSubscriptions().first().mapTo(HashSet()) { it.channelId }
            val exclusions =
                ChannelMemoryExclusions(
                    subscribed = subscribed,
                    blocked = brain.blockedChannels,
                    notInterestedAt = brain.suppressedChannels,
                )
            return ChannelMemory.remembered(
                ChannelMemory.engagements(history, likes, brain.channelMemory),
                brain.channelMemory,
                exclusions,
                now,
            )
        }
    }
