package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.Serializable

@Serializable
data class WatchMetadataResponse(
    val contents: Contents? = null,
) {
    @Serializable
    data class Contents(
        val twoColumnWatchNextResults: TwoColumn? = null,
    )

    @Serializable
    data class TwoColumn(
        val results: ResultsWrap? = null,
        val secondaryResults: SecondaryWrap? = null,
    )

    @Serializable
    data class ResultsWrap(
        val results: ResultsInner? = null,
    )

    @Serializable
    data class ResultsInner(
        val contents: List<ResultContent> = emptyList(),
    )

    @Serializable
    data class ResultContent(
        val videoPrimaryInfoRenderer: PrimaryInfo? = null,
        val videoSecondaryInfoRenderer: SecondaryInfo? = null,
    )

    @Serializable
    data class PrimaryInfo(
        val title: Runs? = null,
        val viewCount: ViewCount? = null,
        val dateText: SimpleText? = null,
        val relativeDateText: SimpleText? = null,
        val videoActions: VideoActions? = null,
    ) {
        @Serializable
        data class ViewCount(
            val videoViewCountRenderer: Inner? = null,
        ) {
            @Serializable
            data class Inner(
                val viewCount: SimpleText? = null,
            )
        }

        /**
         * The like button, which carries the count as text rather than a number.
         *
         * Three forms are served for the three states the button can be in; they differ by one or
         * two because the counter moves between requests, and the indifferent one is what a viewer
         * who has not voted sees.
         */
        @Serializable
        data class VideoActions(
            val menuRenderer: MenuRenderer? = null,
        ) {
            @Serializable
            data class MenuRenderer(
                val topLevelButtons: List<TopLevelButton> = emptyList(),
            )

            @Serializable
            data class TopLevelButton(
                val segmentedLikeDislikeButtonViewModel: SegmentedLikeDislike? = null,
            )

            @Serializable
            data class SegmentedLikeDislike(
                val likeCountEntity: LikeCountEntity? = null,
            )

            @Serializable
            data class LikeCountEntity(
                val expandedLikeCountIfIndifferent: SecondaryInfo.TextContent? = null,
                val likeCountIfIndifferent: SecondaryInfo.TextContent? = null,
            )
        }
    }

    @Serializable
    data class SecondaryInfo(
        val owner: Owner? = null,
        val attributedDescription: TextContent? = null,
    ) {
        @Serializable
        data class Owner(
            val videoOwnerRenderer: OwnerRenderer? = null,
        )

        @Serializable
        data class OwnerRenderer(
            val thumbnail: ThumbList? = null,
            val subscriberCountText: SimpleText? = null,
            val title: Runs? = null,
            val navigationEndpoint: NavEndpoint? = null,
        )

        @Serializable
        data class TextContent(
            val content: String? = null,
        )
    }

    @Serializable
    data class SecondaryWrap(
        val secondaryResults: SecondaryInner? = null,
    )

    @Serializable
    data class SecondaryInner(
        val results: List<SecondaryItem> = emptyList(),
    )

    @Serializable
    data class SecondaryItem(
        val compactVideoRenderer: CompactVideo? = null,
        val compactAutoplayRenderer: CompactAutoplay? = null,
        val itemSectionRenderer: ItemSection? = null,
        val lockupViewModel: LockupViewModel? = null,
    ) {
        fun videos(): List<CompactVideo> =
            buildList {
                compactVideoRenderer?.let(::add)
                compactAutoplayRenderer?.contents?.flatMapTo(this) { it.videos() }
                itemSectionRenderer?.contents?.flatMapTo(this) { it.videos() }
                lockupViewModel?.toCompactVideo()?.let(::add)
            }
    }

    @Serializable
    data class CompactAutoplay(
        val contents: List<SecondaryItem> = emptyList(),
    )

    @Serializable
    data class ItemSection(
        val contents: List<SecondaryItem> = emptyList(),
    )

    @Serializable
    data class CompactVideo(
        val videoId: String? = null,
        val title: SimpleText? = null,
        val longBylineText: Runs? = null,
        val thumbnail: ThumbList? = null,
        val viewCountText: SimpleText? = null,
        val lengthText: SimpleText? = null,
        val publishedTimeText: SimpleText? = null,
        val isLive: Boolean = false,
        val resolvedChannelId: String? = null,
        val channelHandle: String? = null,
        val channelAvatarUrl: String? = null,
    ) {
        fun channelId(): String? =
            resolvedChannelId
                ?: longBylineText?.firstBrowseId()?.takeIf { it.startsWith("UC") }
    }

    @Serializable
    data class LockupViewModel(
        val contentId: String? = null,
        val contentType: String? = null,
        val contentImage: LockupContentImage? = null,
        val metadata: LockupMetadataWrap? = null,
    ) {
        fun toCompactVideo(): CompactVideo? {
            if (contentId.isNullOrBlank()) return null
            if (contentType != null && contentType != "LOCKUP_CONTENT_TYPE_VIDEO") return null

            val metadataModel = metadata?.lockupMetadataViewModel
            val metadataParts =
                metadataModel
                    ?.metadata
                    ?.contentMetadataViewModel
                    ?.metadataRows
                    ?.flatMap { it.metadataParts }
                    .orEmpty()

            fun MetadataPart.content(): String? = text?.content?.takeIf { it.isNotBlank() }
            val metadataTexts = metadataParts.mapNotNull { it.content() }
            val live =
                contentImage?.thumbnailViewModel?.hasLiveBadge() == true ||
                    metadataTexts.any { it.contains("watching", ignoreCase = true) || it.contains("viewer", ignoreCase = true) }

            // The view-count part is identified by its leading icon, not by its wording: the text is
            // a bare "342K" with no "views" in it, and every other row is localised.
            val viewsPart = metadataParts.firstOrNull { it.isViewCount }
            val views = viewsPart?.content() ?: metadataTexts.firstOrNull { it.looksLikeViewCount() }
            val remaining = metadataTexts.filter { it != views }
            val published = remaining.firstOrNull { it.looksLikeDateOrDuration() && !it.contains(":") }
            val byline =
                remaining.firstOrNull { text ->
                    text != published &&
                        !text.looksLikeViewCount() &&
                        !text.looksLikeDateOrDuration() &&
                        !text.contains("recommended", ignoreCase = true)
                }
            val avatar = metadataModel?.image?.decoratedAvatarViewModel

            return CompactVideo(
                videoId = contentId,
                title = SimpleText(simpleText = metadataModel?.title?.content),
                longBylineText = Runs(simpleText = byline),
                thumbnail =
                    ThumbList(
                        sources =
                            contentImage
                                ?.thumbnailViewModel
                                ?.image
                                ?.sources
                                .orEmpty(),
                    ),
                viewCountText = SimpleText(simpleText = views),
                lengthText = SimpleText(simpleText = contentImage?.thumbnailViewModel?.durationText()),
                publishedTimeText = SimpleText(simpleText = published),
                isLive = live,
                resolvedChannelId = avatar?.channelId(),
                channelHandle = avatar?.channelHandle(),
                channelAvatarUrl = avatar?.avatarUrl(),
            )
        }

        private fun String.looksLikeViewCount(): Boolean {
            val lower = lowercase()
            return lower.contains("view") ||
                lower.contains("watching") ||
                lower.contains("viewer") ||
                lower.contains("no views")
        }

        private fun String.looksLikeDateOrDuration(): Boolean {
            val lower = lowercase()
            return contains(":") ||
                lower.contains("ago") ||
                lower.contains("streamed") ||
                lower.contains("premiered") ||
                lower.contains("scheduled")
        }
    }

    @Serializable
    data class LockupContentImage(
        val thumbnailViewModel: ThumbnailViewModel? = null,
    )

    @Serializable
    data class ThumbnailViewModel(
        val image: ThumbList? = null,
        val overlays: List<ThumbnailOverlay> = emptyList(),
    ) {
        fun hasLiveBadge(): Boolean =
            overlays.any { overlay ->
                overlay.thumbnailOverlayBadgeViewModel
                    ?.thumbnailBadges
                    .orEmpty()
                    .any { it.thumbnailBadgeViewModel?.isLive() == true } ||
                    overlay.thumbnailBottomOverlayViewModel
                        ?.badges
                        .orEmpty()
                        .any { it.thumbnailBadgeViewModel?.isLive() == true }
            }

        fun durationText(): String? =
            overlays.firstNotNullOfOrNull { overlay ->
                overlay.thumbnailOverlayBadgeViewModel
                    ?.thumbnailBadges
                    .orEmpty()
                    .firstNotNullOfOrNull { it.thumbnailBadgeViewModel?.text?.takeIf { text -> text.contains(":") } }
                    ?: overlay.thumbnailBottomOverlayViewModel
                        ?.badges
                        .orEmpty()
                        .firstNotNullOfOrNull { it.thumbnailBadgeViewModel?.text?.takeIf { text -> text.contains(":") } }
            }
    }

    @Serializable
    data class ThumbnailOverlay(
        val thumbnailOverlayBadgeViewModel: ThumbnailOverlayBadgeViewModel? = null,
        val thumbnailBottomOverlayViewModel: ThumbnailBottomOverlayViewModel? = null,
    )

    @Serializable
    data class ThumbnailOverlayBadgeViewModel(
        val thumbnailBadges: List<ThumbnailBadge> = emptyList(),
    )

    @Serializable
    data class ThumbnailBottomOverlayViewModel(
        val badges: List<ThumbnailBadge> = emptyList(),
    )

    @Serializable
    data class ThumbnailBadge(
        val thumbnailBadgeViewModel: ThumbnailBadgeViewModel? = null,
    )

    @Serializable
    data class ThumbnailBadgeViewModel(
        val text: String? = null,
        val animatedText: String? = null,
        val badgeStyle: String? = null,
    ) {
        fun isLive(): Boolean =
            badgeStyle?.contains("LIVE", ignoreCase = true) == true ||
                text?.contains("live", ignoreCase = true) == true ||
                animatedText?.contains("live", ignoreCase = true) == true
    }

    @Serializable
    data class LockupMetadataWrap(
        val lockupMetadataViewModel: LockupMetadataViewModel? = null,
    )

    @Serializable
    data class LockupMetadataViewModel(
        val title: LockupText? = null,
        val metadata: LockupContentMetadataWrap? = null,
        val image: LockupMetadataImage? = null,
    )

    /**
     * The channel avatar beside a lockup's title. It is also the only place the card carries its
     * channel id — the byline is plain text with no endpoint of its own.
     */
    @Serializable
    data class LockupMetadataImage(
        val decoratedAvatarViewModel: DecoratedAvatar? = null,
    )

    @Serializable
    data class DecoratedAvatar(
        val avatar: AvatarWrap? = null,
        val rendererContext: LockupRendererContext? = null,
    ) {
        private fun browseEndpoint(): NavEndpoint.BrowseEndpoint? =
            rendererContext
                ?.commandContext
                ?.onTap
                ?.innertubeCommand
                ?.browseEndpoint

        fun channelId(): String? = browseEndpoint()?.browseId?.takeIf { it.startsWith("UC") }

        fun channelHandle(): String? = browseEndpoint()?.canonicalBaseUrl?.takeIf { it.startsWith("/@") }

        fun avatarUrl(): String? = avatar?.avatarViewModel?.image?.bestUrl()
    }

    @Serializable
    data class AvatarWrap(
        val avatarViewModel: AvatarViewModel? = null,
    )

    @Serializable
    data class AvatarViewModel(
        val image: ThumbList? = null,
    )

    @Serializable
    data class LockupRendererContext(
        val commandContext: CommandContext? = null,
    )

    @Serializable
    data class CommandContext(
        val onTap: OnTap? = null,
    )

    @Serializable
    data class OnTap(
        val innertubeCommand: InnertubeCommand? = null,
    )

    @Serializable
    data class InnertubeCommand(
        val browseEndpoint: NavEndpoint.BrowseEndpoint? = null,
    )

    @Serializable
    data class LockupContentMetadataWrap(
        val contentMetadataViewModel: ContentMetadataViewModel? = null,
    )

    @Serializable
    data class ContentMetadataViewModel(
        val metadataRows: List<MetadataRow> = emptyList(),
    )

    @Serializable
    data class MetadataRow(
        val metadataParts: List<MetadataPart> = emptyList(),
    )

    @Serializable
    data class MetadataPart(
        val text: LockupText? = null,
        val accessibilityLabel: String? = null,
        val leadingIcon: LeadingIcon? = null,
    ) {
        val isViewCount: Boolean get() = leadingIcon?.name == VIEW_COUNT_ICON
    }

    @Serializable
    data class LeadingIcon(
        val name: String? = null,
    )

    @Serializable
    data class LockupText(
        val content: String? = null,
    )

    @Serializable
    data class Runs(
        val runs: List<Run> = emptyList(),
        val simpleText: String? = null,
    ) {
        @Serializable
        data class Run(
            val text: String? = null,
            val navigationEndpoint: NavEndpoint? = null,
        )

        fun text(): String? = simpleText ?: runs.joinToString("") { it.text.orEmpty() }.takeIf { it.isNotEmpty() }

        fun firstBrowseId(): String? =
            runs.firstNotNullOfOrNull {
                it.navigationEndpoint
                    ?.browseEndpoint
                    ?.browseId
                    ?.takeIf(String::isNotBlank)
            }
    }

    @Serializable
    data class SimpleText(
        val simpleText: String? = null,
        val runs: List<Runs.Run> = emptyList(),
    ) {
        fun text(): String? = simpleText ?: runs.joinToString("") { it.text.orEmpty() }.takeIf { it.isNotEmpty() }
    }

    @Serializable
    data class ThumbList(
        val thumbnails: List<Thumb> = emptyList(),
        val sources: List<Thumb> = emptyList(),
    ) {
        @Serializable
        data class Thumb(
            val url: String? = null,
            val width: Int? = null,
            val height: Int? = null,
        )

        fun bestUrl(): String? = (thumbnails + sources).maxByOrNull { it.height ?: 0 }?.url
    }

    @Serializable
    data class NavEndpoint(
        val browseEndpoint: BrowseEndpoint? = null,
    ) {
        @Serializable
        data class BrowseEndpoint(
            val browseId: String? = null,
            val canonicalBaseUrl: String? = null,
        )
    }

    private fun primary() =
        contents
            ?.twoColumnWatchNextResults
            ?.results
            ?.results
            ?.contents
            ?.firstOrNull { it.videoPrimaryInfoRenderer != null }
            ?.videoPrimaryInfoRenderer

    private fun secondary() =
        contents
            ?.twoColumnWatchNextResults
            ?.results
            ?.results
            ?.contents
            ?.firstOrNull { it.videoSecondaryInfoRenderer != null }
            ?.videoSecondaryInfoRenderer

    fun title(): String? = primary()?.title?.text()

    fun viewCountText(): String? =
        primary()
            ?.viewCount
            ?.videoViewCountRenderer
            ?.viewCount
            ?.text()

    fun uploadDate(): String? = primary()?.dateText?.text()

    /**
     * "4 hours ago", beside the date-only [uploadDate].
     *
     * The absolute form carries no time of day, so it is the only one that can place an upload
     * within the day it happened.
     */
    fun relativeUploadDate(): String? = primary()?.relativeDateText?.text()?.takeIf { it.isNotBlank() }

    /** The exact like count, or the abbreviated one when the expanded form is absent. */
    fun likeCountText(): String? =
        primary()
            ?.videoActions
            ?.menuRenderer
            ?.topLevelButtons
            ?.firstNotNullOfOrNull { it.segmentedLikeDislikeButtonViewModel?.likeCountEntity }
            ?.let { it.expandedLikeCountIfIndifferent?.content ?: it.likeCountIfIndifferent?.content }
            ?.takeIf { it.isNotBlank() }

    fun description(): String? = secondary()?.attributedDescription?.content

    fun channelName(): String? =
        secondary()
            ?.owner
            ?.videoOwnerRenderer
            ?.title
            ?.text()

    fun channelId(): String? =
        secondary()
            ?.owner
            ?.videoOwnerRenderer
            ?.navigationEndpoint
            ?.browseEndpoint
            ?.browseId

    fun channelAvatarUrl(): String? =
        secondary()
            ?.owner
            ?.videoOwnerRenderer
            ?.thumbnail
            ?.bestUrl()

    fun subscriberCountText(): String? =
        secondary()
            ?.owner
            ?.videoOwnerRenderer
            ?.subscriberCountText
            ?.text()

    fun relatedVideos(): List<CompactVideo> =
        contents
            ?.twoColumnWatchNextResults
            ?.secondaryResults
            ?.secondaryResults
            ?.results
            ?.flatMap { it.videos() }
            ?.filter { !it.videoId.isNullOrBlank() }
            ?.distinctBy { it.videoId }
            .orEmpty()

    fun relatedResultCount(): Int =
        contents
            ?.twoColumnWatchNextResults
            ?.secondaryResults
            ?.secondaryResults
            ?.results
            ?.size ?: 0

    /**
     * Not private. A private companion makes the generated `serializer()` private with it, and
     * `decodeFromJsonElement<WatchMetadataResponse>` inlines that access at its call site: it
     * compiles, then throws IllegalAccessError at runtime. The caller swallowed that into a null,
     * so every cached watch response quietly went back to the network.
     */
    companion object {
        private const val VIEW_COUNT_ICON = "PLAY_ARROW_OUTLINED"
    }
}
