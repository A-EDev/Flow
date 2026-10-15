/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 * Test-source-set only — never shipped in the APK.
 */

package io.github.aedev.flow.data.recommendation.eval

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.NeuroCategoryStore
import io.github.aedev.flow.data.recommendation.NeuroCoWatchStore
import io.github.aedev.flow.data.recommendation.NeuroScoring
import io.github.aedev.flow.data.recommendation.NeuroStorage
import io.github.aedev.flow.data.recommendation.RankTrace
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.recommendation.snapshot.HomeSnapshot
import io.github.aedev.flow.data.recommendation.toUserBrain
import io.github.aedev.flow.ui.screens.home.FeedSource
import io.github.aedev.flow.ui.screens.home.GraphCandidate
import io.github.aedev.flow.ui.screens.home.assembleHomeFeed
import io.github.aedev.flow.ui.screens.home.buildHomeFeedLanes
import io.github.aedev.flow.ui.screens.home.dynamicFreshSubSlots
import io.github.aedev.flow.ui.screens.home.feedTasteProfile
import io.github.aedev.flow.ui.screens.home.storedSubscriptionVideos
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Replays a recorded Home load through the real ranking and Home assembly, with jitter off and
 * every video's age kept, so one feed can be measured before and after a change (#907).
 *
 * Shown cards are judged on-topic by [Labels]: a video listed there, else its channel.
 */
internal object HomeReplay {
    private val json = Json { ignoreUnknownKeys = true }

    /** Which videos and channels the viewer would want; anything else counts as off-topic. */
    data class Labels(
        val onTopicVideos: Set<String> = emptySet(),
        val offTopicVideos: Set<String> = emptySet(),
        val onTopicChannels: Set<String> = emptySet(),
    ) {
        fun isOnTopic(video: Video): Boolean =
            when (video.id) {
                in onTopicVideos -> true
                in offTopicVideos -> false
                else -> video.channelId in onTopicChannels
            }
    }

    data class Metrics(
        val cards: Int,
        val onTopicTop10: Double,
        val onTopicTop20: Double,
        val onTopicByLane: Map<FeedSource, Double>,
        val cardsByLane: Map<FeedSource, Int>,
        val paddingShare: Double,
        val categoryCoverage: Double,
        val genericKeyShare: Double,
    ) {
        fun render(name: String): String =
            "$name: cards $cards | on-topic top 10 ${f(onTopicTop10)}, top 20 ${f(onTopicTop20)}" +
                " | padding ${f(paddingShare)} | category coverage ${f(categoryCoverage)} | generic keys ${f(genericKeyShare)}" +
                " | lanes " + onTopicByLane.entries.joinToString { (lane, share) -> "${lane.name} ${f(share)} of ${cardsByLane[lane]}" }

        private fun f(value: Double) = "%.3f".format(value)
    }

    fun read(file: File): HomeSnapshot {
        val text =
            if (file.name.endsWith(".gz")) {
                GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            } else {
                file.readText()
            }
        return json.decodeFromString(text)
    }

    fun brainOf(snapshot: HomeSnapshot): UserBrain =
        json.decodeFromJsonElement<NeuroStorage.SerializableBrain>(snapshot.brain).toUserBrain()

