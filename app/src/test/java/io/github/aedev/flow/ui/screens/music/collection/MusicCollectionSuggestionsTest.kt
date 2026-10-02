package io.github.aedev.flow.ui.screens.music.collection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicCollectionSuggestionsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        mockkObject(PerformanceDispatcher)
        every { PerformanceDispatcher.networkIO } returns dispatcher
    }

    @After
    fun tearDown() = unmockkAll()

    private fun suggestedIds(suggestions: MusicCollectionSuggestions) =
        suggestions.state.value.tracks
            .map { it.videoId }

    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = id, thumbnailUrl = "", duration = 100)

    @Test
    fun `seeds start from the newest end and move on each round`() {
        val ids = listOf("a", "b", "c", "d", "e", "f", "g")

        assertThat(suggestionSeeds(ids, round = 0)).containsExactly("g", "f", "e").inOrder()
        assertThat(suggestionSeeds(ids, round = 1)).containsExactly("d", "c", "b").inOrder()
        assertThat(suggestionSeeds(listOf("local_1"), round = 0)).isEmpty()
    }

    @Test
    fun `suggestions take from each seed in turn and skip songs already in the playlist`() {
        val merged =
            mergeSuggestions(
                related = listOf(listOf(track("x1"), track("x2")), listOf(track("y1"), track("a"))),
                exclude = setOf("a"),
            )

        assertThat(merged.map { it.videoId }).containsExactly("x1", "y1", "x2").inOrder()
    }

    @Test
    fun `the end of the list fetches once and refresh fetches again`() =
        runTest(dispatcher) {
            val fetched = mutableListOf<String>()
            val suggestions =
                MusicCollectionSuggestions(TestScope(dispatcher)) { seed ->
                    fetched += seed
                    listOf(track("r_$seed"))
                }

            suggestions.requestOnce(listOf("a", "b"))
            suggestions.requestOnce(listOf("a", "b"))
            advanceUntilIdle()
            assertThat(fetched).containsExactly("b", "a")
            assertThat(suggestedIds(suggestions)).containsExactly("r_b", "r_a")

            suggestions.refresh(listOf("a", "b"))
            advanceUntilIdle()
            assertThat(fetched).hasSize(4)

            suggestions.drop("r_b")
            assertThat(suggestedIds(suggestions)).doesNotContain("r_b")
        }
}
