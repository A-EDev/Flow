package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.response.ReelWatchSequenceResponse

data class ShortsPage(
    val items: List<ShortsItem>,
    val continuation: String?
)

data class ShortsItem(
    val id: String,
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val channelId: String?,
    val viewCountText: String?,
    val params: String?,
    val playerParams: String?,
    val sequenceParams: String?
)

fun ReelWatchSequenceResponse.toShortsPage(): ShortsPage {
    val items = entries?.mapNotNull { entry ->
        val endpoint = entry.command?.reelWatchEndpoint ?: return@mapNotNull null
        val overlay = endpoint.overlay?.reelPlayerOverlayRenderer
        val metadata = overlay?.reelMetadata?.reelMetadataRenderer
        
        ShortsItem(
            id = endpoint.videoId ?: return@mapNotNull null,
            title = overlay?.reelTitleText?.text ?: "Short",
            thumbnail = "", // Thumbnails are usually fetched via other means or constructed strings, or sometimes not in this response overlay
            channelName = metadata?.channelTitle?.text ?: "Unknown",
            channelId = null, // Might need to extract from runs command
            viewCountText = metadata?.viewCountText?.text,
            params = endpoint.params,
            playerParams = endpoint.playerParams,
            sequenceParams = endpoint.sequenceParams
        )
    } ?: emptyList()
    
    // Extract continuation if needed (often encoded in the response differently)
    // For now we might just reuse sequenceParams from the last item or specific continuation
    return ShortsPage(items, null) // Needs proper continuation logic
}
