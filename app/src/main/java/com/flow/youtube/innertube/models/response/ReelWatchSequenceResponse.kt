package com.flow.youtube.innertube.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ReelWatchSequenceResponse(
    val entries: List<ReelEntry>?,
    val continuationEndpoint: JsonElement?,
    val continuation: String? // Direct continuation field sometimes present
)

@Serializable
data class ReelEntry(
    val command: ReelCommand?
)

@Serializable
data class ReelCommand(
    val reelWatchEndpoint: ReelWatchEndpoint?
)

@Serializable
data class ReelWatchEndpoint(
    val videoId: String?,
    val playerParams: String?,
    val params: String?,
    val sequenceParams: String?,
    val overlay: ReelOverlay?
)

@Serializable
data class ReelOverlay(
    val reelPlayerOverlayRenderer: ReelPlayerOverlayRenderer?
)

@Serializable
data class ReelPlayerOverlayRenderer(
    val reelTitleText: ReelText?,
    val reelMetadata: ReelMetadata?
)

@Serializable
data class ReelMetadata(
    val reelMetadataRenderer: ReelMetadataRenderer?
)

@Serializable
data class ReelMetadataRenderer(
    val channelTitle: ReelText?,
    val viewCountText: ReelText?
)

@Serializable
data class ReelText(
    val simpleText: String? = null,
    val runs: List<ReelRun>? = null
) {
    val text: String
        get() = simpleText ?: runs?.joinToString("") { it.text ?: "" } ?: ""
}

@Serializable
data class ReelRun(
    val text: String?
)
