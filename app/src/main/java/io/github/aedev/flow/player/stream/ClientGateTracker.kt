package io.github.aedev.flow.player.stream

import io.github.aedev.flow.player.error.StreamDenialClassifier
import io.github.aedev.flow.player.error.StreamDenialKind
import io.github.aedev.flow.utils.potoken.WebPoTokenSession
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
    private val onGated: (clientName: String) -> Unit = {},
) {
    private val gatedUntilMs = ConcurrentHashMap<String, Long>()
    private val refusalStrikes = ConcurrentHashMap<String, Int>()

    fun reportGated(clientName: String?) {
        val key = clientName?.takeIf { it.isNotBlank() }?.uppercase() ?: return
        gatedUntilMs[key] = clockMs() + ttlMs
        onGated(key)
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

    /** Records what a refused googlevideo [url] says about the client that minted it. */
    fun reportDenied(url: String?): StreamDenialKind {
        val kind = StreamDenialClassifier.classify(url, nowSeconds = clockMs() / 1000)
        val client = StreamDenialClassifier.clientOf(url)
        when (kind) {
            StreamDenialKind.ATTESTATION_GATED -> reportGated(client)
            StreamDenialKind.TOKEN_REJECTED -> reportRefused(client)
            StreamDenialKind.URL_EXPIRED, StreamDenialKind.UNKNOWN -> Unit
        }
        return kind
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
object ClientGateTracker : ClientGateRegistry(
    ttlMs = TimeUnit.MINUTES.toMillis(30),
    onGated = { client -> if (client in VISITOR_WALLED_CLIENTS) WebPoTokenSession.rerollAfterWall(client) },
)

/**
 * The app clients GVS walls per visitor rather than per client (#921): a fresh visitor is served
 * on them again, so a gate on one of these replaces the identity as well as demoting the client.
 */
private val VISITOR_WALLED_CLIENTS = setOf("VISIONOS", "ANDROID_VR")
