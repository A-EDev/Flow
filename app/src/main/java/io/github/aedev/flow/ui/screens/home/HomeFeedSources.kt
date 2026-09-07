package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSelector
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.data.repository.YouTubeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val RELATED_TTL_MS = 45L * 60L * 1000L
private const val RELATED_FETCH_CONCURRENCY = 3
private const val RELATED_FETCH_TIMEOUT_MS = 4_000L
private const val SAVED_SEED_COOLDOWN_MS = 3L * 60L * 60L * 1000L

internal data class RelatedGraphFetchResult(
    val seedInputs: List<GraphSeedInput>,
    val seedIds: List<String>,
    val candidates: List<GraphCandidate>,
    val fetchedPerSeed: Map<String, Int>,
)

/**
 * Seed discovery and related-graph retrieval for the home feed.
 *
 * Unscoped, so its per-seed caches live and die with the ViewModel that injects it — the same
 * lifetime the fields had when they were members of it.
 */
class HomeFeedSources
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val homeFeedCache: HomeFeedCacheRepository,
        private val viewHistory: ViewHistory,
        private val likedVideosRepository: LikedVideosRepository,
        private val playlistRepository: PlaylistRepository,
    ) {
        private data class CachedRelated(
            val videos: List<Video>,
            val ts: Long,
        )

        private val relatedCache = ConcurrentHashMap<String, CachedRelated>()
        private val relatedSemaphore = Semaphore(RELATED_FETCH_CONCURRENCY)
        private val savedSeedCooldown = ConcurrentHashMap<String, Long>()

        suspend fun historySeedInputs(): List<GraphSeedInput> = graphSeedInputsFromHistory(viewHistory.getVideoHistoryFlow().first())

        internal suspend fun gatherSavedSeedSources(): SavedSeedSources {
            val historySeeds =
                runCatching {
                    graphSeedInputsFromHistory(viewHistory.getVideoHistoryFlow().first())
                }.getOrElse { emptyList() }
            val likedSeeds =
                runCatching {
                    likedVideosRepository.getLikedVideosFlow().first().map {
                        GraphSeedInput(
                            id = it.videoId,
                            title = it.title,
                            channelId = "",
                            source = GraphSeedSource.LIKED,
                            engagementWeight = 1.0,
                            timestamp = it.likedAt,
                            durationSec = 0,
                            percentWatched = 0.0,
                        )
                    }
                }.getOrElse { emptyList() }
            val playlistSeeds =
                runCatching {
                    playlistRepository.getSavedVideoPlaylistVideos().map {
                        GraphSeedInput(
                            id = it.id,
                            title = it.title,
                            channelId = it.channelId,
                            source = GraphSeedSource.PLAYLIST,
                            engagementWeight = 1.0,
                            timestamp = it.timestamp,
                            durationSec = it.duration,
                            percentWatched = 0.0,
                        )
                    }
                }.getOrElse { emptyList() }
            return SavedSeedSources(historySeeds, likedSeeds, playlistSeeds)
        }

        fun activeSavedSeedCooldown(now: Long): Set<String> {
            savedSeedCooldown.entries.removeAll { now - it.value > SAVED_SEED_COOLDOWN_MS }
            return savedSeedCooldown.keys.toHashSet()
        }

        fun markSeedsUsed(
            seedIds: Collection<String>,
            now: Long,
        ) {
            seedIds.forEach { savedSeedCooldown[it] = now }
        }

        private suspend fun fetchRelatedVideos(
            seedId: String,
            filters: suspend () -> HomeFeedCacheFilters,
        ): List<Video> {
            val ts = System.currentTimeMillis()
            relatedCache[seedId]?.takeIf { ts - it.ts < RELATED_TTL_MS }?.videos?.let { return it }

            val persisted =
                runCatching {
                    homeFeedCache.loadRelated(seedId, filters(), ts)
                }.getOrElse { emptyList() }
            if (persisted.isNotEmpty()) {
                relatedCache[seedId] = CachedRelated(persisted, ts)
                return persisted
            }

            return (
                relatedSemaphore.withPermit {
                    withTimeoutOrNull(RELATED_FETCH_TIMEOUT_MS) {
                        repository.getRelatedCandidates(seedId)
                    } ?: emptyList()
                }
            ).also {
                relatedCache[seedId] = CachedRelated(it, ts)
                homeFeedCache.saveRelated(seedId, it, ts)
            }
        }

        /** Expands seed video ids into related (/next) neighbours with graph metadata. */
        internal suspend fun fetchRelatedGraph(
            seedInputs: List<GraphSeedInput>,
            seedIds: List<String>,
            filters: suspend () -> HomeFeedCacheFilters,
        ): RelatedGraphFetchResult =
            coroutineScope {
                if (seedIds.isEmpty()) {
                    return@coroutineScope RelatedGraphFetchResult(seedInputs, seedIds, emptyList(), emptyMap())
                }
                val now = System.currentTimeMillis()
                val seedMetadata =
                    seedInputs
                        .filter { it.id in seedIds }
                        .groupBy { it.id }
                        .mapValues { (_, seeds) -> seeds.maxBy { GraphSeedSelector.scoreSeed(it, now) } }

                val perSeed =
                    seedIds
                        .map { seedId ->
                            async {
                                val seed = seedMetadata[seedId]
                                val videos = fetchRelatedVideos(seedId, filters)
                                val seedScore = seed?.let { GraphSeedSelector.scoreSeed(it, now) } ?: 0.0
                                val seedCluster = seed?.let { GraphSeedSelector.clusterKey(it) } ?: "misc"
                                val candidates =
                                    videos.mapIndexed { index, video ->
                                        GraphCandidate(
                                            video = video,
                                            seedId = seedId,
                                            seedScore = seedScore,
                                            graphRank = index,
                                            seedCluster = seedCluster,
                                            seedResultCount = videos.size,
                                        )
                                    }
                                seedId to candidates
                            }
                        }.awaitAll()
                val rawCandidates = perSeed.flatMap { it.second }
                RelatedGraphFetchResult(
                    seedInputs = seedInputs,
                    seedIds = seedIds,
                    candidates = mergeGraphCandidates(rawCandidates),
                    fetchedPerSeed = perSeed.associate { (seedId, candidates) -> seedId to candidates.size },
                )
            }

        internal suspend fun fetchRelatedGraphCandidates(
            seedInputs: List<GraphSeedInput>,
            seedIds: List<String>,
            filters: suspend () -> HomeFeedCacheFilters,
        ): List<GraphCandidate> = fetchRelatedGraph(seedInputs, seedIds, filters).candidates
    }

/** Videos currently on screen, usable as related-graph seeds for load-more. */
internal fun feedSeedInputs(
    videos: List<Video>,
    now: Long,
    max: Int,
): List<GraphSeedInput> =
    videos
        .asSequence()
        .filter { !it.isShort && it.id.isNotBlank() }
        .take(max)
        .map { video ->
            GraphSeedInput(
                id = video.id,
                title = video.title,
                channelId = video.channelId,
                source = GraphSeedSource.FEED,
                engagementWeight = 0.6,
                timestamp = now,
                durationSec = video.duration,
                percentWatched = 0.0,
            )
        }.toList()
