package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.parseYouTubeViewCount
import io.github.aedev.flow.innertube.pages.renderer.largestImageUrl
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.net.URLDecoder
import java.util.Base64

/** What `reel_item_watch` says about one reel. Every field is optional because the clients differ. */
data class ReelOverlay(
    val title: String? = null,
    val channelId: String? = null,
    val channelName: String? = null,
    val channelAvatarUrl: String? = null,
    val likeCount: Long? = null,
    val likeCountText: String? = null,
    val commentCount: Long? = null,
    val commentCountText: String? = null,
    val relativeTimestamp: String? = null,
    val soundTitle: String? = null,
    val soundThumbnailUrl: String? = null,
    val isSubscribed: Boolean? = null,
    val companionVideoId: String? = null,
)

/**
 * Null when the response holds no overlay this parser can read. ANDROID and IOS answer with an
 * Elements template, which is opaque; WEB and MWEB send view models, VISIONOS and ANDROID_VR the
 * older renderers. Both readable shapes land in the same [ReelOverlay].
 */
fun JsonObject.toReelOverlay(): ReelOverlay? {
    val overlay = at("overlay", "reelPlayerOverlayRenderer").objectOrNull() ?: return null
    val header = overlay.at("reelPlayerHeaderSupportedRenderers", "reelPlayerHeaderRenderer").objectOrNull()
    val legacy = overlay.legacyOverlay(header)
    val viewModel = overlay.viewModelOverlay()
    if (legacy == null && viewModel == null) return null
    val merged = (viewModel ?: ReelOverlay()).let { vm -> legacy?.let { vm.filledFrom(it) } ?: vm }
    return merged.copy(
        relativeTimestamp = header?.get("timestampText").youtubeText() ?: merged.relativeTimestamp,
        isSubscribed = merged.isSubscribed ?: subscriptionState(),
    )
}

private fun ReelOverlay.filledFrom(other: ReelOverlay): ReelOverlay =
    ReelOverlay(
        title = title ?: other.title,
        channelId = channelId ?: other.channelId,
        channelName = channelName ?: other.channelName,
        channelAvatarUrl = channelAvatarUrl ?: other.channelAvatarUrl,
        likeCount = likeCount ?: other.likeCount,
        likeCountText = likeCountText ?: other.likeCountText,
        commentCount = commentCount ?: other.commentCount,
        commentCountText = commentCountText ?: other.commentCountText,
        relativeTimestamp = relativeTimestamp ?: other.relativeTimestamp,
        soundTitle = soundTitle ?: other.soundTitle,
        soundThumbnailUrl = soundThumbnailUrl ?: other.soundThumbnailUrl,
        isSubscribed = isSubscribed ?: other.isSubscribed,
        companionVideoId = companionVideoId ?: other.companionVideoId,
    )

private fun JsonObject.legacyOverlay(header: JsonObject?): ReelOverlay? {
    val like = at("likeButton", "likeButtonRenderer").objectOrNull()
    val subscribe = at("subscribeButtonRenderer").objectOrNull()
    val comments = at("viewCommentsButton", "buttonRenderer").objectOrNull()
    val pivot = at("pivotButton", "pivotButtonRenderer").objectOrNull()
    if (like == null && subscribe == null && comments == null && header?.get("reelTitleText") == null) return null
    val commentText = comments?.get("text").youtubeText()
    return ReelOverlay(
        title = header?.get("reelTitleText").youtubeText(),
        channelId =
            header?.at("channelNavigationEndpoint", "browseEndpoint", "browseId").stringOrNull()
                ?: subscribe?.get("channelId").stringOrNull(),
        channelName = header?.get("channelTitleText").youtubeText(),
        channelAvatarUrl = header?.get("channelThumbnail").largestImageUrl(),
        likeCount = like?.get("likeCount").stringOrNull()?.toLongOrNull(),
        likeCountText = like?.get("likeCountText").youtubeText(),
        commentCount = commentText?.let(::parseYouTubeViewCount)?.takeIf { it > 0 },
        commentCountText = commentText,
        soundTitle = pivot?.get("contentDescription").youtubeText(),
        soundThumbnailUrl = pivot?.get("thumbnail").largestImageUrl(),
        isSubscribed = subscribe?.get("subscribed").stringOrNull()?.toBooleanStrictOrNull(),
    )
}

