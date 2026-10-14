package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.local.dao.HomeFeedCacheDao
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.di.NetworkIoDispatcher
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val PARALLEL_FETCHES = 4
private const val FETCH_TIMEOUT_MS = 10_000L

/**
 * Avatars for channels known only by id and name. Cached feed rows and saved videos answer first;
 * only the rest are fetched, a few at a time, and each answer is kept for the life of the process.
 */
@Singleton
class ChannelAvatarRepository
    @Inject
    constructor(
        private val homeFeedCacheDao: HomeFeedCacheDao,
        private val videoDao: VideoDao,
        @NetworkIoDispatcher private val networkDispatcher: CoroutineDispatcher,
    ) {
        /** Channel id to avatar URL; an empty URL is a channel already tried with no avatar found. */
        private val known = ConcurrentHashMap<String, String>()

        /** What the device already has, without the network. */
        suspend fun local(channelIds: List<String>): Map<String, String> {
            val missing = channelIds.filterNot(known::containsKey)
            if (missing.isNotEmpty()) {
                (homeFeedCacheDao.channelAvatars(missing) + videoDao.channelAvatars(missing))
                    .forEach { known.putIfAbsent(it.channelId, it.channelThumbnailUrl) }
            }
            return channelIds.mapNotNull { id -> known[id]?.takeIf(String::isNotBlank)?.let { id to it } }.toMap()
        }

        /** [local], then each channel still without an avatar fetched once. */
        suspend fun all(channelIds: List<String>): Map<String, String> {
            local(channelIds)
            channelIds.filterNot(known::containsKey).chunked(PARALLEL_FETCHES).forEach { batch ->
                coroutineScope {
                    batch
                        .map { id ->
                            async(networkDispatcher) {
                                id to withTimeoutOrNull(FETCH_TIMEOUT_MS) { YouTube.channelLanding(id).getOrNull() }
                            }
                        }.awaitAll()
                        // A failed fetch is not recorded, so the next visit tries that channel again.
                        .forEach { (id, page) -> page?.let { known[id] = it.header?.avatarUrl.orEmpty() } }
                }
            }
            return channelIds.mapNotNull { id -> known[id]?.takeIf(String::isNotBlank)?.let { id to it } }.toMap()
        }
    }
