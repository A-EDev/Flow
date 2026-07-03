/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.data.recommendation.UserBrain
import org.junit.Test

/** I-10: behavioral coverage for the home-feed consumer logic (R-2 seeds, I-1 impressions). */
class HomeFeedLogicTest {

    private fun entry(id: String, pos: Long, dur: Long, ts: Long, isShort: Boolean = false) =
        VideoHistoryEntry(
            videoId = id, position = pos, duration = dur, timestamp = ts,
            title = "t-$id", thumbnailUrl = "", isShort = isShort
        )

    @Test
    fun `history graph seeds keep non-shorts newest first with progress metadata`() {
        val history = listOf(
            entry("a", 90, 100, ts = 1),                  // 90% ✓
            entry("b", 50, 100, ts = 2),                  // 50% ✗ below threshold
            entry("c", 80, 100, ts = 3, isShort = true),  // short ✗
            entry("d", 100, 100, ts = 4)                  // 100% ✓ newest
        )
        val seeds = graphSeedInputsFromHistory(history)
        assertThat(seeds.map { it.id }).containsExactly("d", "b", "a").inOrder()
        assertThat(seeds.first { it.id == "a" }.engagementWeight).isWithin(1e-6).of(0.9)
        assertThat(seeds.first { it.id == "a" }.percentWatched).isWithin(1e-6).of(90.0)
    }

    @Test
    fun `history graph seeds are capped at max`() {
        val history = (1..20).map { entry("v$it", 100, 100, ts = it.toLong()) }
        assertThat(graphSeedInputsFromHistory(history, max = 4)).hasSize(4)
    }

    @Test
    fun `impression filter drops non-video keys like shelves and loaders`() {
        val visible = listOf("v1", "shorts_shelf", "v2", "loading_indicator")
        val known = setOf("v1", "v2", "v3")
        assertThat(feedImpressionIds(visible, known)).containsExactly("v1", "v2").inOrder()
    }

    private fun v(title: String, duration: Int) = Video(
        id = title, title = title, channelName = "C", channelId = "c",
        thumbnailUrl = "", duration = duration, viewCount = 1000, uploadDate = "1 day ago"
    )

    private val defaultTaste = FeedTasteProfile(
        comfortDurationSec = DURATION_COMFORT_DEFAULT_SEC, affinityTopics = emptySet()
    )

    @Test
    fun `format markers demote exploration content only for users with no matching interest`() {
        val compilation = v("Amazing Cream Dessert Korean Bakery Compilation", 600)
        // No baking interest: marker penalty applies.
        assertThat(feedFitPenalty(compilation, defaultTaste)).isGreaterThan(0.0)
        // Baking enthusiast: the same video is a good fit, no penalty.
        val baker = defaultTaste.copy(affinityTopics = setOf("baking", "bakery", "dessert"))
        assertThat(feedFitPenalty(compilation, baker)).isEqualTo(0.0)
    }

    @Test
    fun `long-form is penalised by comfort but never for long-form personas`() {
        val essay = v("Three hour deep dive documentary", 11_000)
        assertThat(feedFitPenalty(essay, defaultTaste)).isGreaterThan(0.0)
        // Deep-diver / scholar tolerate any length: comfort cap is 0 ⇒ no duration penalty.
        val deepDiver = feedTasteProfile(
            UserBrain(totalInteractions = 200), FlowPersona.DEEP_DIVER
        )
        assertThat(feedFitPenalty(essay, deepDiver)).isEqualTo(0.0)
    }

    @Test
    fun `demoteByFit pushes poor-fit items below well-fit ones without dropping any`() {
        val ranked = listOf(
            v("3 Hour Baking Compilation Marathon", 11_000),   // marker + far over comfort
            v("Kotlin coroutines explained", 900)              // clean
        )
        val out = demoteByFit(ranked, defaultTaste)
        assertThat(out.map { it.id })
            .containsExactly("Kotlin coroutines explained", "3 Hour Baking Compilation Marathon")
            .inOrder()
        assertThat(out).hasSize(ranked.size) // nothing dropped
    }

    @Test
    fun `well-fit content keeps its engine order`() {
        val ranked = listOf(v("Kotlin coroutines explained", 900), v("Python walkthrough", 1_200))
        assertThat(demoteByFit(ranked, defaultTaste).map { it.id })
            .containsExactly("Kotlin coroutines explained", "Python walkthrough").inOrder()
    }

    private fun vc(id: String, channelId: String) = Video(
        id = id, title = id, channelName = channelId, channelId = channelId,
        thumbnailUrl = "", duration = 600, viewCount = 1, uploadDate = "1 day ago"
    )

