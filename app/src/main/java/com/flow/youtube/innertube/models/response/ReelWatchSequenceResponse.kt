package com.flow.youtube.innertube.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class ReelWatchSequenceResponse(
    val entries: List<ReelEntry>?,
    val continuationEndpoint: ReelContinuationEndpoint?,
    val continuation: String?
) {
    /**
     * Extract the continuation token for pagination.
     * YouTube encodes this in different locations depending on response format.
     */
    fun extractContinuation(): String? {
        continuation?.let { return it }
        continuationEndpoint?.let { endpoint ->
            endpoint.reelWatchSequenceEndpoint?.sequenceParams?.let { return it }
            endpoint.continuationCommand?.token?.let { return it }
        }
        entries?.lastOrNull()?.command?.reelWatchEndpoint?.sequenceParams?.let { return it }
        return null
    }
}

@Serializable
data class ReelContinuationEndpoint(
    val reelWatchSequenceEndpoint: ReelWatchSequenceContinuation? = null,
    val continuationCommand: ContinuationCommand? = null
)

@Serializable
data class ReelWatchSequenceContinuation(
    val sequenceParams: String? = null
)

@Serializable
data class ContinuationCommand(
    val token: String? = null
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
    val overlay: ReelOverlay?,
    val navigationEndpoint: ReelNavigationEndpoint? = null
)

@Serializable
data class ReelNavigationEndpoint(
    val browseEndpoint: ReelBrowseEndpoint? = null
)

@Serializable
data class ReelBrowseEndpoint(
    val browseId: String? = null
)

@Serializable
data class ReelOverlay(
    val reelPlayerOverlayRenderer: ReelPlayerOverlayRenderer?
)

@Serializable
data class ReelPlayerOverlayRenderer(
    val reelTitleText: ReelText? = null,
    val reelMetadata: ReelMetadata? = null,
    val reelPlayerHeaderSupportedRenderers: ReelPlayerHeaderSupportedRenderers? = null,
    val style: String? = null,
    val likeButton: ReelToggleButton? = null,
    val viewCountText: ReelText? = null
)

// ── Actual YouTube reel API header path ──

@Serializable
data class ReelPlayerHeaderSupportedRenderers(
    val reelPlayerHeaderRenderer: ReelPlayerHeaderRenderer? = null
)

@Serializable
data class ReelPlayerHeaderRenderer(
    val channelTitleText: ReelText? = null,
    val channelNavigationEndpoint: ChannelNavigationEndpoint? = null,
    val channelThumbnail: ReelThumbnail? = null,
    val reelTitleOnExpandedStateRenderer: ReelTitleOnExpandedStateRenderer? = null,
    val timestampText: ReelText? = null
)

@Serializable
data class ReelTitleOnExpandedStateRenderer(
    val dynamicTextContent: ReelText? = null,
    val simpleTitleText: ReelText? = null
)

@Serializable
data class ReelToggleButton(
    val toggleButtonRenderer: ToggleButtonRenderer? = null
)

@Serializable
data class ToggleButtonRenderer(
    val defaultText: ReelText? = null
)

@Serializable
data class ReelMetadata(
    val reelMetadataRenderer: ReelMetadataRenderer?
)

@Serializable
data class ReelMetadataRenderer(
    val channelTitle: ReelText?,
    val viewCountText: ReelText?,
    val channelNavigationEndpoint: ChannelNavigationEndpoint? = null,
    val channelThumbnail: ReelThumbnail? = null
)

@Serializable
data class ChannelNavigationEndpoint(
    val browseEndpoint: ReelBrowseEndpoint? = null
)

@Serializable
data class ReelThumbnail(
    val thumbnails: List<Thumbnail>? = null
)

@Serializable
data class Thumbnail(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
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
    val text: String?,
    val navigationEndpoint: ReelRunNavigationEndpoint? = null
)

@Serializable
data class ReelRunNavigationEndpoint(
    val browseEndpoint: ReelBrowseEndpoint? = null
)
