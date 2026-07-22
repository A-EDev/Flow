package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordPresencePolicyTest {
    private val policy = DiscordPresencePolicy(
        minimumUpdateIntervalMs = 5_000L,
        seekDriftThresholdMs = 10_000L,
    )

    private val base = DiscordPresencePayload(
        type = DiscordActivityType.WATCHING,
        mediaId = "video-1",
        details = "Title",
        state = "by Creator",
        largeImage = "flow_logo",
        largeImageText = "Title",
        startTimestampSeconds = 100L,
        endTimestampSeconds = 200L,
    )

    @Test
    fun firstPayloadIsSent() {
        assertThat(policy.decide(null, base, 1_000L))
            .isEqualTo(DiscordPresenceDecision.Send(base))
    }

    @Test
    fun identicalPayloadIsSkipped() {
        assertThat(
            policy.decide(
                previous = SentPresence(base, sentAtElapsedMs = 1_000L),
                candidate = base,
                nowElapsedMs = 8_000L,
            ),
        ).isEqualTo(DiscordPresenceDecision.Skip)
    }

    @Test
    fun changedMediaBypassesMinimumInterval() {
        val changed = base.copy(mediaId = "video-2", details = "Next Video")

        assertThat(
            policy.decide(
                previous = SentPresence(base, sentAtElapsedMs = 1_000L),
                candidate = changed,
                nowElapsedMs = 2_000L,
            ),
        ).isEqualTo(DiscordPresenceDecision.Send(changed))
    }

    @Test
    fun timestampDriftBelowThresholdIsSkipped() {
        val drifted = base.copy(
            startTimestampSeconds = 95L,
            endTimestampSeconds = 195L,
        )

        assertThat(
            policy.decide(
                previous = SentPresence(base, sentAtElapsedMs = 1_000L),
                candidate = drifted,
                nowElapsedMs = 7_000L,
            ),
        ).isEqualTo(DiscordPresenceDecision.Skip)
    }

    @Test
    fun timestampDriftAtThresholdIsSentAfterMinimumInterval() {
        val drifted = base.copy(
            startTimestampSeconds = 90L,
            endTimestampSeconds = 190L,
        )

        assertThat(
            policy.decide(
                previous = SentPresence(base, sentAtElapsedMs = 1_000L),
                candidate = drifted,
                nowElapsedMs = 7_000L,
            ),
        ).isEqualTo(DiscordPresenceDecision.Send(drifted))
    }
}