private fun JsonObject.viewModelOverlay(): ReelOverlay? {
    val playerOverlay = at("playerOverlay", "reelPlayerOverlayViewModel").objectOrNull()
    val items =
        (playerOverlay?.get("metapanel") ?: this["metapanel"])
            .at("reelMetapanelViewModel", "metadataItems")
            .arrayOrNull()
            ?.mapNotNull { it.objectOrNull() }
    val buttons =
        (playerOverlay?.get("actionBar") ?: this["buttonBar"])
            .at("reelActionBarViewModel", "buttonViewModels")
            .arrayOrNull()
            ?.mapNotNull { it.objectOrNull() }
    if (items == null && buttons == null) return null

    val channelBar = items?.firstNotNullOfOrNull { it["reelChannelBarViewModel"].objectOrNull() }
    val channelRun =
        channelBar
            ?.at("channelName", "commandRuns")
            .arrayOrNull()
            ?.firstNotNullOfOrNull { it.at("onTap", "innertubeCommand", "browseEndpoint").objectOrNull() }
    val like =
        buttons
            ?.firstNotNullOfOrNull { it["likeButtonViewModel"].objectOrNull() }
            ?.at("toggleButtonViewModel", "toggleButtonViewModel")
            .objectOrNull()
    val commentButton =
        buttons
            ?.mapNotNull { it["buttonViewModel"].objectOrNull() }
            ?.firstOrNull { it["iconName"].stringOrNull() == "SHORTS_COMMENT" }
    val pivot = buttons?.firstNotNullOfOrNull { it["pivotButtonViewModel"].objectOrNull() }
    val likeExact = like?.at("toggledButtonViewModel", "buttonViewModel", "accessibilityText").stringOrNull()
    val likeShort = like?.at("defaultButtonViewModel", "buttonViewModel", "title").stringOrNull()
    val commentText = commentButton?.get("title").stringOrNull()

    return ReelOverlay(
        title = items?.firstNotNullOfOrNull { it.at("shortsVideoTitleViewModel", "text", "content").stringOrNull() },
        channelId =
            channelRun?.get("browseId").stringOrNull()
                ?: channelBar?.get("subscribeStateEntityStoreKey").stringOrNull()?.let(::channelIdFromEntityKey),
        channelName = channelBar?.at("channelName", "content").stringOrNull(),
        channelAvatarUrl =
            channelBar
                ?.at("decoratedAvatarViewModel", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image")
                .largestImageUrl()
                ?: channelBar
                    ?.at("avatarStackViewModel", "avatarStackViewModel", "avatars")
                    .arrayOrNull()
                    ?.firstOrNull()
                    .at("avatarViewModel", "image")
                    .largestImageUrl(),
        likeCount =
            likeExact?.let(::parseYouTubeViewCount)?.takeIf { it > 0 }
                ?: likeShort?.let(::parseYouTubeViewCount)?.takeIf { it > 0 },
        likeCountText = likeShort,
        commentCount = commentText?.let(::parseYouTubeViewCount)?.takeIf { it > 0 },
        commentCountText = commentText,
        soundTitle = pivot?.at("soundAttributionTitle", "content").stringOrNull(),
        soundThumbnailUrl = pivot?.get("thumbnail").largestImageUrl(),
        companionVideoId =
            items
                ?.firstNotNullOfOrNull { it["reelCarouselViewModel"].objectOrNull() }
                ?.get("buttonViewModels")
                .arrayOrNull()
                ?.firstNotNullOfOrNull { button ->
                    button
                        .at(
                            "reelCarouselButtonViewModel",
                            "buttonViewModel",
                            "buttonViewModel",
                            "onTap",
                            "innertubeCommand",
                            "watchEndpoint",
                            "videoId",
                        ).stringOrNull()
                },
    )
}

private fun JsonObject.subscriptionState(): Boolean? =
    at("frameworkUpdates", "entityBatchUpdate", "mutations")
        .arrayOrNull()
        ?.firstNotNullOfOrNull { it.at("payload", "subscriptionStateEntity", "subscribed").stringOrNull() }
        ?.toBooleanStrictOrNull()

/**
 * A collaboration's channel bar opens a dialog instead of a browse endpoint, so the only channel id
 * left on it is inside the subscribe entity key: protobuf field 2 of its base64 payload.
 */
private fun channelIdFromEntityKey(key: String): String? =
    runCatching {
        val decoded = URLDecoder.decode(key, Charsets.UTF_8.name())
        val bytes =
            runCatching { Base64.getDecoder().decode(decoded) }
                .getOrElse { Base64.getUrlDecoder().decode(decoded) }
        CHANNEL_ID.find(String(bytes, Charsets.ISO_8859_1))?.value
    }.getOrNull()

private val CHANNEL_ID = Regex("UC[0-9A-Za-z_-]{22}")

private fun JsonElement?.at(vararg keys: String): JsonElement? = keys.fold(this) { node, key -> node.objectOrNull()?.get(key) }
