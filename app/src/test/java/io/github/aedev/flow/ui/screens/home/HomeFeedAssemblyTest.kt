/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Coverage for the feed assembly pipeline that used to be inline in `loadFlowFeed`. */
class HomeFeedAssemblyTest {
    private val now = 1_000_000_000L
    private val hour = 60L * 60L * 1000L

    private fun video(
        id: String,
        channelId: String = "c-$id",
        ageMs: Long = 10 * 24 * hour,
        duration: Int = 600,
        isShort: Boolean = false,
        isLive: Boolean = false,
        isUpcoming: Boolean = false,
        uploadDate: String = "10 days ago",
        avatar: String = "",
    ) = Video(
        id = id,
        title = "title-$id",
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = duration,
        viewCount = 100,
        uploadDate = uploadDate,
        timestamp = now - ageMs,
        isShort = isShort,
        isLive = isLive,
        isUpcoming = isUpcoming,
        channelThumbnailUrl = avatar,
    )

    private val taste = FeedTasteProfile(DURATION_COMFORT_DEFAULT_SEC, emptySet())

    private suspend fun lanes(
        subs: List<Video> = emptyList(),
        discovery: List<Video> = emptyList(),
        viral: List<Video> = emptyList(),
        related: List<GraphCandidate> = emptyList(),
        rss: List<Video> = emptyList(),
        watched: Set<String> = emptySet(),
        excluded: Set<String> = emptySet(),
        freshSlots: Int = 3,
        avatars: Map<String, String> = emptyMap(),
    ) = buildHomeFeedLanes(
        rawSubs = subs,
        rawDiscovery = discovery,
        rawViral = viral,
        rawRelated = related,
        rssFeed = rss,
        watched = watched,
        excludedChannels = excluded,
        taste = taste,
        now = now,
        freshSlotTarget = freshSlots,
        subAvatarMap = avatars,
        rank = { it },
    )

    @Test
    fun `shorts and zero-duration items never reach the video lanes`() =
        runTest {
            val result =
                lanes(
                    subs = listOf(video("keep"), video("short", isShort = true), video("empty", duration = 0)),
                )

            assertThat(result.subsPoolSize).isEqualTo(1)
            assertThat(result.bestSubs.map { it.id }).containsExactly("keep")
        }

    @Test
    fun `a live item survives the zero-duration filter`() =
        runTest {
            val result = lanes(subs = listOf(video("live", duration = 0, isLive = true)))

            assertThat(result.bestSubs.map { it.id }).containsExactly("live")
        }

    @Test
    fun `excluded channels cannot resurface through the fresh-subs lane`() =
        runTest {
            val blocked = video("blocked", channelId = "bad", ageMs = hour, uploadDate = "1 hour ago")
            val allowed = video("allowed", channelId = "good", ageMs = hour, uploadDate = "1 hour ago")

            val result = lanes(subs = listOf(blocked, allowed), excluded = setOf("bad"))

            assertThat((result.pinnedFresh + result.overflowFresh).map { it.id }).containsExactly("allowed")
        }

    @Test
    fun `rss uploads join the fresh lane and upcoming ones do not`() =
        runTest {
            val result =
                lanes(
                    rss =
                        listOf(
                            video("rss-fresh", channelId = "r1", ageMs = 2 * hour),
                            video("rss-premiere", channelId = "r2", ageMs = 2 * hour, isUpcoming = true),
                        ),
                )

            assertThat((result.pinnedFresh + result.overflowFresh).map { it.id })
                .containsExactly("rss-fresh")
        }

    @Test
    fun `one fresh slot per channel even when a channel uploaded repeatedly`() =
        runTest {
            val result =
                lanes(
                    rss =
                        listOf(
                            video("a1", channelId = "same", ageMs = 1 * hour),
                            video("a2", channelId = "same", ageMs = 2 * hour),
                            video("a3", channelId = "same", ageMs = 3 * hour),
                            video("b1", channelId = "other", ageMs = 4 * hour),
                        ),
                )

            val fresh = (result.pinnedFresh + result.overflowFresh).map { it.id }
            assertThat(fresh).containsExactly("a1", "b1").inOrder()
        }

    @Test
    fun `fresh lane is capped at the dynamic slot target`() =
        runTest {
            val uploads = (1..6).map { video("f$it", channelId = "ch$it", ageMs = it * hour) }

            val result = lanes(rss = uploads, freshSlots = 2)

            assertThat(result.pinnedFresh + result.overflowFresh).hasSize(2)
        }

    @Test
    fun `only the first two fresh items are pinned and the rest overflow`() =
        runTest {
            val uploads = (1..4).map { video("f$it", channelId = "ch$it", ageMs = it * hour) }

            val result = lanes(rss = uploads, freshSlots = 4)

            assertThat(result.pinnedFresh.map { it.id }).containsExactly("f1", "f2").inOrder()
            assertThat(result.overflowFresh.map { it.id }).containsExactly("f3", "f4").inOrder()
        }

