package io.github.aedev.flow.player.stream

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Remembers which InnerTube clients GVS is currently refusing, so the ladder stops restarting at
 * one: a 403 arrives on a URL that has already been discarded, so without this nothing survives the
 * re-extraction and every video plays for a minute and stalls.
 *
 * Keyed by `clientName`, which is what the failing URL's `c=` parameter reports. Entries lapse on a
 * timer rather than a connectivity callback, which would cost battery in every session to serve a
 * minority failure.
 */
open class ClientGateRegistry(
    private val ttlMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val gatedUntilMs = ConcurrentHashMap<String, Long>()
    private val refusalStrikes = ConcurrentHashMap<String, Int>()

    fun reportGated(clientName: String?) {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return
        gatedUntilMs[key] = clockMs() + ttlMs
    }

    /**
     * GVS took the client's PO Token and refused it. Demoted on the second strike, because one
     * refusal can be a cold attestation the next mint fixes and the first strike would drop the
     * only client whose token the app can mint at all.
     *
     * @return true when this strike demoted the client.
     */
    fun reportRefused(clientName: String?): Boolean {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return false
        val strikes = refusalStrikes.merge(key, 1, Int::plus) ?: 1
        if (strikes < REFUSALS_BEFORE_DEMOTION) return false
        refusalStrikes.remove(key)
        gatedUntilMs[key] = clockMs() + ttlMs
        return true
    }

    fun isGated(clientName: String?): Boolean {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return false
        val until = gatedUntilMs[key] ?: return false
        if (clockMs() >= until) {
            gatedUntilMs.remove(key, until)
            return false
        }
        return true
    }

    /** The clients currently demoted, for diagnostics. Lapsed entries are dropped on the way out. */
    fun gatedClients(): Set<String> {
        val now = clockMs()
        gatedUntilMs.entries.removeAll { it.value <= now }
        return gatedUntilMs.keys.toSet()
    }

    fun clear() {
        gatedUntilMs.clear()
        refusalStrikes.clear()
    }

    private companion object {
        const val REFUSALS_BEFORE_DEMOTION = 2
    }
}

/**
 * The process-wide registry. Deliberately not persisted: a gate is a property of the current
 * network and visitor identity, and a stale one written to disk would demote a working client for
 * a user who has since moved networks.
 */
object ClientGateTracker : ClientGateRegistry(TimeUnit.MINUTES.toMillis(30))
