package io.github.aedev.flow.discord

import android.os.SystemClock
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

internal class DiscordReconnectBackoff(
    private val nowElapsedMs: () -> Long = SystemClock::elapsedRealtime,
    private val jitterMs: (Long) -> Long = { upperBound -> Random.nextLong(upperBound) },
    private val initialDelayMs: Long = TimeUnit.SECONDS.toMillis(5),
    private val maximumDelayMs: Long = TimeUnit.MINUTES.toMillis(5),
) {
    private var failures = 0
    private var nextAttemptAtMs = 0L

    fun canAttempt(): Boolean = nowElapsedMs() >= nextAttemptAtMs

    fun recordFailure() {
        failures = (failures + 1).coerceAtMost(MAX_EXPONENT)
        val baseDelay = min(
            initialDelayMs * (1L shl (failures - 1)),
            maximumDelayMs,
        )
        val jitterBound = (baseDelay / 4L).coerceAtLeast(1L)
        nextAttemptAtMs = nowElapsedMs() + baseDelay + jitterMs(jitterBound)
    }

    fun reset() {
        failures = 0
        nextAttemptAtMs = 0L
    }

    private companion object {
        const val MAX_EXPONENT = 7
    }
}

internal class DiscordHeartbeatTracker {
    private var awaitingAcknowledgement = false

    @Synchronized
    fun markPeriodicHeartbeatSent(): Boolean {
        if (awaitingAcknowledgement) return false
        awaitingAcknowledgement = true
        return true
    }

    @Synchronized
    fun markRequestedHeartbeatSent() {
        awaitingAcknowledgement = true
    }

    @Synchronized
    fun acknowledge() {
        awaitingAcknowledgement = false
    }

    @Synchronized
    fun reset() {
        awaitingAcknowledgement = false
    }
}
