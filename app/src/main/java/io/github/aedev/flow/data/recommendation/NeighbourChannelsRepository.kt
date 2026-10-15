/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.ChannelUploadsClient
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A neighbour channel ([NeighbourChannels]) with the names of the viewer's channels that point to it. */
data class NeighbourChannel(
    val channelId: String,
    val name: String,
    val pointerNames: List<String>,
)

/**
 * Channels like the viewer's own and their latest uploads, for Home's neighbour lane and Discover
 * channels. Reads co-watch and the neighbour store; fetches only on [refresh], after first paint.
 */
@Singleton
class NeighbourChannelsRepository
    @Inject
    constructor(
        private val store: NeuroNeighbourStore,
        private val uploadsClient: ChannelUploadsClient,
    ) {
        private companion object {
            const val REFRESH_CONCURRENCY = 3
            const val REFRESH_DEADLINE_MS = 15_000L
        }

        /** The neighbours not yet dropped for being ignored, best first, named where co-watch saw their names. */
        suspend fun neighbours(
            subscribed: Map<String, String>,
            now: Long = System.currentTimeMillis(),
        ): List<NeighbourChannel> {
            val dropped = store.entries().filterValues { it.dropped }.keys
            val names = FlowNeuroEngine.coWatchChannelNames() + subscribed
            return FlowNeuroEngine
                .neighbourChannels(subscribed.keys)
                .filter { it.channelId !in dropped }
                .map { neighbour ->
                    NeighbourChannel(
                        channelId = neighbour.channelId,
                        name = names[neighbour.channelId].orEmpty(),
                        pointerNames = neighbour.pointers.mapNotNull { names[it]?.takeIf(String::isNotBlank) },
                    )
                }
        }

        /** The neighbours whose uploads were fetched and found on the viewer's subjects, for Discover channels. */
        suspend fun proven(subscribed: Map<String, String>): List<NeighbourChannel> {
            val entries = store.entries()
            return neighbours(subscribed).filter { (entries[it.channelId]?.fetchedAt ?: 0L) > 0L && it.name.isNotBlank() }
        }

        /** The stored uploads of current neighbours from the last two weeks; local only, safe for the first paint. */
        suspend fun storedUploads(
            neighbours: List<NeighbourChannel>,
            now: Long = System.currentTimeMillis(),
        ): List<Video> {
            val entries = store.entries()
            return neighbours.flatMap { neighbour ->
                entries[neighbour.channelId]
                    ?.uploads
                    .orEmpty()
                    .filter { now - it.publishedAt in 0..NeighbourChannels.UPLOAD_WINDOW_MS }
                    .map { it.toVideo(neighbour) }
            }
        }

        /** Fetches the newest uploads of the best named neighbours not fetched in six hours, at most four per load. */
        suspend fun refresh(
            neighbours: List<NeighbourChannel>,
            now: Long = System.currentTimeMillis(),
        ) {
            val entries = store.entries()
            val queue =
                neighbours
                    .filter { it.name.isNotBlank() && now - (entries[it.channelId]?.fetchedAt ?: 0L) >= NeighbourChannels.REFRESH_AFTER_MS }
                    .take(NeighbourChannels.REFRESH_PER_LOAD)
            if (queue.isEmpty()) return

            val fetched = ConcurrentHashMap<NeighbourChannel, List<Video>>()
            val permits = Semaphore(REFRESH_CONCURRENCY)
            withTimeoutOrNull(REFRESH_DEADLINE_MS) {
                coroutineScope {
                    queue
                        .map { neighbour ->
                            async(PerformanceDispatcher.networkIO) {
                                permits.withPermit {
                                    uploadsClient
                                        .latest(
                                            FeedItemOwner(id = neighbour.channelId, name = neighbour.name),
                                            NeighbourChannels.UPLOADS_PER_CHANNEL,
                                        ).getOrNull()
                                        ?.let { fetched[neighbour] = it }
                                }
                            }
                        }.awaitAll()
                }
            }
            if (fetched.isEmpty()) return
            store.update { current ->
                current +
                    fetched.map { (neighbour, videos) ->
                        val entry = current[neighbour.channelId] ?: NeuroNeighbourStore.Entry()
                        neighbour.channelId to entry.copy(name = neighbour.name, fetchedAt = now, uploads = videos.toStoredUploads(now))
                    }
            }
        }

        /** Counts one impression for each of [channelIds]; a neighbour ignored too often is dropped. */
        suspend fun noteShown(channelIds: Collection<String>) {
            if (channelIds.isEmpty()) return
            store.update { current ->
                current +
                    channelIds.distinct().map { channelId ->
                        val entry = current[channelId] ?: NeuroNeighbourStore.Entry()
                        val impressions = entry.impressions + 1
                        channelId to entry.copy(impressions = impressions, dropped = impressions >= NeighbourChannels.IGNORED_IMPRESSIONS)
                    }
            }
        }

        /** Drops neighbours none of whose fetched uploads is on the viewer's subjects: popular channels YouTube puts beside anything. */
        suspend fun dropOffTopic(channelIds: Collection<String>) {
            if (channelIds.isEmpty()) return
            store.update { current ->
                current +
                    channelIds.map { channelId -> channelId to (current[channelId] ?: NeuroNeighbourStore.Entry()).copy(dropped = true) }
            }
        }

        suspend fun clear() = store.clear()

        private fun List<Video>.toStoredUploads(now: Long) =
            asSequence()
                .filter { !it.isShort && !it.isUpcoming && !it.isLive && it.membersOnlyText == null }
                .filter { it.timestamp > 0L && now - it.timestamp in 0..NeighbourChannels.UPLOAD_WINDOW_MS }
                .sortedByDescending { it.timestamp }
                .take(NeighbourChannels.UPLOADS_PER_CHANNEL)
                .map {
                    NeuroNeighbourStore.StoredUpload(
                        it.id,
                        it.title,
                        it.thumbnailUrl,
                        it.duration,
                        it.timestamp,
                        it.uploadDate,
                        it.viewCount,
                    )
                }.toList()

        private fun NeuroNeighbourStore.StoredUpload.toVideo(neighbour: NeighbourChannel) =
            Video(
                id = id,
                title = title,
                channelName = neighbour.name,
                channelId = neighbour.channelId,
                thumbnailUrl = thumbnailUrl,
                duration = duration,
                viewCount = viewCount,
                uploadDate = uploadDate,
                timestamp = publishedAt,
            )
    }
