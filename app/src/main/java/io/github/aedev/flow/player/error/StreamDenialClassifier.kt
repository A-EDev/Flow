package io.github.aedev.flow.player.error

import java.net.URLDecoder

/**
 * Why GVS refused a `googlevideo` stream URL, as far as the URL itself can tell.
 *
 * An HTTP 403 is not one condition. An expired URL and an unattested one fail identically at the
 * socket but need opposite recoveries: the first wants the same client to mint a fresh URL, the
 * second wants a *different* client or a PO Token, because re-minting under the same client
 * reproduces the refusal exactly.
 */
enum class StreamDenialKind {
    /** The `expire` deadline has passed. Re-extracting with the same client is the fix. */
    URL_EXPIRED,

    /** Deadline still valid and no `pot` on the URL — GVS enforced attestation on an unattested client. */
    ATTESTATION_GATED,

    /** Deadline still valid and a `pot` was present, so the token itself was refused. */
    TOKEN_REJECTED,

    /** No readable `expire` — not a GVS URL, or the query could not be parsed. */
    UNKNOWN,
}

/**
 * Reads the verdict out of a googlevideo URL's own query string.
 *
 * Deliberately free of `android.net.Uri` so the rules can be exercised in a plain unit test: `Uri`
 * is stubbed to null in unit tests, which would silently classify everything as [StreamDenialKind.UNKNOWN].
 */
object StreamDenialClassifier {
    /**
     * A URL within this many seconds of its deadline is treated as expired. Covers device clock
     * skew and the gap between the failing request and this classification.
     */
    private const val EXPIRY_GRACE_SECONDS = 30L

    fun classify(
        url: String?,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): StreamDenialKind {
        val expire = queryParam(url, "expire")?.toLongOrNull() ?: return StreamDenialKind.UNKNOWN
        if (expire - nowSeconds <= EXPIRY_GRACE_SECONDS) return StreamDenialKind.URL_EXPIRED
        return if (queryParam(url, "pot").isNullOrEmpty()) {
            StreamDenialKind.ATTESTATION_GATED
        } else {
            StreamDenialKind.TOKEN_REJECTED
        }
    }

    /** Seconds left before the URL's deadline; negative once passed, null when there is no deadline. */
    fun expiresInSeconds(
        url: String?,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Long? = queryParam(url, "expire")?.toLongOrNull()?.minus(nowSeconds)

    /** The `c=` client that minted the URL, e.g. `VISIONOS` or `MWEB`. */
    fun clientOf(url: String?): String? = queryParam(url, "c")?.takeIf { it.isNotBlank() }?.uppercase()

    fun itagOf(url: String?): String? = queryParam(url, "itag")?.takeIf { it.isNotBlank() }

    fun hasPoToken(url: String?): Boolean = !queryParam(url, "pot").isNullOrEmpty()

    /** One log-ready fragment describing the deadline, matching what the data source already prints. */
    fun describeExpiry(
        url: String?,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): String {
        val remaining = expiresInSeconds(url, nowSeconds) ?: return "expire=absent"
        return if (remaining < 0) "expire=PASSED ${-remaining}s ago" else "expire=valid ${remaining}s left"
    }

    fun queryParam(
        url: String?,
        name: String,
    ): String? {
        if (url.isNullOrEmpty()) return null
        val queryStart = url.indexOf('?')
        if (queryStart < 0 || queryStart == url.length - 1) return null
        val query = url.substring(queryStart + 1).substringBefore('#')
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val separator = pair.indexOf('=')
            val key = if (separator < 0) pair else pair.substring(0, separator)
            if (key != name) continue
            val raw = if (separator < 0) "" else pair.substring(separator + 1)
            return decode(raw)
        }
        return null
    }

    private fun decode(raw: String): String =
        try {
            URLDecoder.decode(raw, Charsets.UTF_8.name())
        } catch (e: IllegalArgumentException) {
            raw
        }
}
