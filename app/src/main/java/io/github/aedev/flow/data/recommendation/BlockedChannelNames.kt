package io.github.aedev.flow.data.recommendation

import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.dao.CacheDao
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.subscriptions.ChannelRssClient
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Names blocked channels for Hidden from you (#1325). A block saves the name it was made with; for
 * older blocks, and ones that arrived by sync or restore, the device's own data is read first and
 * the channel's RSS feed is asked once for whatever is still missing.
 */
@Singleton
class BlockedChannelNames
    @Inject
    constructor(
        private val store: BlockedChannelNameStore,
        private val subscriptions: SubscriptionRepository,
        private val videoDao: VideoDao,
        private val cacheDao: CacheDao,
        private val rss: ChannelRssClient,
    ) {
        private companion object {
            const val PARALLEL_FETCHES = 4
            const val FETCH_TIMEOUT_MS = 10_000L

            // Under SQLite's bound-variable limit.
            const val QUERY_CHUNK = 500
        }

        // Channels the network could not name this session; they are not asked again until the next one.
        private val unnamed = ConcurrentHashMap.newKeySet<String>()
        private val resolving = Mutex()

        suspend fun remember(
            channelId: String,
            name: String,
        ) = store.remember(channelId, name)

        suspend fun forget(channelId: String) = store.forget(channelId)

        /** Names for [ids] that the device already has, without any network call. */
        suspend fun known(ids: Set<String>): Map<String, String> {
            if (ids.isEmpty()) return emptyMap()
            return withContext(PerformanceDispatcher.diskIO) {
                val names = HashMap<String, String>()
                val chunks = ids.toList().chunked(QUERY_CHUNK)
                chunks.forEach { chunk ->
                    runCatching { cacheDao.subscriptionFeedChannelNames(chunk) }
                        .getOrDefault(emptyList())
                        .forEach { names[it.channelId] = it.channelName }
                    runCatching { videoDao.channelNames(chunk) }
                        .getOrDefault(emptyList())
                        .forEach { names[it.channelId] = it.channelName }
                }
                runCatching { subscriptions.getAllSubscriptions().first() }
                    .getOrDefault(emptyList())
                    .forEach { if (it.channelId in ids && it.channelName.isNotBlank()) names[it.channelId] = it.channelName }
                runCatching { FlowNeuroEngine.getSavedBrainSnapshot().channelMemory.entries }
                    .getOrDefault(emptyMap())
                    .forEach { (id, entry) -> if (id in ids && entry.name.isNotBlank()) names[id] = entry.name }
                store.names().forEach { (id, name) -> if (id in ids) names[id] = name }
                names
            }
        }

        /**
         * Fetches the names [missing] channels lack, a few at a time, saving each round and handing it
         * to [onNames]. One backfill runs at a time; a second call while one runs returns at once.
         */
        suspend fun resolve(
            missing: Collection<String>,
            blocked: Set<String>,
            onNames: suspend (Map<String, String>) -> Unit,
        ) {
            if (!resolving.tryLock()) return
            try {
                fetchNamesInRounds(missing.filterNot { it in unnamed }, PARALLEL_FETCHES, ::fetchName) { names, failed ->
                    unnamed += failed
                    store.save(names, blocked)
                    onNames(names)
                }
            } finally {
                resolving.unlock()
            }
        }

        private suspend fun fetchName(channelId: String): String? =
            withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                rss.fetch(channelId).getOrNull()?.channelName
            }
    }

/**
 * Asks [fetch] for each of [ids], [parallel] at a time, and hands each round's names and the ids it
 * could not name to [onRound]. A round that names nothing at all looks like no connection rather
 * than gone channels, so it ends the pass unreported and leaves the rest for another time.
 */
internal suspend fun fetchNamesInRounds(
    ids: List<String>,
    parallel: Int,
    fetch: suspend (String) -> String?,
    onRound: suspend (names: Map<String, String>, failed: List<String>) -> Unit,
) {
    for (round in ids.distinct().chunked(parallel)) {
        val answers = coroutineScope { round.map { id -> async { id to fetch(id)?.trim().orEmpty() } }.awaitAll() }
        val names = answers.filter { it.second.isNotEmpty() }.toMap()
        if (names.isEmpty()) return
        onRound(names, answers.filter { it.second.isEmpty() }.map { it.first })
    }
}
