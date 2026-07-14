package io.github.aedev.flow.discord

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

class DiscordPresenceCoordinator(
    private val enabled: Flow<Boolean>,
    private val playback: Flow<PlaybackSnapshot?>,
    private val transport: DiscordPresenceTransport,
    private val mapper: DiscordPresenceMapper = DiscordPresenceMapper(),
    private val policy: DiscordPresencePolicy = DiscordPresencePolicy(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val nowElapsedMs: () -> Long,
) {
    private var lastSent: SentPresence? = null

    suspend fun run() {
        combine(enabled, playback) { isEnabled, snapshot -> isEnabled to snapshot }
            .collect { (isEnabled, snapshot) ->
                if (!isEnabled || snapshot == null) {
                    clearPresence()
                    return@collect
                }

                val candidate = mapper.map(snapshot, nowEpochSeconds())
                if (candidate == null) {
                    clearPresence()
                    return@collect
                }

                when (val decision = policy.decide(lastSent, candidate, nowElapsedMs())) {
                    is DiscordPresenceDecision.Send -> {
                        if (transport.update(decision.payload)) {
                            lastSent = SentPresence(
                                payload = decision.payload,
                                sentAtElapsedMs = nowElapsedMs(),
                            )
                        }
                    }

                    DiscordPresenceDecision.Skip -> Unit
                }
            }
    }

    private suspend fun clearPresence() {
        if (transport.clear()) {
            lastSent = null
        }
    }
}
