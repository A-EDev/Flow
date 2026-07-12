package io.github.aedev.flow.innertube.models.body

import io.github.aedev.flow.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
    val cpn: String? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext? = null,
        val reloadPlaybackContext: ReloadPlaybackContext? = null,
    ) {
        // The WEB player sends a full contentPlaybackContext, not just the signatureTimestamp.
        // The extra fields default to null so they are omitted for the native (non-web) player()
        // path and only populated for the WEB request in InnerTube.playerWeb.
        @Serializable
        data class ContentPlaybackContext(
            val signatureTimestamp: Int,
            val referer: String? = null,
            val vis: Int? = null,
            val splay: Boolean? = null,
            val lactMilliseconds: String? = null,
            val html5Preference: String? = null,
        )

        // Echoes the RELOAD_PLAYER_RESPONSE token from a SABR session so the fresh player
        // response stays linked to the server's enforcement session.
        @Serializable
        data class ReloadPlaybackContext(
            val reloadPlaybackParams: ReloadPlaybackParams
        ) {
            @Serializable
            data class ReloadPlaybackParams(
                val token: String
            )
        }
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String
    )
}
