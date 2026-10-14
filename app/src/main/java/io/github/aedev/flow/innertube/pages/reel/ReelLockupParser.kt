package io.github.aedev.flow.innertube.pages.reel

import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.parseYouTubeViewCount
import io.github.aedev.flow.innertube.pages.renderer.largestImageUrl
import io.github.aedev.flow.innertube.pages.renderer.webCommandUrl
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One reel as a shelf, grid or search result lists it. The lockup never carries its owner or a
 * duration; those come from the page around it.
 */
data class ReelLockup(
    val id: String,
    val title: String,
    val viewCount: Long,
    val thumbnailUrl: String = "",
    val posterUrl: String? = null,
    val viewsText: String? = null,
    val playerParams: String? = null,
    val params: String? = null,
    val sequenceParams: String? = null,
)

fun reelPosterUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/oar2.jpg"

/** Every reel anywhere in a response, in document order, de-duplicated by id. */
fun JsonObject.toReelLockups(): List<ReelLockup> {
    val reels = mutableListOf<ReelLockup>()
    collectReelLockups(this, reels)
    return reels.distinctBy { it.id }
}

private fun collectReelLockups(
    element: JsonElement,
    out: MutableList<ReelLockup>,
) {
    when (element) {
        is JsonArray -> {
            element.forEach { collectReelLockups(it, out) }
        }

        is JsonObject -> {
            element.parseReelLockup()?.let(out::add)
            element.values.forEach { collectReelLockups(it, out) }
        }

        else -> {
            Unit
        }
    }
}

/** Reads the wrapper object that holds a `shortsLockupViewModel` or `reelItemRenderer` key. */
internal fun JsonObject.parseReelLockup(): ReelLockup? {
    this["reelItemRenderer"].objectOrNull()?.let { renderer ->
        val id = renderer["videoId"].stringOrNull() ?: return null
        val views = renderer["viewCountText"].youtubeText()
        return ReelLockup(
            id = id,
            title = renderer["headline"].youtubeText().orEmpty(),
            viewCount = parseYouTubeViewCount(views),
            viewsText = views,
        )
    }
    val lockup = this["shortsLockupViewModel"].objectOrNull() ?: return null
    val command = lockup["onTap"].objectOrNull()?.get("innertubeCommand").objectOrNull()
    val reelWatch = command?.get("reelWatchEndpoint").objectOrNull()
    val id =
        reelWatch?.get("videoId").stringOrNull()?.takeIf { it.isNotBlank() }
            ?: command
                ?.webCommandUrl()
                ?.substringAfter("/shorts/", "")
                ?.substringBefore("?")
                ?.takeIf { it.isNotBlank() }
            ?: return null
    val overlay = lockup["overlayMetadata"].objectOrNull()
    val viewsText =
        overlay
            ?.get("secondaryText")
            .objectOrNull()
            ?.get("content")
            .stringOrNull()
    return ReelLockup(
        id = id,
        title =
            overlay
                ?.get("primaryText")
                .objectOrNull()
                ?.get("content")
                .stringOrNull()
                .orEmpty(),
        viewCount = parseYouTubeViewCount(viewsText),
        thumbnailUrl =
            lockup["thumbnailViewModel"]
                .objectOrNull()
                ?.get("thumbnailViewModel")
                .objectOrNull()
                ?.get("image")
                .largestImageUrl()
                .orEmpty(),
        posterUrl = reelWatch?.get("thumbnail").largestImageUrl(),
        viewsText = viewsText,
        playerParams = reelWatch?.get("playerParams").stringOrNull(),
        params = reelWatch?.get("params").stringOrNull(),
        sequenceParams = reelWatch?.get("sequenceParams").stringOrNull(),
    )
}
