package io.github.aedev.flow.utils.cipher

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * This is based and ported from Metrolist,
 * see https://github.com/MetrolistGroup/Metrolist for the original code and license.
 * 
 * Main cipher deobfuscation orchestrator for YouTube stream URLs.
 *
 * Handles both signature deobfuscation (for signatureCipher streams) and
 * n-parameter transformation (for throttle avoidance / 403 fix).
 */
object CipherDeobfuscator {
    private const val TAG = "Flow_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    fun initialize(context: Context) {
        Log.d(TAG, "CipherDeobfuscator initializing...")
        appContext = context.applicationContext
        Log.d(TAG, "CipherDeobfuscator initialized")
    }

    private var cipherWebView: CipherWebView? = null
    private var currentPlayerHash: String? = null

    /**
     * Deobfuscate a signatureCipher stream URL.
     * Returns the full URL with deobfuscated signature, or null if failed.
     */
    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? {
        Log.d(TAG, "deobfuscateStreamUrl: videoId=$videoId, cipher length=${signatureCipher.length}")
        return try {
            deobfuscateInternal(signatureCipher, videoId, isRetry = false)
        } catch (e: Exception) {
            Log.e(TAG, "Cipher deobfuscation failed, retrying with fresh JS: ${e.message}", e)
            try {
                PlayerJsFetcher.invalidateCache()
                closeWebView()
                deobfuscateInternal(signatureCipher, videoId, isRetry = true)
            } catch (retryE: Exception) {
                Log.e(TAG, "Cipher deobfuscation retry also failed: ${retryE.message}", retryE)
                null
            }
        }
    }

    private suspend fun deobfuscateInternal(signatureCipher: String, videoId: String, isRetry: Boolean): String? {
        val params = parseQueryParams(signatureCipher)
        val obfuscatedSig = params["s"]
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"]

        if (obfuscatedSig == null || baseUrl == null) {
            Log.e(TAG, "Could not parse signatureCipher params: s=${obfuscatedSig != null}, url=${baseUrl != null}")
            return null
        }

        val webView = getOrCreateWebView(forceRefresh = isRetry) ?: run {
            Log.e(TAG, "Failed to get/create CipherWebView")
            return null
        }

        val deobfuscatedSig = webView.deobfuscateSignature(obfuscatedSig)
        val separator = if ("?" in baseUrl) "&" else "?"
        val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"

        Log.d(TAG, "Cipher deobfuscation success: videoId=$videoId, url length=${finalUrl.length}")
        return finalUrl
    }

    /**
     * Transform the 'n' parameter in a streaming URL to avoid throttling/403.
     * Returns the URL with the transformed 'n' value, or the original URL if transform fails.
     *
     * IMPORTANT: Must be called for WEB_REMIX, WEB, WEB_CREATOR, TVHTML5 clients.
     */
    suspend fun transformNParamInUrl(url: String): String {
        Log.d(TAG, "transformNParamInUrl: url length=${url.length}")
        return try {
            transformNInternal(url)
        } catch (e: Exception) {
            Log.e(TAG, "N-transform failed, returning original URL: ${e.message}", e)
            url
        }
    }

    private suspend fun transformNInternal(url: String): String {
        val nMatch = Regex("[?&]n=([^&]+)").find(url)
        if (nMatch == null) {
            Log.d(TAG, "No 'n' parameter found in URL, skipping transform")
            return url
        }

        val nValueEncoded = nMatch.groupValues[1]
        val nValue = Uri.decode(nValueEncoded)
        Log.d(TAG, "N-param: encoded=$nValueEncoded, decoded=$nValue")

        val webView = getOrCreateWebView(forceRefresh = false) ?: run {
            Log.e(TAG, "Failed to get CipherWebView for n-transform")
            return url
        }

        if (!webView.nFunctionAvailable) {
            Log.e(TAG, "N-transform function was not discovered at init time")
            return url
        }

        val transformedN = webView.transformN(nValue)
        Log.d(TAG, "N-transform: $nValue -> $transformedN")

        return url.replaceFirst(
            Regex("([?&])n=[^&]+"),
            "$1n=${Uri.encode(transformedN)}"
        )
    }

    private suspend fun getOrCreateWebView(forceRefresh: Boolean): CipherWebView? {
        Log.d(TAG, "getOrCreateWebView: forceRefresh=$forceRefresh, existing=${cipherWebView != null}")

        if (!forceRefresh && cipherWebView != null) {
            return cipherWebView
        }

        if (cipherWebView != null) {
            closeWebView()
        }

        val result = PlayerJsFetcher.getPlayerJs(forceRefresh = forceRefresh)
        if (result == null) {
            Log.e(TAG, "Failed to get player JS")
            return null
        }
        val (playerJs, hash) = result
        Log.d(TAG, "Got player JS: hash=$hash, length=${playerJs.length}")

        val analysis = FunctionNameExtractor.analyzePlayerJs(playerJs, knownHash = hash)

        if (analysis.sigInfo == null) {
            Log.e(TAG, "Could not extract signature function info from player JS")
            return null
        }

        if (analysis.nFuncInfo == null) {
            Log.w(TAG, "Could not extract n-function info from player JS (will try brute-force)")
        }

        Log.d(TAG, "Creating CipherWebView: sig=${analysis.sigInfo.name}, nFunc=${analysis.nFuncInfo?.name}")
        val webView = CipherWebView.create(
            context = appContext,
            playerJs = playerJs,
            sigInfo = analysis.sigInfo,
            nFuncInfo = analysis.nFuncInfo,
        )

        Log.d(TAG, "CipherWebView created: nAvailable=${webView.nFunctionAvailable}, sigAvailable=${webView.sigFunctionAvailable}")
        cipherWebView = webView
        currentPlayerHash = hash
        return webView
    }

    private suspend fun closeWebView() {
        withContext(Dispatchers.Main) {
            cipherWebView?.close()
        }
        cipherWebView = null
        currentPlayerHash = null
        Log.d(TAG, "CipherWebView closed")
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf('=')
            if (idx > 0) {
                result[Uri.decode(pair.substring(0, idx))] = Uri.decode(pair.substring(idx + 1))
            }
        }
        return result
    }
}
