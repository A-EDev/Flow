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
    val channelThumbnailUrl: String?,
    val viewCountText: String?,
    val likeCountText: String?,
    val params: String?,
    val playerParams: String?,
    val sequenceParams: String?
)

fun ReelWatchSequenceResponse.toShortsPage(): ShortsPage {
    val items = entries?.mapNotNull { entry ->
        val endpoint = entry.command?.reelWatchEndpoint ?: return@mapNotNull null
        val overlay = endpoint.overlay?.reelPlayerOverlayRenderer
        val metadata = overlay?.reelMetadata?.reelMetadataRenderer
        val header = overlay?.reelPlayerHeaderSupportedRenderers?.reelPlayerHeaderRenderer
        val videoId = endpoint.videoId ?: return@mapNotNull null
        
        val title = header?.reelTitleOnExpandedStateRenderer?.dynamicTextContent?.text?.takeIf { it.isNotBlank() }
            ?: header?.reelTitleOnExpandedStateRenderer?.simpleTitleText?.text?.takeIf { it.isNotBlank() }
            ?: overlay?.reelTitleText?.text?.takeIf { it.isNotBlank() }
        
        val channelName = header?.channelTitleText?.text?.takeIf { it.isNotBlank() }
            ?: metadata?.channelTitle?.text?.takeIf { it.isNotBlank() }
        
        val channelId = header?.channelNavigationEndpoint?.browseEndpoint?.browseId
            ?: metadata?.channelNavigationEndpoint?.browseEndpoint?.browseId
            ?: endpoint.navigationEndpoint?.browseEndpoint?.browseId
            ?: header?.channelTitleText?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
            ?: metadata?.channelTitle?.runs?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId
        
        val thumbnail = "https://i.ytimg.com/vi/$videoId/oar2.jpg"
        
        val channelThumbnail = header?.channelThumbnail?.thumbnails?.firstOrNull()?.url
            ?: metadata?.channelThumbnail?.thumbnails?.firstOrNull()?.url
        
        val likeCountText = overlay?.likeButton?.toggleButtonRenderer?.defaultText?.text
        
        val viewCountText = overlay?.viewCountText?.text
            ?: metadata?.viewCountText?.text
        
        ShortsItem(
            id = videoId,
            title = title ?: "Short",
            thumbnail = thumbnail,
            channelName = channelName ?: "Unknown",
            channelId = channelId,
            channelThumbnailUrl = channelThumbnail,
            viewCountText = viewCountText,
            likeCountText = likeCountText,
            params = endpoint.params,
            playerParams = endpoint.playerParams,
            sequenceParams = endpoint.sequenceParams
        )
    } ?: emptyList()
    
    val continuation = extractContinuation()
    
    return ShortsPage(items, continuation)
}
