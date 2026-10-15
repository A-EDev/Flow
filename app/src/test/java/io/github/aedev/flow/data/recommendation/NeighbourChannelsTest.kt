package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeighbourChannelsTest {
    private val now = 1_800_000_000_000L
    private val dayMs = 24L * 60L * 60L * 1000L

    private fun source(
        channel: String,
        vararg related: String,
        at: Long = now,
    ) = NeuroCoWatchStore.Source(channel, related.toList(), at)

    @Test
    fun `a channel needs two of the viewer's channels pointing to it`() {
        val sources = listOf(source("mkbhd", "dave2d", "cooking"), source("linus", "dave2d"), source("mkbhd", "cooking"))

        val neighbours = NeighbourChannels.neighbours(sources, setOf("mkbhd", "linus"), { false }, now)

        assertThat(neighbours.map { it.channelId }).containsExactly("dave2d")
        assertThat(neighbours.single().pointers).containsExactly("mkbhd", "linus")
    }

    @Test
    fun `the viewer's own, excluded and unanchored channels never count`() {
        val sources =
            listOf(
                source("mkbhd", "linus", "blocked"),
                source("linus", "mkbhd", "blocked"),
                source("stranger", "dave2d"),
                source("other", "dave2d"),
            )

        val neighbours = NeighbourChannels.neighbours(sources, setOf("mkbhd", "linus"), { it == "blocked" }, now)

        assertThat(neighbours).isEmpty()
    }

    @Test
    fun `older pointers weigh less`() {
        val fresh =
            listOf(
                source("a", "n1"),
                source("b", "n1"),
                source("a", "n2", at = now - 90 * dayMs),
                source(
                    "b",
                    "n2",
                    at =
                        now - 90 * dayMs,
                ),
            )

        val neighbours = NeighbourChannels.neighbours(fresh, setOf("a", "b"), { false }, now)

        assertThat(neighbours.map { it.channelId }).containsExactly("n1", "n2").inOrder()
        assertThat(neighbours[1].score).isWithin(0.01).of(0.5)
    }
}
