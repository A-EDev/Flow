package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.RichText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val STRUCTURED_DESCRIPTION_PANEL = "engagement-panel-structured-description"

/** One of the figures the description header prints above the text, as the server worded it. */
data class VideoDescriptionFactoid(
    val value: String,
    val label: String,
    val accessibilityText: String? = null,
)

/**
 * The watch page's own description: the text with its typed spans, and the figures beside it.
 *
 * Everything here is read rather than derived — the view count is the exact one the response
 * carries, not an abbreviation expanded back into a number.
 */
data class VideoDescriptionPage(
    val description: RichText? = null,
    val viewCountText: String? = null,
    val viewCount: Long? = null,
    val publishedDateText: String? = null,
    val relativeDateText: String? = null,
    val factoids: List<VideoDescriptionFactoid> = emptyList(),
) {
    val isEmpty: Boolean
        get() = description == null && viewCount == null && factoids.isEmpty()
}

internal fun JsonElement.toVideoDescriptionPage(ownVideoId: String?): VideoDescriptionPage {
    val results =
        objectOrNull()
            ?.get("contents")
            .objectOrNull()
            ?.get("twoColumnWatchNextResults")
            .objectOrNull()
            ?.get("results")
            .objectOrNull()
            ?.get("results")
            .objectOrNull()
            ?.get("contents")
            .arrayOrNull()
            .orEmpty()
    val primary =
        results.firstNotNullOfOrNull { it.objectOrNull()?.get("videoPrimaryInfoRenderer").objectOrNull() }
    val secondary =
        results.firstNotNullOfOrNull { it.objectOrNull()?.get("videoSecondaryInfoRenderer").objectOrNull() }
    val viewCountRenderer =
        primary
            ?.get("viewCount")
            .objectOrNull()
            ?.get("videoViewCountRenderer")
            .objectOrNull()

    return VideoDescriptionPage(
        description = secondary?.get("attributedDescription").toRichText(ownVideoId) ?: structuredDescription(ownVideoId),
        viewCountText = viewCountRenderer?.get("viewCount").youtubeText(),
        viewCount =
            viewCountRenderer
                ?.get("originalViewCount")
                .stringOrNull()
                ?.toLongOrNull()
                ?: viewCountRenderer
                    ?.get("viewCount")
                    .youtubeText()
                    ?.let(::parseYouTubeViewCount)
                    ?.takeIf { it > 0L },
        publishedDateText = primary?.get("dateText").youtubeText(),
        relativeDateText = primary?.get("relativeDateText").youtubeText(),
        factoids = structuredDescriptionItems().flatMap { it.toFactoids() },
    )
}

/** The body the structured-description panel carries, for a response whose secondary info has none. */
private fun JsonElement.structuredDescription(ownVideoId: String?): RichText? =
    structuredDescriptionItems()
        .firstNotNullOfOrNull { item ->
            item["expandableVideoDescriptionBodyRenderer"]
                .objectOrNull()
                ?.get("attributedDescriptionBodyText")
                .toRichText(ownVideoId)
        }

private fun JsonElement.structuredDescriptionItems(): List<JsonObject> =
    objectOrNull()
        ?.get("engagementPanels")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { panel ->
            val section = panel.objectOrNull()?.get("engagementPanelSectionListRenderer").objectOrNull()
            section?.takeIf { it["panelIdentifier"].stringOrNull() == STRUCTURED_DESCRIPTION_PANEL }
        }.flatMap { section ->
            section["content"]
                .objectOrNull()
                ?.get("structuredDescriptionContentRenderer")
                .objectOrNull()
                ?.get("items")
                .arrayOrNull()
                .orEmpty()
                .mapNotNull { it.objectOrNull() }
        }

private fun JsonObject.toFactoids(): List<VideoDescriptionFactoid> =
    this["videoDescriptionHeaderRenderer"]
        .objectOrNull()
        ?.get("factoid")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { entry ->
            val wrapper = entry.objectOrNull() ?: return@mapNotNull null
            val renderer =
                wrapper["factoidRenderer"].objectOrNull()
                    ?: wrapper.values
                        .firstNotNullOfOrNull { nested ->
                            nested
                                .objectOrNull()
                                ?.get("factoid")
                                .objectOrNull()
                                ?.get("factoidRenderer")
                                .objectOrNull()
                        }
                    ?: return@mapNotNull null
            val value = renderer["value"].youtubeText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VideoDescriptionFactoid(
                value = value,
                label = renderer["label"].youtubeText().orEmpty(),
                accessibilityText = renderer["accessibilityText"].stringOrNull(),
            )
        }
