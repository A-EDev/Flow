package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The channels behind a collaboration, read from the "Collaborators" dialog YouTube inlines under a
 * collaboration's byline, avatar stack and subscribe button. A collaboration's byline links to this
 * dialog instead of to a channel, so it is the only place the card carries any channel id at all.
 *
 * Call it on the node that opens the dialog, never on a whole renderer: a renderer's menu carries
 * unrelated dialogs of the same shape. Empty unless the dialog names at least two channels.
 */
internal fun JsonElement?.collaboratorDialog(): List<VideoCollaborator> =
    firstShowDialogCommand()
        ?.get("panelLoadingStrategy")
        .objectOrNull()
        ?.get("inlineContent")
        .objectOrNull()
        ?.get("dialogViewModel")
        .objectOrNull()
        ?.get("customContent")
        .objectOrNull()
        ?.get("listViewModel")
        .objectOrNull()
        ?.get("listItems")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull {
            it
                .objectOrNull()
                ?.get("listItemViewModel")
                .objectOrNull()
                ?.toCollaborator()
        }.distinctBy { it.channelId }
        .takeIf { it.size > 1 }
        .orEmpty()

private fun JsonElement?.firstShowDialogCommand(): JsonObject? =
    when (this) {
        is JsonObject -> this["showDialogCommand"].objectOrNull() ?: values.firstNotNullOfOrNull { it.firstShowDialogCommand() }
        is JsonArray -> firstNotNullOfOrNull { it.firstShowDialogCommand() }
        else -> null
    }

private fun JsonObject.toCollaborator(): VideoCollaborator? {
    val channelId =
        (this["rendererContext"].firstChannelBrowseId() ?: this["title"].firstChannelBrowseId())
            ?: return null
    val name = this["title"].youtubeText()?.withoutBidiMarks()?.takeIf(String::isNotBlank) ?: return null
    val avatarUrl =
        this["leadingAccessory"]
            .objectOrNull()
            ?.get("avatarViewModel")
            .objectOrNull()
            ?.get("image")
            .largestImageUrl()
    return VideoCollaborator(
        name = name,
        channelId = channelId,
        thumbnailUrl = ThumbnailUrlResolver.resolveChannelAvatar(avatarUrl),
        // "@handle • 21.3M subscribers": the count follows the bullet in every locale.
        subscriberCountText =
            this["subtitle"]
                .youtubeText()
                ?.withoutBidiMarks()
                ?.substringAfter('•', missingDelimiterValue = "")
                ?.trim()
                .orEmpty(),
    )
}

private fun JsonElement?.firstChannelBrowseId(): String? =
    when (this) {
        is JsonObject -> {
            this["browseEndpoint"]
                .objectOrNull()
                ?.get("browseId")
                .stringOrNull()
                ?.takeIf { it.startsWith("UC") }
                ?: values.firstNotNullOfOrNull { it.firstChannelBrowseId() }
        }

        is JsonArray -> {
            firstNotNullOfOrNull { it.firstChannelBrowseId() }
        }

        else -> {
            null
        }
    }

private val BidiMarks = Regex("[‎‏⁦-⁩]")

private fun String.withoutBidiMarks(): String = replace(BidiMarks, "").trim()
