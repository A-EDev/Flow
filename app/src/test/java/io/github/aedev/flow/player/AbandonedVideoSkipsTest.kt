package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AbandonedVideoSkipsTest {
    @Test
    fun `a refused video in a queue is skipped`() {
        assertThat(AbandonedVideoSkips().trySkip(hasNextInQueue = true)).isTrue()
    }

    @Test
    fun `the last video in the queue is not skipped`() {
        assertThat(AbandonedVideoSkips().trySkip(hasNextInQueue = false)).isFalse()
    }

    @Test
    fun `a run of refusals stops the queue instead of skipping through it`() {
        val skips = AbandonedVideoSkips(limit = 2)

        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
        assertThat(skips.trySkip(hasNextInQueue = true)).isFalse()
    }

    @Test
    fun `a video that plays starts the count over`() {
        val skips = AbandonedVideoSkips(limit = 2)
        skips.trySkip(hasNextInQueue = true)
        skips.trySkip(hasNextInQueue = true)

        skips.onPlaybackStarted()

        assertThat(skips.trySkip(hasNextInQueue = true)).isTrue()
    }
}
