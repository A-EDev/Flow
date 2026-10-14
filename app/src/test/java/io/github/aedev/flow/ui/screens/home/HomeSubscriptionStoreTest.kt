/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/** Home reads subscriptions from the local store and adds the network top-up below the viewport. */
class HomeSubscriptionStoreTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun video(
        id: String,
        channelId: String = "c-$id",
        ageHours: Long = 1,
        duration: Int = 600,
        isShort: Boolean = false,
        isLive: Boolean = false,
    ) = Video(
        id = id,
        title = id,
        channelName = channelId,
        channelId = channelId,
        thumbnailUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        timestamp = now - ageHours * hour,
        isShort = isShort,
        isLive = isLive,
    )

    @Test
    fun `stored uploads are cards only when their length is known and they are recent`() {
        val stored =
            listOf(
                video("known", ageHours = 30),
                video("live", duration = 0, isLive = true),
                video("rss-only", duration = 0),
                video("reel", isShort = true),
                video("old", ageHours = 20 * 24),
            )

        assertThat(stored.storedSubscriptionVideos(now).map { it.id }).containsExactly("live", "known").inOrder()
        assertThat(stored.storedSubscriptionReels(now).map { it.id }).containsExactly("reel")
    }

    @Test
    fun `channels whose fresh uploads still lack a length come newest first`() {
        val stored =
            listOf(
                video("a", channelId = "UCa", ageHours = 10, duration = 0),
                video("b", channelId = "UCb", ageHours = 2, duration = 0),
                video("c", channelId = "UCc", ageHours = 2),
                video("d", channelId = "UCd", ageHours = 100, duration = 0),
            )

        assertThat(stored.channelsMissingLengths(now)).containsExactly("UCb", "UCa").inOrder()
    }

    @Test
    fun `late uploads go below the viewport and never move what was seen`() {
        val feed = (0 until 8).map { video("f$it") }
        val late = listOf(video("late1"), video("late2"), video("f1"))

        val merged = insertBelowViewport(feed, late, lastVisibleIndex = 3)

        assertThat(merged.take(4)).isEqualTo(feed.take(4))
        assertThat(merged.map { it.id })
            .containsExactly("f0", "f1", "f2", "f3", "f4", "f5", "late1", "f6", "f7", "late2")
            .inOrder()
    }

    @Test
    fun `a late upload never lands near its own channel, and is left out when it cannot (#907)`() {
        val feed = (0 until 20).map { video("f$it", channelId = if (it == 6) "UCjack" else "c-$it") }
        val late = listOf(video("jack2", channelId = "UCjack"), video("other", channelId = "UCother"))

        val merged = insertBelowViewport(feed, late, lastVisibleIndex = 4)
        val wide = insertBelowViewport(feed + (20 until 50).map { video("f$it") }, late, lastVisibleIndex = 4)

        assertThat(merged.map { it.id }).doesNotContain("jack2")
        assertThat(merged.map { it.id }).contains("other")
        assertThat(wide.indexOfFirst { it.id == "jack2" } - wide.indexOfFirst { it.id == "f6" }).isGreaterThan(20)
    }

    @Test
    fun `a late upload from a followed channel only goes between two cards that are not`() {
        val feed = listOf("o0", "s1", "o2", "o3", "s4", "s5", "o6", "o7", "s8").map { video(it) }
        val followed = { v: Video -> v.id.startsWith("s") }

        val merged = insertBelowViewport(feed, listOf(video("sLate")), lastVisibleIndex = 0, spacing = 1, isGrouped = followed)

        assertThat(merged.map { it.id }).containsExactly("o0", "s1", "o2", "sLate", "o3", "s4", "s5", "o6", "o7", "s8").inOrder()
    }

    @Test
    fun `nothing new leaves the feed as it is`() {
        val feed = listOf(video("a"), video("b"))

        assertThat(insertBelowViewport(feed, listOf(video("a")), lastVisibleIndex = 0)).isSameInstanceAs(feed)
    }
}