    @Test
    fun `a video already in the fresh lane is not repeated in the subs lane`() =
        runTest {
            val fresh = video("dup", channelId = "c1", ageMs = hour, uploadDate = "1 hour ago")

            val result = lanes(subs = listOf(fresh, video("older", channelId = "c2")))

            assertThat((result.pinnedFresh + result.overflowFresh).map { it.id }).contains("dup")
            assertThat(result.bestSubs.map { it.id }).doesNotContain("dup")
        }

    @Test
    fun `subscription avatars backfill only videos missing one`() =
        runTest {
            val result =
                lanes(
                    subs =
                        listOf(
                            video("bare", channelId = "c1"),
                            video("has-avatar", channelId = "c2", avatar = "own.jpg"),
                        ),
                    avatars = mapOf("c1" to "sub.jpg", "c2" to "sub2.jpg"),
                )

            val byId = result.bestSubs.associateBy { it.id }
            assertThat(byId.getValue("bare").channelThumbnailUrl).isEqualTo("sub.jpg")
            assertThat(byId.getValue("has-avatar").channelThumbnailUrl).isEqualTo("own.jpg")
        }

    @Test
    fun `watched videos are dropped from every lane`() =
        runTest {
            val result =
                lanes(
                    subs = listOf(video("s1"), video("s2")),
                    discovery = listOf(video("d1")),
                    viral = listOf(video("v1")),
                    watched = setOf("s1", "d1", "v1"),
                )

            assertThat(result.bestSubs.map { it.id }).containsExactly("s2")
            assertThat(result.bestDiscovery).isEmpty()
            assertThat(result.bestViral).isEmpty()
        }

    @Test
    fun `pinned fresh items lead the assembled feed`() =
        runTest {
            val built =
                lanes(
                    rss = listOf(video("fresh", channelId = "f", ageMs = hour)),
                    discovery = (1..5).map { video("d$it", channelId = "d$it") },
                )

            val mix = assembleHomeFeed(built, onScreenIds = emptySet(), subCount = 10, totalInteractions = 0)

            assertThat(mix.videos.first().id).isEqualTo("fresh")
            assertThat(mix.freshAdded).isEqualTo(1)
        }

    @Test
    fun `fresh lane additions are credited to the subs source count`() =
        runTest {
            val built = lanes(rss = listOf(video("fresh", channelId = "f", ageMs = hour)))

            val mix = assembleHomeFeed(built, onScreenIds = emptySet(), subCount = 10, totalInteractions = 0)

            assertThat(mix.selectedSourceCounts[FeedSource.SUBS]).isEqualTo(1)
        }

    @Test
    fun `a deep feed excludes what is already on screen so a refresh looks new`() =
        runTest {
            // Needs more than half a page of unseen candidates, and the per-lane caps mean one
            // lane alone can never supply that.
            val built =
                lanes(
                    subs = (1..20).map { video("s$it", channelId = "sc$it") },
                    discovery = (1..20).map { video("d$it", channelId = "dc$it") },
                )
            val onScreen = setOf("d1", "d2", "d3")

            val mix = assembleHomeFeed(built, onScreenIds = onScreen, subCount = 20, totalInteractions = 0)

            assertThat(mix.videos.map { it.id }).containsNoneIn(onScreen)
        }

    @Test
    fun `a thin feed reuses on-screen items rather than rendering half a page`() =
        runTest {
            val built = lanes(discovery = (1..3).map { video("d$it", channelId = "ch$it") })
            val onScreen = setOf("d1", "d2", "d3")

            val mix = assembleHomeFeed(built, onScreenIds = onScreen, subCount = 0, totalInteractions = 0)

            assertThat(mix.videos).isNotEmpty()
        }

    @Test
    fun `the assembled feed never exceeds the target size`() =
        runTest {
            val built =
                lanes(
                    subs = (1..30).map { video("s$it", channelId = "sc$it") },
                    discovery = (1..30).map { video("d$it", channelId = "dc$it") },
                    viral = (1..30).map { video("v$it", channelId = "vc$it") },
                )

            val mix = assembleHomeFeed(built, onScreenIds = emptySet(), subCount = 50, totalInteractions = 100)

            assertThat(mix.videos.size).isAtMost(HOME_TARGET_SIZE)
            assertThat(mix.videos.map { it.id }).containsNoDuplicates()
        }

    @Test
    fun `the subs backlog keeps what the mix did not use`() =
        runTest {
            val built = lanes(subs = (1..20).map { video("s$it", channelId = "sc$it") })

            val mix = assembleHomeFeed(built, onScreenIds = emptySet(), subCount = 20, totalInteractions = 0)

            val used = mix.videos.mapTo(mutableSetOf()) { it.id }
            assertThat(mix.subsBacklog.map { it.id }).containsNoneIn(used)
        }

    @Test
    fun `related metadata is keyed by video id for the graph boost`() =
        runTest {
            val candidate =
                GraphCandidate(
                    video = video("r1"),
                    seedId = "seed",
                    seedScore = 1.0,
                    graphRank = 0,
                    seedCluster = "misc",
                    seedResultCount = 3,
                )

            val result = lanes(related = listOf(candidate))

            assertThat(result.relatedMetadata.keys).containsExactly("r1")
            assertThat(result.bestRelated.map { it.id }).containsExactly("r1")
        }
}
