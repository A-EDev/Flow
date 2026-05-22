package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for YouTube WEB channel videos/live tabs.
 *
 * YouTube currently returns channel grid items as lockupViewModel, while older
 * payloads may still contain videoRenderer.
 */
@Serializable
data class ChannelVideosResponse(
    @SerialName("contents")
    val contents: Contents? = null,
    @SerialName("metadata")
    val metadata: Metadata? = null,
    @SerialName("continuationContents")
    val continuationContents: ContinuationContents? = null,
    @SerialName("onResponseReceivedActions")
    val onResponseReceivedActions: List<OnResponseReceivedAction>? = null,
) {
    @Serializable
    data class Metadata(
        @SerialName("channelMetadataRenderer")
        val channelMetadataRenderer: ChannelMetadataRenderer? = null,
    ) {
        @Serializable
        data class ChannelMetadataRenderer(
            @SerialName("externalId")
            val externalId: String? = null,
            @SerialName("externalChannelId")
            val externalChannelId: String? = null,
            @SerialName("title")
            val title: String? = null,
            @SerialName("avatar")
            val avatar: ThumbnailContainer? = null,
        )
    }

    @Serializable
    data class ThumbnailContainer(
        @SerialName("thumbnails")
        val thumbnails: List<ThumbnailItem>? = null,
    )

    @Serializable
    data class ThumbnailItem(
        @SerialName("url")
        val url: String? = null,
        @SerialName("width")
        val width: Int? = null,
        @SerialName("height")
        val height: Int? = null,
    )

    @Serializable
    data class SimpleText(
        @SerialName("simpleText")
        val simpleText: String? = null,
        @SerialName("runs")
        val runs: List<Run>? = null,
    ) {
        @Serializable
        data class Run(
            @SerialName("text")
            val text: String? = null,
        )
    }

    @Serializable
    data class VideoRenderer(
        @SerialName("videoId")
        val videoId: String? = null,
        @SerialName("thumbnail")
        val thumbnail: ThumbnailContainer? = null,
        @SerialName("title")
        val title: SimpleText? = null,
        @SerialName("publishedTimeText")
        val publishedTimeText: SimpleText? = null,
        @SerialName("viewCountText")
        val viewCountText: SimpleText? = null,
        @SerialName("lengthText")
        val lengthText: SimpleText? = null,
    )

    @Serializable
    data class LockupViewModel(
        @SerialName("contentId")
        val contentId: String? = null,
        @SerialName("metadata")
        val metadata: MetadataContainer? = null,
        @SerialName("contentImage")
        val contentImage: ContentImage? = null,
    ) {
        @Serializable
        data class MetadataContainer(
            @SerialName("lockupMetadataViewModel")
            val lockupMetadataViewModel: LockupMetadataViewModel? = null,
        )

        @Serializable
        data class LockupMetadataViewModel(
            @SerialName("title")
            val title: TextContent? = null,
            @SerialName("metadata")
            val metadata: ContentMetadata? = null,
        )

        @Serializable
        data class TextContent(
            @SerialName("content")
            val content: String? = null,
        )

        @Serializable
        data class ContentMetadata(
            @SerialName("contentMetadataViewModel")
            val contentMetadataViewModel: ContentMetadataViewModel? = null,
        )

        @Serializable
        data class ContentMetadataViewModel(
            @SerialName("metadataRows")
            val metadataRows: List<MetadataRow>? = null,
        )

        @Serializable
        data class MetadataRow(
            @SerialName("metadataParts")
            val metadataParts: List<MetadataPart>? = null,
        )

        @Serializable
        data class MetadataPart(
            @SerialName("text")
            val text: TextContent? = null,
        )

        @Serializable
        data class ContentImage(
            @SerialName("thumbnailViewModel")
            val thumbnailViewModel: ThumbnailViewModel? = null,
        )

        @Serializable
        data class ThumbnailViewModel(
            @SerialName("image")
            val image: Image? = null,
            @SerialName("overlays")
            val overlays: List<Overlay>? = null,
        )

        @Serializable
        data class Image(
            @SerialName("sources")
            val sources: List<ImageSource>? = null,
        )

        @Serializable
        data class ImageSource(
            @SerialName("url")
            val url: String? = null,
            @SerialName("width")
            val width: Int? = null,
            @SerialName("height")
            val height: Int? = null,
        )

        @Serializable
        data class Overlay(
            @SerialName("thumbnailBottomOverlayViewModel")
            val thumbnailBottomOverlayViewModel: ThumbnailBottomOverlayViewModel? = null,
        )

        @Serializable
        data class ThumbnailBottomOverlayViewModel(
            @SerialName("badges")
            val badges: List<Badge>? = null,
        )

        @Serializable
        data class Badge(
            @SerialName("thumbnailBadgeViewModel")
            val thumbnailBadgeViewModel: ThumbnailBadgeViewModel? = null,
        )

        @Serializable
        data class ThumbnailBadgeViewModel(
            @SerialName("text")
            val text: String? = null,
        )
    }

    @Serializable
    data class RichItem(
        @SerialName("richItemRenderer")
        val richItemRenderer: RichItemRenderer? = null,
        @SerialName("continuationItemRenderer")
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    ) {
        @Serializable
        data class RichItemRenderer(
            @SerialName("content")
            val content: RichItemContent? = null,
        )

        @Serializable
        data class RichItemContent(
            @SerialName("lockupViewModel")
            val lockupViewModel: LockupViewModel? = null,
            @SerialName("videoRenderer")
            val videoRenderer: VideoRenderer? = null,
        )
    }

    @Serializable
    data class ContinuationItemRenderer(
        @SerialName("continuationEndpoint")
        val continuationEndpoint: ContinuationEndpoint? = null,
    ) {
        @Serializable
        data class ContinuationEndpoint(
            @SerialName("continuationCommand")
            val continuationCommand: ContinuationCommand? = null,
        )

        @Serializable
        data class ContinuationCommand(
            @SerialName("token")
            val token: String? = null,
        )
    }

    @Serializable
    data class Contents(
        @SerialName("twoColumnBrowseResultsRenderer")
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    ) {
        @Serializable
        data class TwoColumnBrowseResultsRenderer(
            @SerialName("tabs")
            val tabs: List<Tab>? = null,
        )

        @Serializable
        data class Tab(
            @SerialName("tabRenderer")
            val tabRenderer: TabRenderer? = null,
            @SerialName("expandableTabRenderer")
            val expandableTabRenderer: TabRenderer? = null,
        )

        @Serializable
        data class TabRenderer(
            @SerialName("selected")
            val selected: Boolean? = null,
            @SerialName("title")
            val title: String? = null,
            @SerialName("content")
            val content: Content? = null,
        )

        @Serializable
        data class Content(
            @SerialName("richGridRenderer")
            val richGridRenderer: RichGridRenderer? = null,
        )

        @Serializable
        data class RichGridRenderer(
            @SerialName("contents")
            val contents: List<RichItem>? = null,
        )
    }

    @Serializable
    data class ContinuationContents(
        @SerialName("richGridContinuation")
        val richGridContinuation: RichGridContinuation? = null,
    ) {
        @Serializable
        data class RichGridContinuation(
            @SerialName("contents")
            val contents: List<RichItem>? = null,
        )
    }

    @Serializable
    data class OnResponseReceivedAction(
        @SerialName("appendContinuationItemsAction")
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
    ) {
        @Serializable
        data class AppendContinuationItemsAction(
            @SerialName("continuationItems")
            val continuationItems: List<RichItem>? = null,
        )
    }
}
