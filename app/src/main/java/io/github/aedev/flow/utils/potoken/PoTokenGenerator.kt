package io.github.aedev.flow.utils.potoken

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Process-wide owner of the single BotGuard WebView.
 *
 * Based on and ported from Metrolist — see https://github.com/MetrolistGroup/Metrolist for the
 * original code and license.
 *
 * A singleton because attestation is expensive and main-thread bound: constructing a WebView and
 * running the BotGuard challenge both hop to [Dispatchers.Main], so a second instance means a
 * second WebView doing that work concurrently with the first, competing with Compose and the
 * player. The video, music and NewPipe paths previously each held their own generator and could
 * attest three times over; they now share this one, and [webPoTokenGenLock] serialises them.
 */
object PoTokenGenerator {
    private const val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null
    private var webPoTokenStreamingPotLowTrust = false

    fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRefresh: Boolean = false,
    ): PoTokenResult? =
        runBlocking {
            getWebClientPoTokenSuspend(videoId, sessionId, forceRefresh)
        }

    suspend fun getWebClientPoTokenSuspend(
        videoId: String,
        sessionId: String,
        forceRefresh: Boolean = false,
    ): PoTokenResult? {
        Log.d(TAG, "getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Log.d(TAG, "WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Log.d(TAG, "WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        return try {
            generateWebClientPoToken(videoId, sessionId, forceRecreate = forceRefresh)
        } catch (e: Exception) {
            Log.e(TAG, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}", e)
            when (e) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }

                else -> {
                    throw e
                } // includes PoTokenException
            }
        }
    }

    /** Whether the last streaming token BotGuard handed back was a cold, short one. */
    val lastStreamingTokenWasLowTrust: Boolean
        get() = webPoTokenStreamingPotLowTrust

    /**
     * Drops the BotGuard session and the browsing state it was built on. Clearing the WebView jar
     * is the point, not a side effect: it carries the identity BotGuard keeps grading. Nothing
     * signed-in is lost — the app has no account login.
     */
    suspend fun resetSession() {
        webPoTokenGenLock.withLock {
            withContext(NonCancellable + Dispatchers.Main) {
                webPoTokenGenerator?.close()
                runCatching {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    WebStorage.getInstance().deleteAllData()
                }.onFailure { Log.w(TAG, "Could not clear WebView state: ${it.message}") }
            }
            webPoTokenGenerator = null
            webPoTokenSessionId = null
            webPoTokenStreamingPot = null
            webPoTokenStreamingPotLowTrust = false
            webViewBadImpl = false
            Log.w(TAG, "BotGuard session and WebView identity reset")
        }
    }

    fun prewarmWebClient(sessionId: String): Boolean {
        if (sessionId.isBlank() || !webViewSupported || webViewBadImpl) return false
        return try {
            runBlocking { ensureWebPoTokenGenerator(sessionId, forceRecreate = false) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Web poToken prewarm failed: ${e.message}", e)
            if (e is BadWebViewException) webViewBadImpl = true
            false
        }
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun generateWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoTokenResult {
        Log.d(TAG, "Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            ensureWebPoTokenGenerator(sessionId, forceRecreate)

        val playerPot =
            try {
                poTokenGenerator.generatePoToken(videoId)
            } catch (throwable: Throwable) {
                if (hasBeenRecreated) {
                    throw throwable
                } else {
                    Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                    return generateWebClientPoToken(
                        videoId = videoId,
                        sessionId = sessionId,
                        forceRecreate = true,
                    )
                }
            }

        Log.d(TAG, "poToken generated successfully: player=${playerPot.take(20)}..., streaming=${streamingPot.take(20)}...")

        return PoTokenResult(playerPot, streamingPot)
    }

    private suspend fun ensureWebPoTokenGenerator(
        sessionId: String,
        forceRecreate: Boolean,
    ): Triple<PoTokenWebView, String, Boolean> =
        webPoTokenGenLock.withLock {
            val shouldRecreate =
                PoTokenAttestationPolicy.shouldReattest(
                    forceRecreate = forceRecreate,
                    hasSession = webPoTokenGenerator != null,
                    isExpired = webPoTokenGenerator?.isExpired == true,
                    sessionIdChanged = webPoTokenSessionId != sessionId,
                    lastTokenWasLowTrust = webPoTokenStreamingPotLowTrust,
                )

            if (shouldRecreate) {
                Log.d(TAG, "Re-attesting BotGuard session (forceRecreate=$forceRecreate)")

                var newStreamingPot: String? = null
                var lowTrust = true
                // GVS honors cold/low-trust attestations only briefly (the mid-playback 403).
                // Re-run the full BotGuard challenge until the minted token reaches the
                // documented 110-128 byte range, like the desktop minter's retry loop.
                // Bound to the 11-char visitor ID, not the visitorData blob it sits inside: GVS
                // validates the streaming token against the ID, so minting against the blob yields
                // a token that is accepted at load time and refused once enforcement kicks in.
                val gvsBinding = VisitorId.gvsBinding(sessionId)
                var attempt = 0
                while (attempt < STREAMING_POT_ATTEMPTS) {
                    val generator = attestedGenerator()
                    val pot = generator.generatePoToken(gvsBinding)
                    newStreamingPot = pot
                    lowTrust = PoTokenAttestationPolicy.isLowTrust(pot)
                    if (!lowTrust) break
                    attempt++
                    Log.w(
                        TAG,
                        "Streaming poToken is low-trust (${PoTokenAttestationPolicy.tokenLength(pot)} chars, " +
                            "attempt $attempt/$STREAMING_POT_ATTEMPTS)",
                    )
                }
                if (lowTrust) {
                    Log.w(TAG, "Accepting low-trust streaming poToken provisionally; will re-attest on next use")
                }
                // Published only once a token exists: clearing first meant a mint cancelled
                // mid-challenge emptied the session and forced a re-attestation on the next open.
                webPoTokenStreamingPot = newStreamingPot
                webPoTokenSessionId = sessionId
                webPoTokenStreamingPotLowTrust = lowTrust
                Log.d(TAG, "Streaming poToken generated for sessionId=${sessionId.take(20)}... lowTrust=$lowTrust")
            }

            Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
        }

    /**
     * Returns a freshly attested generator, reusing the existing WebView wherever possible.
     *
     * Re-attesting reloads the page, which replaces the JS context wholesale — so it is as clean
     * as a new instance without the main-thread cost of building and destroying a WebView. That
     * cost used to be paid up to three times per call, because the low-trust retry loop
     * constructed a new WebView on every attempt and a low-trust token forces a redo on the next
     * call too. Only a WebView that fails to re-attest is torn down and replaced.
     */
    private suspend fun attestedGenerator(): PoTokenWebView {
        val existing = webPoTokenGenerator?.takeIf { !it.isDestroyed }
        if (existing != null) {
            try {
                existing.attest()
                return existing
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "Re-attestation failed on the existing WebView; rebuilding it", e)
                withContext(NonCancellable + Dispatchers.Main) { existing.close() }
                webPoTokenGenerator = null
            }
        }
        return PoTokenWebView.create(CipherDeobfuscator.appContext).also { webPoTokenGenerator = it }
    }

    private const val STREAMING_POT_ATTEMPTS = 3
}
