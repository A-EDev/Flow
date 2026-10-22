package io.github.aedev.flow.ui.screens.music

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicSeekGuardTest {
    private var clock = 0L
    private val guard = MusicSeekGuard(now = { clock })

    @Test
    fun `reports pass straight through when nothing was sought`() {
        assertThat(guard.accept(42_000L)).isEqualTo(42_000L)
    }

    @Test
    fun `a stale report before the seek lands is held back`() {
        guard.seekRequested(60_000L)
        clock = 100L

        assertThat(guard.accept(5_000L)).isNull()
    }

    @Test
    fun `the guard releases once the player reaches the target and the hold has passed`() {
        guard.seekRequested(60_000L)
        clock = 300L

        assertThat(guard.accept(60_400L)).isEqualTo(60_400L)
        assertThat(guard.accept(5_000L)).isEqualTo(5_000L)
    }

    @Test
    fun `a seek that never lands stops holding after the guard time`() {
        guard.seekRequested(60_000L)
        clock = 1_500L

        assertThat(guard.accept(5_000L)).isEqualTo(5_000L)
    }
}
