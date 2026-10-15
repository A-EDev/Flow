package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.NeuroKeySpec
import io.github.aedev.flow.data.recommendation.RankTrace
import io.github.aedev.flow.data.recommendation.snapshot.HomeSnapshot
import io.github.aedev.flow.data.recommendation.snapshot.HomeSnapshotStore
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotCard
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotRelated
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotSearch
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotSeed
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotShown
import io.github.aedev.flow.data.recommendation.snapshot.SnapshotVideo
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Saves a Home load as a [HomeSnapshot] while the viewer has recording switched on in Diagnostics. */
class HomeSnapshotRecorder
    @Inject
    constructor(
        private val store: HomeSnapshotStore,
        private val preferences: PlayerPreferences,
    ) {
        private var current: HomeSnapshot? = null

        suspend fun isRecording(): Boolean = preferences.homeSnapshotRecording.first()

        internal suspend fun record(
            createdAt: Long,
            subscriptions: Set<String>,
            watched: Set<String>,
            discovery: List<Pair<String, List<Video>>>,
            related: RelatedGraphFetchResult,
            subscriptionFeed: List<Video>,
            memory: List<Video>,
            page: List<Video>,
            sourceOf: (String) -> FeedSource?,
            trace: RankTrace,
        ) {
            val state = runCatching { FlowNeuroEngine.diagnosticState() }.getOrNull() ?: return
            val snapshot =
                HomeSnapshot(
                    appVersion = BuildConfig.VERSION_NAME,
                    keySpec = NeuroKeySpec.VERSION,
                    createdAt = createdAt,
                    brain = state.brain,
                    categories = state.categories,
                    coWatch = state.coWatch,
                    subscriptions = subscriptions.toList(),
                    watched = watched.toList(),
                    discovery = discovery.map { (query, videos) -> SnapshotSearch(query, videos.map(SnapshotVideo::of)) },
                    seeds = related.seedInputs.filter { it.id in related.seedIds }.map { SnapshotSeed(it.id, it.title) },
                    related =
                        related.candidates.map {
                            SnapshotRelated(
                                video = SnapshotVideo.of(it.video),
                                seedId = it.seedId,
                                seedScore = it.seedScore,
                                graphRank = it.graphRank,
                                seedCluster = it.seedCluster,
                                seedResultCount = it.seedResultCount,
                                hitCount = it.hitCount,
                            )
                        },
                    subscriptionFeed = subscriptionFeed.map(SnapshotVideo::of),
                    memory = memory.map(SnapshotVideo::of),
                    page = page.map { SnapshotCard(it.id, sourceOf(it.id)?.name, trace.isPadding(it.id)) },
                )
            current = snapshot
            store.save(snapshot)
        }

        /** Rewrites the current snapshot with the feed as it now stands. */
        internal suspend fun recordShown(
            videos: List<Video>,
            origins: Map<String, HomeCardOrigin>,
            trace: RankTrace,
        ) {
            val snapshot = current ?: return
            if (!isRecording()) return
            val shown =
                videos.map {
                    SnapshotShown(
                        SnapshotVideo.of(it),
                        origins[it.id]?.kind?.name,
                        trace.isPadding(it.id),
                        origins[it.id]?.detail,
                    )
                }
            current = snapshot.copy(shown = shown)
            store.save(snapshot.copy(shown = shown))
        }

        suspend fun clear() = store.clear()
    }
