package io.github.aedev.flow.utils.potoken

/**
 * When the shared BotGuard session needs re-attesting, and whether a minted token can be trusted.
 *
 * Kept pure and free of Android types so the rules can be exercised without standing up a WebView —
 * the surrounding machinery is main-thread bound and effectively untestable in a unit test.
 */
object PoTokenAttestationPolicy {
    /**
     * BgUtils documents a healthy content-bound PoToken as 110-128 **characters** of base64url.
     *
     * The unit matters: this used to decode the token to bytes first and compare 110-128 against
     * that, which no real token can reach — measured over 88 mints on two visitor identities, every
     * token YouTube handed back was 116 or 120 characters (87 or 90 decoded bytes), and playback on
     * them was healthy. So every token was classed cold, [shouldReattest] never reused a session,
     * and the caller's retry loop ran a full BotGuard challenge three times for every single mint.
     */
    const val MIN_TRUSTED_POT_LENGTH = 110

    /** Length of a base64 token in characters, ignoring padding. */
    fun tokenLength(base64Token: String): Int = base64Token.trimEnd('=').length

    fun isLowTrust(base64Token: String): Boolean = tokenLength(base64Token) < MIN_TRUSTED_POT_LENGTH

    /**
     * Whether the cached session must be re-attested before it can be used.
     *
     * [lastTokenWasLowTrust] deliberately forces a redo: a genuinely cold token is never pinned,
     * because keeping one trades a clean failure now for a 403 several minutes into playback.
     */
    fun shouldReattest(
        forceRecreate: Boolean,
        hasSession: Boolean,
        isExpired: Boolean,
        sessionIdChanged: Boolean,
        lastTokenWasLowTrust: Boolean,
    ): Boolean = forceRecreate || !hasSession || isExpired || sessionIdChanged || lastTokenWasLowTrust
}
