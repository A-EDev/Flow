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
    fun `seeds keep only mostly-watched non-shorts, newest first`() {
        val history = listOf(
            entry("a", 90, 100, ts = 1),                  // 90% ✓
            entry("b", 50, 100, ts = 2),                  // 50% ✗ below threshold
            entry("c", 80, 100, ts = 3, isShort = true),  // short ✗
            entry("d", 100, 100, ts = 4)                  // 100% ✓ newest
        )
        val seeds = seedInputsFrom(history)
        assertThat(seeds.map { it.id }).containsExactly("d", "a").inOrder()
        assertThat(seeds.first { it.id == "a" }.weight).isWithin(1e-6).of(0.9)
    }

    @Test
    fun `seeds are capped at max`() {
        val history = (1..20).map { entry("v$it", 100, 100, ts = it.toLong()) }
        assertThat(seedInputsFrom(history, max = 4)).hasSize(4)
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
}
