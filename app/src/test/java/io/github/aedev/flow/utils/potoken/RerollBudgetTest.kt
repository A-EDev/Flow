package io.github.aedev.flow.utils.potoken

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RerollBudgetTest {
    private val now = 100_000_000L

    @Test
    fun `two re-rolls fit in the window and a third does not`() {
        val first = RerollBudget.take(emptyList(), now)!!
        val second = RerollBudget.take(first, now + 1_000L)!!

        assertThat(RerollBudget.take(second, now + 2_000L)).isNull()
    }

    @Test
    fun `a re-roll older than the window frees its slot`() {
        val spent = listOf(now - RerollBudget.WINDOW_MS, now - 1_000L)

        assertThat(RerollBudget.take(spent, now)).containsExactly(now - 1_000L, now).inOrder()
    }

    @Test
    fun `a stamp in the future, after a clock change, does not count against the budget`() {
        assertThat(RerollBudget.take(listOf(now + 60_000L, now + 120_000L), now)).containsExactly(now)
    }

    @Test
    fun `the history survives a round trip through its stored form`() {
        val history = listOf(now - 5L, now)

        assertThat(RerollBudget.parse(RerollBudget.format(history))).isEqualTo(history)
        assertThat(RerollBudget.parse(null)).isEmpty()
        assertThat(RerollBudget.parse("garbage,$now")).containsExactly(now)
    }
}
