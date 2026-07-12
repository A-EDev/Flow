package io.github.aedev.flow.player.sabr

/**
 * Decides when the native SABR path should be engaged and preferred over direct-URL playback.
 *
 * The guiding rule is guarded/additive: SABR is only reached for as an *upgrade* over what
 * direct/NewPipe extraction already produced, and preferred for playback only when it actually
 * delivers a higher resolution. A video that plays today via direct URLs can therefore never
 * regress to a SABR session that might be slower or less reliable.
 */
object SabrRoutingPolicy {

    /**
     * A direct ladder capped below this height is treated as "quality-incomplete" — YouTube is
     * likely serving the higher rungs as SABR-only, so a WEB+PoToken+SABR upgrade is worth the
     * extra round-trip. Direct ladders at or above this height are accepted as-is (no added
     * latency), keeping the common fast path untouched.
     */
    const val QUALITY_UPGRADE_FLOOR = 720

    /** True when the best direct video height is low enough to justify attempting a SABR upgrade. */
    fun shouldAttemptSabrUpgrade(directMaxHeight: Int): Boolean =
        directMaxHeight < QUALITY_UPGRADE_FLOOR

    /**
     * Whether to route playback through SABR. Prefer it when a caller forces escalation (e.g. a
     * 403/expiry reload where direct URLs just failed) or when the resolved SABR ladder is
     * strictly higher-resolution than the best playable direct stream.
     */
    fun shouldPreferSabr(forceEscalation: Boolean, sabrHeight: Int, directMaxHeight: Int): Boolean =
        forceEscalation || (sabrHeight > 0 && sabrHeight > directMaxHeight)
}
