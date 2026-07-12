package io.github.aedev.flow.utils.potoken

import android.util.Log
import android.webkit.CookieManager
import io.github.aedev.flow.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 */

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

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
        forceRefresh: Boolean = false
    ): PoTokenResult? = runBlocking {
        getWebClientPoTokenSuspend(videoId, sessionId, forceRefresh)
    }

    suspend fun getWebClientPoTokenSuspend(
        videoId: String,
        sessionId: String,
        forceRefresh: Boolean = false
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
                else -> throw e // includes PoTokenException
            }
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
        forceRecreate: Boolean
    ): PoTokenResult {
        Log.d(TAG, "Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            ensureWebPoTokenGenerator(sessionId, forceRecreate)

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                throw throwable
            } else {
                Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return generateWebClientPoToken(
                    videoId = videoId,
                    sessionId = sessionId,
                    forceRecreate = true
                )
            }
        }

        Log.d(TAG, "poToken generated successfully: player=${playerPot.take(20)}..., streaming=${streamingPot.take(20)}...")

        return PoTokenResult(playerPot, streamingPot)
    }

    private suspend fun ensureWebPoTokenGenerator(
        sessionId: String,
        forceRecreate: Boolean
    ): Triple<PoTokenWebView, String, Boolean> = webPoTokenGenLock.withLock {
        // A low-trust token is never pinned: keep re-attesting until a full-trust one lands.
        val shouldRecreate = forceRecreate || webPoTokenGenerator == null ||
            webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId ||
            webPoTokenStreamingPotLowTrust

        if (shouldRecreate) {
            Log.d(TAG, "Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
            withContext(Dispatchers.Main) {
                webPoTokenGenerator?.close()
            }
            webPoTokenGenerator = null
            webPoTokenStreamingPot = null
            webPoTokenSessionId = null

            var newGenerator: PoTokenWebView? = null
            var newStreamingPot: String? = null
            var lowTrust = true
            try {
                // GVS honors cold/low-trust attestations only briefly (the mid-playback 403).
                // Re-run the full BotGuard challenge until the minted token reaches the
                // documented 110-128 byte range, like the desktop minter's retry loop.
                var attempt = 0
                while (attempt < STREAMING_POT_ATTEMPTS) {
                    newGenerator?.let { old -> withContext(Dispatchers.Main) { old.close() } }
                    val generator = PoTokenWebView.getNewPoTokenGenerator(
                        CipherDeobfuscator.appContext
                    )
                    newGenerator = generator
                    val pot = generator.generatePoToken(sessionId)
                    newStreamingPot = pot
                    lowTrust = tokenByteLength(pot) < MIN_TRUSTED_POT_BYTES
                    if (!lowTrust) break
                    attempt++
                    Log.w(
                        TAG,
                        "Streaming poToken is low-trust (${tokenByteLength(pot)} bytes, " +
                            "attempt $attempt/$STREAMING_POT_ATTEMPTS)"
                    )
                }
                if (lowTrust) {
                    Log.w(TAG, "Accepting low-trust streaming poToken provisionally; will re-attest on next use")
                }
            } catch (error: Throwable) {
                newGenerator?.let { gen -> withContext(Dispatchers.Main) { gen.close() } }
                throw error
            }
            webPoTokenGenerator = newGenerator
            webPoTokenStreamingPot = newStreamingPot
            webPoTokenSessionId = sessionId
            webPoTokenStreamingPotLowTrust = lowTrust
            Log.d(TAG, "Streaming poToken generated for sessionId=${sessionId.take(20)}... lowTrust=$lowTrust")
        }

        Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
    }

    companion object {
        // BgUtils documents full-trust content-bound PoTokens at 110-128 bytes; short (~88 byte)
        // tokens are cold/low-trust attestations that GVS rejects shortly into playback.
        private const val MIN_TRUSTED_POT_BYTES = 100
        private const val STREAMING_POT_ATTEMPTS = 3

        internal fun tokenByteLength(base64Token: String): Int =
            base64Token.trimEnd('=').length * 3 / 4
    }
}
