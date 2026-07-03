package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GraphSeedSelectorTest {
    private val now = 1_000_000_000L

    private fun seed(
        id: String,
        title: String = "topic $id",
        source: GraphSeedSource = GraphSeedSource.WATCH_HISTORY,
        channelId: String = "",
        engagementWeight: Double = 1.0,
        timestamp: Long = now,
        durationSec: Int = 600,
        percentWatched: Double = 100.0,
        isShort: Boolean = false
    ) = GraphSeedInput(
        id = id,
        title = title,
        channelId = channelId,
        source = source,
        engagementWeight = engagementWeight,
        timestamp = timestamp,
        durationSec = durationSec,
        percentWatched = percentWatched,
        isShort = isShort
    )

    @Test
    fun `explicit liked seeds outrank equally recent watch seeds`() {
        val selected = GraphSeedSelector.select(
            listOf(
                seed("watch", source = GraphSeedSource.WATCH_HISTORY),
                seed("liked", source = GraphSeedSource.LIKED)
            ),
            maxSeeds = 1,
            now = now
        )

        assertThat(selected).containsExactly("liked")
    }

    @Test
    fun `long partial watches qualify while short partial watches do not`() {
        val selected = GraphSeedSelector.select(
            listOf(
                seed("long_partial", durationSec = 600, percentWatched = 40.0, engagementWeight = 0.4),
                seed("short_partial", durationSec = 200, percentWatched = 40.0, engagementWeight = 0.4)
            ),
            maxSeeds = 4,
            now = now
        )

        assertThat(selected).containsExactly("long_partial")
    }

    @Test
    fun `selection caps dense title clusters at two seeds`() {
        val selected = GraphSeedSelector.select(
            listOf(
                seed("a1", title = "alpha one", engagementWeight = 1.0),
                seed("a2", title = "alpha two", engagementWeight = 0.9),
                seed("a3", title = "alpha three", engagementWeight = 0.8),
                seed("b1", title = "beta one", engagementWeight = 0.1)
            ),
            maxSeeds = 4,
            now = now
        )

        assertThat(selected).containsExactly("a1", "a2", "b1").inOrder()
    }

    @Test
    fun `selection excludes known blocked or suppressed channels`() {
        val selected = GraphSeedSelector.select(
            listOf(
                seed("blocked", channelId = "UC_blocked", engagementWeight = 1.0),
                seed("allowed", channelId = "UC_allowed", engagementWeight = 0.5)
            ),
            maxSeeds = 2,
            now = now,
            excludedChannelIds = setOf("UC_blocked")
        )

        assertThat(selected).containsExactly("allowed")
    }
}
