package io.github.aedev.flow.utils.potoken

/**
 * When the shared BotGuard session needs re-attesting, and whether a minted token can be trusted.
 *
 * Kept pure and free of Android types so the rules can be exercised without standing up a WebView —
 * the surrounding machinery is main-thread bound and effectively untestable in a unit test.
 */
object PoTokenAttestationPolicy {
    /**
     * BgUtils documents full-trust content-bound PoTokens at 110-128 bytes. Short (~88 byte)
     * tokens are cold attestations that GVS accepts at load time and then rejects partway into
     * playback, which surfaces as the mid-playback 403 rather than a clean failure up front.
     */
    const val MIN_TRUSTED_POT_BYTES = 100

    /** Decoded byte length of a base64 token, without allocating the decoded array. */
    fun tokenByteLength(base64Token: String): Int = base64Token.trimEnd('=').length * 3 / 4

    fun isLowTrust(base64Token: String): Boolean = tokenByteLength(base64Token) < MIN_TRUSTED_POT_BYTES

    /**
     * Whether the cached session must be re-attested before it can be used.
     *
     * [lastTokenWasLowTrust] deliberately forces a redo: a cold token is never pinned, because
     * keeping one trades a clean failure now for a 403 several minutes into playback.
     */
    fun shouldReattest(
        forceRecreate: Boolean,
        hasSession: Boolean,
        isExpired: Boolean,
        sessionIdChanged: Boolean,
        lastTokenWasLowTrust: Boolean,
    ): Boolean = forceRecreate || !hasSession || isExpired || sessionIdChanged || lastTokenWasLowTrust
}
