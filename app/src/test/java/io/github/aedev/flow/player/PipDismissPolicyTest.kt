package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PipDismissPolicyTest {
    @Test
    fun `normal backgrounded pip dismissal stops playback`() {
        val shouldDismiss =
            PipDismissPolicy.shouldDismissAfterPipExit(
                stillBackgrounded = true,
                isInPipMode = false,
                explicitBackgroundActive = false,
            )

        assertThat(shouldDismiss).isTrue()
    }

    @Test
    fun `headphones triggered pip exit preserves playback`() {
        val shouldDismiss =
            PipDismissPolicy.shouldDismissAfterPipExit(
                stillBackgrounded = true,
                isInPipMode = false,
                explicitBackgroundActive = true,
            )

        assertThat(shouldDismiss).isFalse()
    }

    @Test
    fun `foreground pip exit does not trigger dismissal cleanup`() {
        val shouldDismiss =
            PipDismissPolicy.shouldDismissAfterPipExit(
                stillBackgrounded = false,
                isInPipMode = false,
                explicitBackgroundActive = false,
            )

        assertThat(shouldDismiss).isFalse()
    }

    @Test
    fun `still in pip never dismisses`() {
        val shouldDismiss =
            PipDismissPolicy.shouldDismissAfterPipExit(
                stillBackgrounded = true,
                isInPipMode = true,
                explicitBackgroundActive = false,
            )

        assertThat(shouldDismiss).isFalse()
    }
}