    fun replay(
        snapshot: HomeSnapshot,
        labels: Labels,
        now: Long = System.currentTimeMillis(),
    ): Metrics =
        runBlocking {
            val shift = now - snapshot.createdAt

            fun List<io.github.aedev.flow.data.recommendation.snapshot.SnapshotVideo>.videos() = map { it.toVideo(shift) }

            val brain = brainOf(snapshot)
            val categories = NeuroCategoryStore(mockk(relaxed = true)).apply { restore(snapshot.categories) }
            val coWatch = NeuroCoWatchStore(mockk(relaxed = true)).apply { restore(snapshot.coWatch) }
            val engine = NeuroLearningBenchmark.engine(brain, NeuroLearningBenchmark.NO_JITTER, categories, coWatch)
            val subs = snapshot.subscriptions.toSet()
            val storedFeed = snapshot.subscriptionFeed.videos()
            val discovery = snapshot.discovery.flatMap { it.videos.videos() }
            val related =
                snapshot.related.map {
                    GraphCandidate(
                        it.video.toVideo(shift),
                        it.seedId,
                        it.seedScore,
                        it.graphRank,
                        it.seedCluster,
                        it.seedResultCount,
                        it.hitCount,
                    )
                }
            val trace = RankTrace()
            val lanes =
                buildHomeFeedLanes(
                    rawSubs = storedFeed.storedSubscriptionVideos(now),
                    rawDiscovery = discovery,
                    rawMemory = snapshot.memory.videos(),
                    rawRelated = related,
                    rssFeed = storedFeed,
                    watched = snapshot.watched.toSet(),
                    exclusions = engine.feedExclusions(now),
                    isRecentlyShown = { id -> NeuroScoring.isRecentlySeen(brain.feedHistory[id], now) },
                    taste = feedTasteProfile(brain, engine.getPersona(brain)),
                    now = now,
                    freshSlotTarget = dynamicFreshSubSlots(subs.size),
                    subAvatarMap = emptyMap(),
                    rank = { engine.rank(it, subs, trace = trace) },
                    rankRelevant = { engine.rank(it, subs, NeuroScoring.LANE_RELEVANCE_FLOOR, trace) },
                    share = engine::shareByInterest,
                    searchOf = snapshot.discovery
                        .asReversed()
                        .flatMap { search ->
                            search.videos.map { it.id to search.query }
                        }.toMap()::get,
                )
            val mix = assembleHomeFeed(lanes, emptySet(), subs.size, brain.totalInteractions)
            engine.shutdown()

            val page = mix.videos
            val sourceOf = mix.sourceMix.items.associate { it.video.id to it.source }
            val candidates = (storedFeed + discovery + related.map { it.video }).distinctBy { it.id }

            fun share(videos: List<Video>) = if (videos.isEmpty()) 0.0 else videos.count(labels::isOnTopic).toDouble() / videos.size
            val byLane = page.groupBy { sourceOf[it.id] ?: FeedSource.SUBS }
            Metrics(
                cards = page.size,
                onTopicTop10 = share(page.take(10)),
                onTopicTop20 = share(page.take(20)),
                onTopicByLane = byLane.mapValues { (_, videos) -> share(videos) },
                cardsByLane = byLane.mapValues { (_, videos) -> videos.size },
                paddingShare = if (page.isEmpty()) 0.0 else page.count { trace.isPadding(it.id) }.toDouble() / page.size,
                categoryCoverage =
                    if (candidates.isEmpty()) {
                        0.0
                    } else {
                        candidates.count { it.category != null || categories.categoryOf(it.channelId) != null }.toDouble() / candidates.size
                    },
                genericKeyShare = Issue907Scenarios.genericKeyShare(brain),
            )
        }

    /** The page the app showed when it recorded [snapshot], judged by the same [labels]. */
    fun recordedOnTopicTop20(
        snapshot: HomeSnapshot,
        labels: Labels,
    ): Double {
        val videos =
            (snapshot.subscriptionFeed + snapshot.discovery.flatMap { it.videos } + snapshot.related.map { it.video } + snapshot.memory)
                .associateBy { it.id }
        val shown = snapshot.page.take(20).mapNotNull { videos[it.id]?.toVideo() }
        return if (shown.isEmpty()) 0.0 else shown.count(labels::isOnTopic).toDouble() / shown.size
    }

    /** The whole feed as the viewer scrolled it when [snapshot] was recorded: load-more pages and late cards too. */
    fun renderShown(
        snapshot: HomeSnapshot,
        labels: Labels,
    ): String? {
        if (snapshot.shown.isEmpty()) return null
        val cards = snapshot.shown.map { it to labels.isOnTopic(it.video.toVideo()) }

        fun share(part: List<Pair<*, Boolean>>) = if (part.isEmpty()) 0.0 else part.count { it.second }.toDouble() / part.size
        val byOrigin =
            cards
                .groupBy { it.first.origin ?: "NONE" }
                .entries
                .joinToString { (origin, part) -> "$origin ${"%.3f".format(share(part))} of ${part.size}" }
        return "shown ${cards.size}: on-topic 1-20 ${"%.3f".format(share(cards.take(20)))}, 1-50 ${"%.3f".format(share(cards.take(50)))}," +
            " all ${"%.3f".format(share(cards))} | padding ${cards.count { it.first.padding }} | $byOrigin"
    }
}
