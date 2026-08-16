package io.github.aedev.flow.player.sabr.integration

import io.github.aedev.flow.innertube.models.YouTubeClient

/**
 * The identity a SABR streaming request must report so that it matches the player response that
 * produced the session.
 *
 * `streamer_context.client_name` is the numeric InnerTube client id — the same value sent in the
 * `X-YouTube-Client-Name` header (WEB = 1, MWEB = 2) — so it is read off the [YouTubeClient] rather
 * than kept as a second table that could drift out of step with it.
 */
object SabrClientIdentity {
    const val WEB_CLIENT_NAME_ID = 1
    private const val MWEB_CLIENT_NAME_ID = 2

    fun clientNameId(client: YouTubeClient): Int = client.clientId.toIntOrNull() ?: WEB_CLIENT_NAME_ID

    /**
     * The client a live SABR session belongs to, so that a mid-playback reload re-resolves as the
     * same client that opened it. Resolving an MWEB session as WEB would hand the server a
     * streamer_context that disagrees with the original player response.
     */
    fun sabrClientFor(clientNameId: Int): YouTubeClient =
        when (clientNameId) {
            MWEB_CLIENT_NAME_ID -> YouTubeClient.MWEB
            else -> YouTubeClient.WEB
        }
}
