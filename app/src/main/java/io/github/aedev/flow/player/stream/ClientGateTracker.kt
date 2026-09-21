package io.github.aedev.flow.player.stream

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Remembers which InnerTube clients GVS is currently refusing to serve unattested.
 *
 * Without this the client ladder restarts at the same gated client for every video: the refusal
 * arrives as an HTTP 403 on a URL that has already been discarded, so nothing survives the
 * re-extraction to say "that one is being enforced right now". The result a user sees is every
 * video playing for about a minute and then stalling, one after another.
 *
 * Keyed by [io.github.aedev.flow.innertube.models.YouTubeClient.clientName] because that is what
 * the failing URL's `c=` parameter reports, and because enforcement is bound to the client
 * identity rather than to a particular build of it.
 *
 * Entries lapse on a timer rather than on a connectivity callback. A gate does travel with the
 * network, but registering an app-wide network callback to catch that would cost battery in every
 * session to serve a minority failure; the timer is short enough that a genuine network change is
 * re-probed within one, and [clear] covers the cases the app already knows about.
 */
open class ClientGateRegistry(
    private val ttlMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val gatedUntilMs = ConcurrentHashMap<String, Long>()

    fun reportGated(clientName: String?) {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return
        gatedUntilMs[key] = clockMs() + ttlMs
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
    }
}

/**
 * The process-wide registry. Deliberately not persisted: a gate is a property of the current
 * network and visitor identity, and a stale one written to disk would demote a working client for
 * a user who has since moved networks.
 */
object ClientGateTracker : ClientGateRegistry(TimeUnit.MINUTES.toMillis(30))
