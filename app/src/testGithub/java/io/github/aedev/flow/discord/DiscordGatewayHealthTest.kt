package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordGatewayHealthTest {
    @Test
    fun `reconnect backoff grows caps and resets`() {
        var now = 1_000L
        val backoff = DiscordReconnectBackoff(
            nowElapsedMs = { now },
            jitterMs = { 0L },
            initialDelayMs = 100L,
            maximumDelayMs = 400L,
        )

        assertThat(backoff.canAttempt()).isTrue()
        backoff.recordFailure()
        assertThat(backoff.canAttempt()).isFalse()
        now += 100L
        assertThat(backoff.canAttempt()).isTrue()

        backoff.recordFailure()
        now += 199L
        assertThat(backoff.canAttempt()).isFalse()
        now += 1L
        assertThat(backoff.canAttempt()).isTrue()

        repeat(8) {
            backoff.recordFailure()
            now += 400L
            assertThat(backoff.canAttempt()).isTrue()
        }

        backoff.recordFailure()
        backoff.reset()
        assertThat(backoff.canAttempt()).isTrue()
    }

    @Test
    fun `heartbeat requires an acknowledgement before the next periodic send`() {
        val tracker = DiscordHeartbeatTracker()

        assertThat(tracker.markPeriodicHeartbeatSent()).isTrue()
        assertThat(tracker.markPeriodicHeartbeatSent()).isFalse()

        tracker.acknowledge()
        assertThat(tracker.markPeriodicHeartbeatSent()).isTrue()

        tracker.reset()
        assertThat(tracker.markPeriodicHeartbeatSent()).isTrue()
    }

    @Test
    fun `server requested heartbeat still requires a later acknowledgement`() {
        val tracker = DiscordHeartbeatTracker()

        tracker.markRequestedHeartbeatSent()

        assertThat(tracker.markPeriodicHeartbeatSent()).isFalse()
        tracker.acknowledge()
        assertThat(tracker.markPeriodicHeartbeatSent()).isTrue()
    }
}