    private fun gc(
        video: Video,
        seedId: String,
        seedScore: Double,
        graphRank: Int = 0,
        seedResultCount: Int = 12,
        hitCount: Int = 1
    ) = GraphCandidate(
        video = video,
        seedId = seedId,
        seedScore = seedScore,
        graphRank = graphRank,
        seedCluster = "cluster-$seedId",
        seedResultCount = seedResultCount,
        hitCount = hitCount
    )

    private fun adjacentSameChannel(videos: List<Video>) =
        videos.zipWithNext().count { (a, b) -> a.channelId.isNotBlank() && a.channelId == b.channelId }

    @Test
    fun `spaceByChannel separates clustered same-channel items without dropping any`() {
        val clustered = listOf(
            vc("a1", "A"), vc("a2", "A"), vc("a3", "A"), vc("b1", "B"), vc("c1", "C")
        )
        val spaced = spaceByChannel(clustered)
        assertThat(spaced.map { it.id }).containsExactlyElementsIn(clustered.map { it.id })
        assertThat(adjacentSameChannel(spaced)).isEqualTo(0)
    }

    @Test
    fun `spaceByChannel honours the seeded tail to avoid cross-page repeats`() {
        // Prior page ended on channel A; a page that starts with A must not lead with it.
        val page = listOf(vc("a1", "A"), vc("b1", "B"))
        val spaced = spaceByChannel(page, seedRecent = listOf("A"))
        assertThat(spaced.first().channelId).isEqualTo("B")
    }

    @Test
    fun `spaceByChannel ignores blank channel ids`() {
        val related = listOf(vc("r1", ""), vc("r2", ""), vc("r3", ""))
        assertThat(spaceByChannel(related).map { it.id }).containsExactly("r1", "r2", "r3").inOrder()
    }

    @Test
    fun `home feed quotas adapt to no subscription maturing and mature profiles`() {
        assertThat(homeFeedQuotas(40, subCount = 0, totalInteractions = 200))
            .containsExactly(
                FeedSource.SUBS, 0,
                FeedSource.RELATED, 14,
                FeedSource.DISCOVERY, 18,
                FeedSource.VIRAL, 8
            )

        assertThat(homeFeedQuotas(40, subCount = 12, totalInteractions = 20))
            .containsExactly(
                FeedSource.SUBS, 14,
                FeedSource.RELATED, 12,
                FeedSource.DISCOVERY, 10,
                FeedSource.VIRAL, 4
            )

        assertThat(homeFeedQuotas(40, subCount = 12, totalInteractions = 200))
            .containsExactly(
                FeedSource.SUBS, 16,
                FeedSource.RELATED, 10,
                FeedSource.DISCOVERY, 10,
                FeedSource.VIRAL, 4
            )
    }

    @Test
    fun `blendFeedSources reports source distribution`() {
        val result = blendFeedSources(
            lanes = mapOf(
                FeedSource.SUBS to listOf(vc("s1", "S")),
                FeedSource.RELATED to listOf(vc("r1", "R")),
                FeedSource.DISCOVERY to listOf(vc("d1", "D")),
                FeedSource.VIRAL to listOf(vc("v1", "V"))
            ),
            quotas = mapOf(
                FeedSource.SUBS to 1,
                FeedSource.RELATED to 1,
                FeedSource.DISCOVERY to 1,
                FeedSource.VIRAL to 1
            ),
            targetSize = 4
        )

        assertThat(result.videos.map { it.id }).containsExactly("s1", "r1", "d1", "v1").inOrder()
        assertThat(result.sourceCounts)
            .containsExactly(
                FeedSource.SUBS, 1,
                FeedSource.RELATED, 1,
                FeedSource.DISCOVERY, 1,
                FeedSource.VIRAL, 1
            )
    }

    @Test
    fun `blendFeedSources relaxes scarce quota in related discovery subs viral order`() {
        val result = blendFeedSources(
            lanes = mapOf(
                FeedSource.SUBS to listOf(vc("s1", "S"), vc("s2", "S")),
                FeedSource.RELATED to listOf(vc("r1", "R1"), vc("r2", "R2")),
                FeedSource.DISCOVERY to listOf(vc("d1", "D1"), vc("d2", "D2")),
                FeedSource.VIRAL to listOf(vc("v1", "V1"), vc("v2", "V2"))
            ),
            quotas = FeedSource.entries.associateWith { 0 },
            targetSize = 3
        )

        assertThat(result.items.map { it.source })
            .containsExactly(FeedSource.RELATED, FeedSource.RELATED, FeedSource.DISCOVERY)
            .inOrder()
        assertThat(result.videos.map { it.id }).containsExactly("r1", "r2", "d1").inOrder()
    }

