package io.github.aedev.flow.data.scrobble

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LastFmDiscoveryTest {
    @Test
    fun `picks alternate between seeds and skip the seeds themselves and hidden artists`() {
        val picks =
            discoveryPicks(
                similar =
                    listOf(
                        listOf("A" to "a1", "Seeded" to "Song", "A" to "a2"),
                        listOf("Hidden" to "h1", "B" to "b1", "a" to "A1"),
                    ),
                seeds = listOf("Seeded" to "Song"),
                hiddenArtists = setOf("hidden"),
                limit = 10,
            )

        assertThat(picks).containsExactly("A" to "a1", "B" to "b1", "A" to "a2").inOrder()
    }

    @Test
    fun `the shelf stops at its limit`() {
        val many = (1..30).map { "Artist $it" to "Song $it" }

        assertThat(discoveryPicks(listOf(many), emptyList(), emptySet(), limit = 12)).hasSize(12)
    }
}
