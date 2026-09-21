package io.github.aedev.flow.utils.potoken

import android.util.Log
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.player.stream.InFlightRequestCoalescer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single source of truth for the WEB BotGuard PoToken session used by the **native video
 * stream extractor** (separate from [NewPipePoTokenProvider], which serves the NewPipe path).
 */
object WebPoTokenSession {
    private const val TAG = "WebPoTokenSession"

    /**
     * Server refusals tolerated before the visitor identity itself is replaced. Two, because one
     * refusal can be a cold attestation the next mint fixes, while a second under the same identity
     * means the grade is the identity's rather than the attempt's.
     */
    private const val REFUSALS_BEFORE_ROTATION = 2

    /**
     * A rotation destroys and rebuilds the BotGuard WebView on the main thread, so it is rate
     * limited. Without this, a device whose attestation never improves rotates on every reload.
     */
    private const val ROTATION_COOLDOWN_MS = 5 * 60 * 1000L

    private val generator = PoTokenGenerator
    private val visitorMutex = Mutex()
    private val rotationMutex = Mutex()

    @Volatile
    private var consecutiveLowTrustMints = 0

    /**
     * Counted separately from cold mints and never cleared by a good mint. A re-attestation after a
     * rejection usually *does* hand back a full-trust token, so folding the two together let a
     * refused-token loop reset its own counter on every pass and rotate nothing.
     */
    @Volatile
    private var tokenRejections = 0

    @Volatile
    private var forceReattestNextMint = false

    @Volatile
    private var lastRotationMs = 0L

    /**
     * Only a server refusal justifies replacing the identity.
     *
     * A cold mint does not: measured on device 2026-09-21, a handset whose BotGuard never returns
     * more than ~88 bytes played normally for a minute on those tokens, and rotating on the length
     * heuristic alone rebuilt the WebView on a loop that could never reach a different verdict.
     * The byte length stays a diagnostic; the 403 is the signal.
     */
    private val attestationStuck: Boolean
        get() = tokenRejections >= REFUSALS_BEFORE_ROTATION

    /**
     * Bumped every time the visitor identity is replaced. Lets callers that cached a verdict about
     * the old identity — which client GVS was refusing, say — notice it no longer applies.
     */
    @Volatile
    var identityGeneration: Int = 0
        private set

