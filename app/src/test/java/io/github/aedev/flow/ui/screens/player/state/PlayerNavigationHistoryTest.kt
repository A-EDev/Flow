package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerNavigationHistoryTest {
    @Test
    fun `a fresh history has nothing to go back to`() {
        val history = PlayerNavigationHistory()

        assertThat(history.canGoPrevious).isFalse()
        assertThat(history.previous()).isNull()
    }

    @Test
    fun `the first entry is still not something to go back from`() {
        val history = PlayerNavigationHistory()

        history.push("a")

        assertThat(history.canGoPrevious).isFalse()
        assertThat(history.previous()).isNull()
    }

    @Test
    fun `previous walks back one entry at a time and stops at the first`() {
        val history = PlayerNavigationHistory()
        history.push("a")
        history.push("b")
        history.push("c")

        assertThat(history.canGoPrevious).isTrue()
        assertThat(history.previous()).isEqualTo("b")
        assertThat(history.previous()).isEqualTo("a")
        assertThat(history.canGoPrevious).isFalse()
        assertThat(history.previous()).isNull()
    }

    @Test
    fun `re-pushing the entry being watched does not grow the history`() {
        val history = PlayerNavigationHistory()

        history.push("a")
        history.push("a")
        history.push("a")

        assertThat(history.size).isEqualTo(1)
        assertThat(history.canGoPrevious).isFalse()
    }

    @Test
    fun `the same id opened again after a step back is a new entry`() {
        val history = PlayerNavigationHistory()
        history.push("a")
        history.push("b")
        history.previous()

        history.push("b")

        assertThat(history.size).isEqualTo(2)
        assertThat(history.previous()).isEqualTo("a")
    }

    @Test
    fun `opening a video after stepping back drops the entries ahead of the cursor`() {
        val history = PlayerNavigationHistory()
        history.push("a")
        history.push("b")
        history.push("c")
        history.previous()
        history.previous()

        history.push("d")

        assertThat(history.size).isEqualTo(2)
        assertThat(history.previous()).isEqualTo("a")
        assertThat(history.canGoPrevious).isFalse()
    }

    @Test
    fun `the history is capped so a long session cannot grow it without bound`() {
        val history = PlayerNavigationHistory(maxEntries = 3)

        listOf("a", "b", "c", "d", "e").forEach(history::push)

        assertThat(history.size).isEqualTo(3)
        assertThat(history.previous()).isEqualTo("d")
        assertThat(history.previous()).isEqualTo("c")
        assertThat(history.previous()).isNull()
    }

    @Test
    fun `clear empties the history`() {
        val history = PlayerNavigationHistory()
        history.push("a")
        history.push("b")

        history.clear()

        assertThat(history.size).isEqualTo(0)
        assertThat(history.canGoPrevious).isFalse()
        assertThat(history.previous()).isNull()

        history.push("c")
        assertThat(history.size).isEqualTo(1)
    }
}
