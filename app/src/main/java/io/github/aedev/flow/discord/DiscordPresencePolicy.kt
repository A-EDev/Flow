package io.github.aedev.flow.discord

import kotlin.math.abs

class DiscordPresencePolicy(
    private val minimumUpdateIntervalMs: Long = 5_000L,
    private val seekDriftThresholdMs: Long = 10_000L,
) {
    fun decide(
        previous: SentPresence?,
        candidate: DiscordPresencePayload,
        nowElapsedMs: Long,
    ): DiscordPresenceDecision {
        if (previous == null) return DiscordPresenceDecision.Send(candidate)

        if (
            previous.payload.mediaId != candidate.mediaId ||
            previous.payload.type != candidate.type
        ) {
            return DiscordPresenceDecision.Send(candidate)
        }

        val previousWithoutTimestamps = previous.payload.withoutTimestamps()
        val candidateWithoutTimestamps = candidate.withoutTimestamps()
        val intervalElapsed = nowElapsedMs - previous.sentAtElapsedMs >= minimumUpdateIntervalMs

        if (previousWithoutTimestamps != candidateWithoutTimestamps) {
            return if (intervalElapsed) {
                DiscordPresenceDecision.Send(candidate)
            } else {
                DiscordPresenceDecision.Skip
            }
        }

        val meaningfulSeek = maxOf(
            timestampDriftMs(
                previous.payload.startTimestampSeconds,
                candidate.startTimestampSeconds,
            ),
            timestampDriftMs(
                previous.payload.endTimestampSeconds,
                candidate.endTimestampSeconds,
            ),
        ) >= seekDriftThresholdMs

        return if (meaningfulSeek && intervalElapsed) {
            DiscordPresenceDecision.Send(candidate)
        } else {
            DiscordPresenceDecision.Skip
        }
    }

    private fun DiscordPresencePayload.withoutTimestamps(): DiscordPresencePayload = copy(
        startTimestampSeconds = null,
        endTimestampSeconds = null,
    )

    private fun timestampDriftMs(left: Long?, right: Long?): Long = when {
        left == null && right == null -> 0L
        left == null || right == null -> Long.MAX_VALUE
        else -> abs(left - right) * 1_000L
    }
}