    @Test
    fun `mergeGraphCandidates counts multi-seed convergence and keeps strongest metadata`() {
        val weak = gc(vc("shared", "A"), seedId = "s1", seedScore = 0.7, graphRank = 5)
        val strong = gc(vc("shared", "B"), seedId = "s2", seedScore = 1.2, graphRank = 2)

        val merged = mergeGraphCandidates(listOf(weak, strong)).single()

        assertThat(merged.hitCount).isEqualTo(2)
        assertThat(merged.seedId).isEqualTo("s2")
        assertThat(merged.seedScore).isWithin(1e-6).of(1.2)
        assertThat(merged.graphRank).isEqualTo(2)
    }

    @Test
    fun `graph boost lets multi-seed candidate beat an adjacent weak graph candidate`() {
        val ranked = (0 until 20).map { vc("v$it", "C$it") }.toMutableList()
        val oneOff = vc("one_off", "one")
        val multiSeed = vc("multi_seed", "multi")
        ranked[10] = oneOff
        ranked[11] = multiSeed

        val boosted = applyGraphBoost(
            ranked,
            mapOf(
                oneOff.id to gc(oneOff, seedId = "s1", seedScore = 0.4, graphRank = 0),
                multiSeed.id to gc(multiSeed, seedId = "s2", seedScore = 1.2, graphRank = 0, hitCount = 2)
            )
        )

        assertThat(boosted.indexOf(multiSeed)).isLessThan(boosted.indexOf(oneOff))
    }

    @Test
    fun `fit demotion still wins after graph boost`() {
        val clean = v("Kotlin coroutines explained", 900)
        val poorFit = v("3 Hour Baking Compilation Marathon", 11_000)
        val ranked = (0 until 20).map { vc("v$it", "C$it") }.toMutableList()
        ranked[10] = clean
        ranked[11] = poorFit

        val boosted = applyGraphBoost(
            ranked,
            mapOf(poorFit.id to gc(poorFit, seedId = "s1", seedScore = 1.2, graphRank = 0, hitCount = 2))
        )
        val demoted = demoteByFit(boosted, defaultTaste)

        assertThat(demoted.indexOf(clean)).isLessThan(demoted.indexOf(poorFit))
    }

    private fun graphSeed(
        id: String,
        source: GraphSeedSource,
        timestamp: Long = 100L
    ) = GraphSeedInput(
        id = id,
        title = "title-$id",
        channelId = "",
        source = source,
        engagementWeight = 1.0,
        timestamp = timestamp,
        durationSec = 600,
        percentWatched = 100.0
    )

    @Test
    fun `saved interest seed inputs preserve source order before engine selection`() {
        val sources = SavedSeedSources(
            history = listOf(graphSeed("h_latest", GraphSeedSource.WATCH_HISTORY)),
            liked = listOf(graphSeed("l1", GraphSeedSource.LIKED)),
            playlists = listOf(graphSeed("p1", GraphSeedSource.PLAYLIST))
        )
        val seeds = savedInterestSeedInputs(sources, cooldown = emptySet())
        assertThat(seeds.map { it.id }).containsExactly("h_latest", "l1", "p1").inOrder()
    }

    @Test
    fun `saved interest seed inputs pull from history liked and playlists within bounds`() {
        val sources = SavedSeedSources(
            history = (1..10).map { graphSeed("h$it", GraphSeedSource.WATCH_HISTORY) },
            liked = (1..10).map { graphSeed("l$it", GraphSeedSource.LIKED) },
            playlists = (1..10).map { graphSeed("p$it", GraphSeedSource.PLAYLIST) }
        )
        val seeds = savedInterestSeedInputs(sources, cooldown = emptySet(), maxPerSource = 2)
        val ids = seeds.map { it.id }
        assertThat(seeds.size).isEqualTo(6)
        assertThat(ids.any { it.startsWith("h") }).isTrue()
        assertThat(ids.any { it.startsWith("l") }).isTrue()
        assertThat(ids.any { it.startsWith("p") }).isTrue()
    }

    @Test
    fun `saved interest seed inputs exclude ids on cooldown`() {
        val sources = SavedSeedSources(
            history = listOf(
                graphSeed("h_latest", GraphSeedSource.WATCH_HISTORY),
                graphSeed("h2", GraphSeedSource.WATCH_HISTORY)
            ),
            liked = listOf(graphSeed("l1", GraphSeedSource.LIKED)),
            playlists = listOf(graphSeed("p1", GraphSeedSource.PLAYLIST))
        )
        val cooldown = setOf("h_latest", "l1", "p1")
        val seeds = savedInterestSeedInputs(sources, cooldown = cooldown)
        assertThat(seeds.map { it.id }).containsNoneIn(cooldown)
        assertThat(seeds.map { it.id }).contains("h2")
    }

    @Test
    fun `saved interest seed inputs return empty when nothing is saved`() {
        val empty = SavedSeedSources(emptyList(), emptyList(), emptyList())
        assertThat(savedInterestSeedInputs(empty, cooldown = emptySet())).isEmpty()
    }
}
