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
        isShort: Boolean = false,
    ) = GraphSeedInput(
        id = id,
        title = title,
        channelId = channelId,
        source = source,
        engagementWeight = engagementWeight,
        timestamp = timestamp,
        durationSec = durationSec,
        percentWatched = percentWatched,
        isShort = isShort,
    )

    @Test
    fun `explicit liked seeds outrank equally recent watch seeds`() {
        val selected =
            GraphSeedSelector.select(
                listOf(
                    seed("watch", source = GraphSeedSource.WATCH_HISTORY),
                    seed("liked", source = GraphSeedSource.LIKED),
                ),
                maxSeeds = 1,
                now = now,
            )

        assertThat(selected).containsExactly("liked")
    }

    @Test
    fun `long partial watches qualify while short partial watches do not`() {
        val selected =
            GraphSeedSelector.select(
                listOf(
                    seed("long_partial", durationSec = 600, percentWatched = 40.0, engagementWeight = 0.4),
                    seed("short_partial", durationSec = 200, percentWatched = 40.0, engagementWeight = 0.4),
                ),
                maxSeeds = 4,
                now = now,
            )

        assertThat(selected).containsExactly("long_partial")
    }

    @Test
    fun `selection caps dense title clusters at two seeds`() {
        val selected =
            GraphSeedSelector.select(
                listOf(
                    seed("a1", title = "alpha one", engagementWeight = 1.0),
                    seed("a2", title = "alpha two", engagementWeight = 0.9),
                    seed("a3", title = "alpha three", engagementWeight = 0.8),
                    seed("b1", title = "beta one", engagementWeight = 0.1),
                ),
                maxSeeds = 4,
                now = now,
            )

        // Progressive fill: every cluster gets its first seed (a1, b1) before any
        // cluster gets a second (a2); the dense cluster stays capped at two.
        assertThat(selected).containsExactly("a1", "b1", "a2").inOrder()
    }

    @Test
    fun `selection excludes known blocked or suppressed channels`() {
        val selected =
            GraphSeedSelector.select(
                listOf(
                    seed("blocked", channelId = "UC_blocked", engagementWeight = 1.0),
                    seed("allowed", channelId = "UC_allowed", engagementWeight = 0.5),
                ),
                maxSeeds = 2,
                now = now,
                excludedChannelIds = setOf("UC_blocked"),
            )

        assertThat(selected).containsExactly("allowed")
    }

    @Test
    fun `a feed pick never outranks a real watch that qualifies as a seed`() {
        val weakestWatch =
            seed("watch", engagementWeight = 0.35, percentWatched = 35.0, durationSec = 1_200, timestamp = now - 200L * 86_400_000L)
        val feedPick = seed("feed", source = GraphSeedSource.FEED, engagementWeight = 0.6)

        assertThat(GraphSeedSelector.scoreSeed(feedPick, now)).isLessThan(GraphSeedSelector.scoreSeed(weakestWatch, now))
    }

    @Test
    fun `the long-term slot goes to the heaviest interest the recent seeds miss`() {
        val picked =
            GraphSeedSelector.selectLongTermSeed(
                candidates =
                    listOf(
                        seed("guitar-old", title = "guitar solo"),
                        seed("cooking-old", title = "cooking pasta"),
                        seed("comedy-liked", title = "comedy special", source = GraphSeedSource.LIKED),
                    ),
                coveredCommunities = setOf("guitar"),
                communityMass = mapOf("guitar" to 0.9, "comedy" to 0.6, "cooking" to 0.3),
                communityOf = { it },
                topicScores = mapOf("guitar" to 0.9, "comedy" to 0.6, "cooking" to 0.3),
            )

        assertThat(picked).isEqualTo("comedy-liked")
    }

    @Test
    fun `no long-term seed when every learnt interest is already covered`() {
        val picked =
            GraphSeedSelector.selectLongTermSeed(
                candidates = listOf(seed("guitar-old", title = "guitar solo")),
                coveredCommunities = setOf("guitar"),
                communityMass = mapOf("guitar" to 0.9),
                communityOf = { it },
                topicScores = mapOf("guitar" to 0.9),
            )

        assertThat(picked).isNull()
    }

    @Test
    fun `the last seed goes to the long-term interest`() {
        val recent =
            (0 until 3).map { seed("g$it", title = "guitar lesson $it") } + (0 until 3).map { seed("c$it", title = "cooking pasta $it") }

        val selected =
            GraphSeedSelector.selectWithLongTerm(
                candidates = recent,
                maxSeeds = 4,
                longTermCandidates = listOf(seed("comedy", title = "comedy special", source = GraphSeedSource.LIKED)),
                communityMass = mapOf("guitar" to 0.4, "cooking" to 0.3, "comedy" to 0.5),
                communityOf = { it },
                now = now,
                topicScores = mapOf("guitar" to 0.4, "cooking" to 0.3, "comedy" to 0.5),
            )

        assertThat(selected).hasSize(4)
        assertThat(selected.last()).isEqualTo("comedy")
    }

    @Test
    fun `cooled seeds come back when only unqualified watches are left`() {
        val qualified = listOf("guitar lesson", "cooking pasta", "chess opening").mapIndexed { i, title -> seed("q$i", title = title) }
        val halfWatched = (0 until 20).map { seed("h$it", title = "guitar tab $it", percentWatched = 20.0, engagementWeight = 0.2) }

        val selected =
            GraphSeedSelector.selectWithLongTerm(
                candidates = qualified + halfWatched,
                maxSeeds = 3,
                longTermCandidates = emptyList(),
                communityMass = emptyMap(),
                communityOf = { it },
                now = now,
                cooledIds = qualified.mapTo(HashSet()) { it.id },
            )

        assertThat(selected).containsExactly("q0", "q1", "q2")
    }

    @Test
    fun `fresh seeds are never topped up with cooled ones`() {
        val fresh = seed("fresh", title = "guitar lesson")
        val cooled = listOf("cooking pasta", "chess opening").mapIndexed { i, title -> seed("cooled$i", title = title) }

        val selected =
            GraphSeedSelector.selectWithLongTerm(
                candidates = listOf(fresh) + cooled,
                maxSeeds = 3,
                longTermCandidates = emptyList(),
                communityMass = emptyMap(),
                communityOf = { it },
                now = now,
                cooledIds = cooled.mapTo(HashSet()) { it.id },
            )

        assertThat(selected).containsExactly("fresh")
    }
}