    suspend fun sessionVisitorData(): String? {
        if (attestationStuck) rotateVisitorIdentity()
        YouTube.visitorData?.takeIf { it.isNotBlank() }?.let { return it }
        return visitorMutex.withLock {
            YouTube.visitorData?.takeIf { it.isNotBlank() }?.let { return it }
            val fetched = YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() }
            if (fetched != null) {
                YouTube.visitorData = fetched
                Log.d(TAG, "Fetched session visitorData for WEB PoToken")
            } else {
                Log.w(TAG, "Could not obtain visitorData for WEB PoToken")
            }
            fetched
        }
    }

    /**
     * Mint the player + streaming PoToken pair for [videoId], bound to the session visitorData.
     * Returns null if a visitorData is unavailable or the WebView/BotGuard path is unusable

     */
    suspend fun mint(videoId: String): PoTokenResult? {
        val vd = sessionVisitorData() ?: return null
        return mintForVisitorData(videoId, vd)
    }

    private val mintCoalescer =
        InFlightRequestCoalescer<String, PoTokenResult?>(
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

    // Mint with a bounded wait for the fast extraction path.
    suspend fun mintBounded(
        videoId: String,
        maxWaitMs: Long = 10_000L,
    ): PoTokenResult? {
        return withTimeoutOrNull(maxWaitMs) {
            mintCoalescer.run(videoId) {
                val vd = sessionVisitorData() ?: return@run null
                try {
                    generator
                        .getWebClientPoTokenSuspend(videoId, vd, consumeReattestationRequest())
                        .also { noteMintTrust(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Bounded PoToken mint failed for $videoId: ${e.message}")
                    null
                }
            }
        }
    }

    /** Mint against the exact visitor identity carried by the corresponding player response. */
    suspend fun mintForVisitorData(
        videoId: String,
        visitorData: String,
    ): PoTokenResult? = mintForVisitorData(videoId, visitorData, forceRefresh = false)

    /** Re-run attestation and replace the cached streaming token after a protection boundary. */
    suspend fun refreshForVisitorData(
        videoId: String,
        visitorData: String,
    ): PoTokenResult? = mintForVisitorData(videoId, visitorData, forceRefresh = true)

    private suspend fun mintForVisitorData(
        videoId: String,
        visitorData: String,
        forceRefresh: Boolean,
    ): PoTokenResult? {
        if (visitorData.isBlank()) return null
        return withTimeoutOrNull(90_000L) {
            try {
                generator
                    .getWebClientPoTokenSuspend(videoId, visitorData, forceRefresh || consumeReattestationRequest())
                    .also { noteMintTrust(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "PoToken mint failed for $videoId: ${e.message}")
                null
            }
        }
    }

    /**
     * GVS accepted the request but refused the token on it.
     *
     * Counted alongside cold mints because it is the same verdict arriving later: the token was
     * well-formed and correctly bound, and the server still would not have it. The next mint
     * re-attests rather than handing back the cached token GVS has already rejected, and a second
     * rejection takes the identity itself out of service.
     */
    fun reportTokenRejected() {
        forceReattestNextMint = true
        tokenRejections++
        Log.w(TAG, "GVS refused the PO Token ($tokenRejections/$REFUSALS_BEFORE_ROTATION before rotating)")
    }

    private fun consumeReattestationRequest(): Boolean {
        if (!forceReattestNextMint) return false
        forceReattestNextMint = false
        return true
    }

    /**
     * Counts cold mints, but never rotates here: the caller is about to send this token *alongside*
     * the visitorData it was minted for, and swapping the identity underneath it would produce a
     * mismatched pair. The rotation happens in [sessionVisitorData], where both are read together.
     */
    private fun noteMintTrust(result: PoTokenResult?) {
        if (result == null) return
        consecutiveLowTrustMints =
            if (generator.lastStreamingTokenWasLowTrust) {
                val count = consecutiveLowTrustMints + 1
                Log.w(TAG, "Cold streaming token under the current visitor (mint #$count) — diagnostic only")
                count
            } else {
                0
            }
    }

    /**
     * Replaces the visitor identity the BotGuard session is graded against.
     *
     * Re-attesting alone cannot lift a stuck verdict — the challenge is re-run under the same
     * visitor ID, which is the thing GVS has already judged. This is the supported version of what
     * users discovered by clearing app data, and [resetSession] discards the WebView jar the
     * identity lives in so the replacement really is a new one.
     */
    suspend fun rotateVisitorIdentity() {
        rotationMutex.withLock {
            // Another caller may have rotated while this one waited for the lock.
            if (!attestationStuck) return@withLock
            val sinceLast = System.currentTimeMillis() - lastRotationMs
            if (lastRotationMs != 0L && sinceLast < ROTATION_COOLDOWN_MS) {
                Log.w(TAG, "Attestation still refused, but the identity was rotated ${sinceLast}ms ago — not rotating again")
                tokenRejections = 0
                return@withLock
            }
            Log.w(TAG, "Attestation refused $tokenRejections times (cold mints=$consecutiveLowTrustMints) — rotating the visitor identity")
            lastRotationMs = System.currentTimeMillis()
            consecutiveLowTrustMints = 0
            tokenRejections = 0
            generator.resetSession()
            YouTube.visitorData = null
            identityGeneration++
        }
    }

    /** Unconditional reset, for the user-facing "Reset YouTube session" action. */
    suspend fun resetIdentity() {
        rotationMutex.withLock {
            consecutiveLowTrustMints = 0
            tokenRejections = 0
            generator.resetSession()
            YouTube.visitorData = null
            identityGeneration++
        }
    }

    // Pre-warm the BotGuard session at app start so the first real extraction is fast.
    suspend fun prewarm() {
        try {
            val visitorData = sessionVisitorData() ?: return
            withContext(Dispatchers.IO) {
                generator.prewarmWebClient(visitorData)
            }
        } catch (e: Exception) {
            Log.w(TAG, "prewarm failed: ${e.message}")
        }
    }
}
