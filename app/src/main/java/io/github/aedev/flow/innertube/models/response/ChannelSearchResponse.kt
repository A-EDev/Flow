package io.github.aedev.flow.innertube.models.response

import io.github.aedev.flow.innertube.models.Thumbnail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for YouTube.com WEB channel-scoped search using the /browse endpoint
 * (browseId = channelId, params = "EgZzZWFyY2g=").
 */
@Serializable
data class ChannelSearchResponse(
    @SerialName("contents")
    val contents: Contents? = null,
    @SerialName("continuationContents")
    val continuationContents: ContinuationContents? = null,
    @SerialName("onResponseReceivedActions")
    val onResponseReceivedActions: List<OnResponseReceivedAction>? = null,
) {
    // ── Shared item types ────────────────────────────────────────────────────

    @Serializable
    data class VideoRenderer(
        @SerialName("videoId")
        val videoId: String? = null,
        @SerialName("thumbnail")
        val thumbnail: ThumbnailContainer? = null,
        @SerialName("title")
        val title: Runs? = null,
        @SerialName("ownerText")
        val ownerText: Runs? = null,
        @SerialName("publishedTimeText")
        val publishedTimeText: SimpleText? = null,
        @SerialName("viewCountText")
        val viewCountText: SimpleText? = null,
        @SerialName("lengthText")
        val lengthText: SimpleText? = null,
    ) {
        @Serializable
        data class ThumbnailContainer(
            @SerialName("thumbnails")
            val thumbnails: List<Thumbnail>? = null,
        )

        @Serializable
        data class Runs(
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
        data class SimpleText(
            @SerialName("simpleText")
            val simpleText: String? = null,
        )
    }

    @Serializable
    data class Item(
        @SerialName("videoRenderer")
        val videoRenderer: VideoRenderer? = null,
    )

    // ── Section item types (sectionListRenderer path) ───────────────────────

    @Serializable
    data class SectionContent(
        @SerialName("itemSectionRenderer")
        val itemSectionRenderer: ItemSectionRenderer? = null,
        @SerialName("continuationItemRenderer")
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    ) {
        @Serializable
        data class ItemSectionRenderer(
            @SerialName("contents")
            val contents: List<Item>? = null,
        )

        @Serializable
        data class ContinuationItemRenderer(
            @SerialName("continuationEndpoint")
            val continuationEndpoint: ContinuationEndpoint? = null,
        ) {
            @Serializable
            data class ContinuationEndpoint(
                @SerialName("continuationCommand")
                val continuationCommand: ContinuationCommand? = null,
            ) {
                @Serializable
                data class ContinuationCommand(
                    @SerialName("token")
                    val token: String? = null,
                )
            }
        }
    }

    // ── Rich grid item types (richGridRenderer path) ─────────────────────────

    @Serializable
    data class RichItem(
        @SerialName("richItemRenderer")
        val richItemRenderer: RichItemRenderer? = null,
        @SerialName("continuationItemRenderer")
        val continuationItemRenderer: SectionContent.ContinuationItemRenderer? = null,
        @SerialName("itemSectionRenderer")
        val itemSectionRenderer: SectionContent.ItemSectionRenderer? = null,
    ) {
        @Serializable
        data class RichItemRenderer(
            @SerialName("content")
            val content: RichItemContent? = null,
        ) {
            @Serializable
            data class RichItemContent(
                @SerialName("videoRenderer")
                val videoRenderer: VideoRenderer? = null,
            )
        }
    }

    // ── Initial-load structure ───────────────────────────────────────────────

    @Serializable
    data class Contents(
        @SerialName("twoColumnBrowseResultsRenderer")
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    ) {
        @Serializable
        data class TwoColumnBrowseResultsRenderer(
            @SerialName("tabs")
            val tabs: List<Tab>? = null,
        ) {
            @Serializable
            data class Tab(
                @SerialName("tabRenderer")
                val tabRenderer: TabRenderer? = null,
                @SerialName("expandableTabRenderer")
                val expandableTabRenderer: TabRenderer? = null,
            ) {
                @Serializable
                data class TabRenderer(
                    @SerialName("selected")
                    val selected: Boolean? = null,
                    @SerialName("content")
                    val content: Content? = null,
                    @SerialName("endpoint")
                    val endpoint: TabEndpoint? = null,
                ) {
                    @Serializable
                    data class TabEndpoint(
                        @SerialName("commandMetadata")
                        val commandMetadata: CommandMetadata? = null,
                    ) {
                        @Serializable
                        data class CommandMetadata(
                            @SerialName("webCommandMetadata")
                            val webCommandMetadata: WebCommandMetadata? = null,
                        ) {
                            @Serializable
                            data class WebCommandMetadata(
                                @SerialName("url")
                                val url: String? = null,
                            )
                        }
                    }

                    @Serializable
                    data class Content(
                        @SerialName("sectionListRenderer")
                        val sectionListRenderer: SectionListRenderer? = null,
                        @SerialName("richGridRenderer")
                        val richGridRenderer: RichGridRenderer? = null,
                    ) {
                        @Serializable
                        data class SectionListRenderer(
                            @SerialName("contents")
                            val contents: List<SectionContent>? = null,
                        )

                        @Serializable
                        data class RichGridRenderer(
                            @SerialName("contents")
                            val contents: List<RichItem>? = null,
                        )
                    }
                }
            }
        }
    }

    // ── Continuation structure ───────────────────────────────────────────────

    @Serializable
    data class ContinuationContents(
        @SerialName("sectionListContinuation")
        val sectionListContinuation: SectionListContinuation? = null,
        @SerialName("richGridContinuation")
        val richGridContinuation: RichGridContinuation? = null,
    ) {
        @Serializable
        data class SectionListContinuation(
            @SerialName("contents")
            val contents: List<SectionContent>? = null,
            @SerialName("continuations")
            val continuations: List<Continuation>? = null,
        ) {
            @Serializable
            data class Continuation(
                @SerialName("nextContinuationData")
                val nextContinuationData: NextContinuationData? = null,
            ) {
                @Serializable
                data class NextContinuationData(
                    @SerialName("continuation")
                    val continuation: String? = null,
                )
            }
        }

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
