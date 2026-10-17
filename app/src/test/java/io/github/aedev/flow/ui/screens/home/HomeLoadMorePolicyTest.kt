package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeLoadMorePolicyTest {
    @Test
    fun `a failed attempt never ends the feed`() {
        (1..HOME_PREFETCH_EMPTY_PAGE_RETRIES).forEach { attempt ->
            assertThat(nextLoadMoreStep(LoadMoreOutcome.FAILED, attempt)).isEqualTo(LoadMoreStep.STOP_FAILED)
        }
    }

    @Test
    fun `only the last of the answered empty attempts ends the feed`() {
        val steps = (1..HOME_PREFETCH_EMPTY_PAGE_RETRIES).map { nextLoadMoreStep(LoadMoreOutcome.EXHAUSTED, it) }

        assertThat(steps.dropLast(1)).containsNoneOf(LoadMoreStep.END_FEED, LoadMoreStep.STOP_FAILED)
        assertThat(steps.last()).isEqualTo(LoadMoreStep.END_FEED)
    }

    @Test
    fun `held cards wait for the next attempt and a shown page ends the run`() {
        assertThat(nextLoadMoreStep(LoadMoreOutcome.HELD, 1)).isEqualTo(LoadMoreStep.RETRY)
        assertThat(nextLoadMoreStep(LoadMoreOutcome.APPENDED, 1)).isEqualTo(LoadMoreStep.DONE)
    }

    @Test
    fun `a page of one or two cards is held until the last attempt`() {
        assertThat(loadMoreOutcome(cards = 1, lastAttempt = false, requestFailed = false)).isEqualTo(LoadMoreOutcome.HELD)
        assertThat(loadMoreOutcome(cards = 2, lastAttempt = true, requestFailed = false)).isEqualTo(LoadMoreOutcome.APPENDED)
        assertThat(loadMoreOutcome(cards = HOME_LOAD_MORE_MIN_CARDS, lastAttempt = false, requestFailed = false))
            .isEqualTo(LoadMoreOutcome.APPENDED)
    }

    @Test
    fun `cards found despite a failed request are still shown`() {
        assertThat(loadMoreOutcome(cards = 5, lastAttempt = false, requestFailed = true)).isEqualTo(LoadMoreOutcome.APPENDED)
        assertThat(loadMoreOutcome(cards = 1, lastAttempt = false, requestFailed = true)).isEqualTo(LoadMoreOutcome.HELD)
    }

    @Test
    fun `nothing found is a failure only when a request failed`() {
        assertThat(loadMoreOutcome(cards = 0, lastAttempt = true, requestFailed = true)).isEqualTo(LoadMoreOutcome.FAILED)
        assertThat(loadMoreOutcome(cards = 0, lastAttempt = true, requestFailed = false)).isEqualTo(LoadMoreOutcome.EXHAUSTED)
    }

    @Test
    fun `regenerated queries skip the ones already queued`() {
        val fresh = unaskedQueries(listOf("Lofi", "COD MW3", "Gimme", "Gimme", "Space"), queued = listOf("Lofi", "COD MW3"))

        assertThat(fresh).containsExactly("Gimme", "Space").inOrder()
    }
}
