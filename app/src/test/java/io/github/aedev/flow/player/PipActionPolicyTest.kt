package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PipActionPolicyTest {
    @Test
    fun `playing with next produces headphones pause next in order`() {
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = true,
                hasNext = true,
            )

        assertThat(actions)
            .containsExactly(
                PipAction.BACKGROUND_AUDIO,
                PipAction.PAUSE,
                PipAction.NEXT,
            ).inOrder()
    }

    @Test
    fun `paused without next omits next`() {
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = false,
                hasNext = false,
            )

        assertThat(actions)
            .containsExactly(
                PipAction.BACKGROUND_AUDIO,
                PipAction.PLAY,
            ).inOrder()
    }

    @Test
    fun `playing without related fallback omits next`() {
        val hasNext =
            PipActionPolicy.hasPlayableNext(
                hasQueueNext = false,
                hasRelatedFallback = false,
            )
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = true,
                hasNext = hasNext,
            )

        assertThat(actions)
            .containsExactly(
                PipAction.BACKGROUND_AUDIO,
                PipAction.PAUSE,
            ).inOrder()
    }

    @Test
    fun `related fallback counts as playable next`() {
        val hasNext =
            PipActionPolicy.hasPlayableNext(
                hasQueueNext = false,
                hasRelatedFallback = true,
            )

        assertThat(hasNext).isTrue()
        assertThat(
            PipActionPolicy.selectActions(
                isPlaying = false,
                hasNext = hasNext,
            ),
        ).containsExactly(
            PipAction.BACKGROUND_AUDIO,
            PipAction.PLAY,
            PipAction.NEXT,
        ).inOrder()
    }

    @Test
    fun `smaller platform limit keeps headphones and play pause first`() {
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = true,
                hasNext = true,
                maxActions = 2,
            )

        assertThat(actions)
            .containsExactly(
                PipAction.BACKGROUND_AUDIO,
                PipAction.PAUSE,
            ).inOrder()
    }

    @Test
    fun `single action limit keeps headphones`() {
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = false,
                hasNext = true,
                maxActions = 1,
            )

        assertThat(actions).containsExactly(PipAction.BACKGROUND_AUDIO)
    }

    @Test
    fun `shorts action policy keeps only play pause`() {
        val actions =
            PipActionPolicy.selectActions(
                isPlaying = true,
                hasNext = false,
                includeBackgroundAction = false,
            )

        assertThat(actions).containsExactly(PipAction.PAUSE)
    }
}
