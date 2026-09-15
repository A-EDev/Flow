package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.arrayOrNull
import io.github.aedev.flow.innertube.pages.objectOrNull
import io.github.aedev.flow.innertube.pages.renderer.browseParams
import io.github.aedev.flow.innertube.pages.renderer.forEachObject
import io.github.aedev.flow.innertube.pages.stringOrNull
import io.github.aedev.flow.innertube.pages.youtubeText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * One row of a tab's filter bar. A tab can carry several: the Videos tab shows a sort dropdown
 * *and* a visibility chip row, and they filter independently.
 */
data class ChannelFilterGroup(
    val title: String?,
    val isDropdown: Boolean,
    val options: List<ChannelFilterOption>,
) {
    val selectedIndex: Int get() = options.indexOfFirst { it.selected }
}

/**
 * A filter arrives as one of two commands and they are not interchangeable: chips and dropdown
 * entries carry a [continuation], while the playlists sort menu carries browse [params] and has to be
 * re-requested as a fresh browse.
 */
data class ChannelFilterOption(
    val label: String,
    val selected: Boolean = false,
    val continuation: String? = null,
    val params: String? = null,
)

internal fun JsonElement.channelFilterGroups(): List<ChannelFilterGroup> {
    val groups = mutableListOf<ChannelFilterGroup>()
    val chips = mutableListOf<ChannelFilterOption>()

    findFirstRenderer("chipBarViewModel")
        .objectOrNull()
        ?.get("chips")
        .arrayOrNull()
        .orEmpty()
        .forEach { entry ->
            val chip = entry.objectOrNull()?.get("chipViewModel").objectOrNull() ?: return@forEach
            val dropdown = chip.dropdownOptions()
            if (dropdown.isNotEmpty()) {
                groups += ChannelFilterGroup(title = null, isDropdown = true, options = dropdown)
                return@forEach
            }
            chip.asChip()?.let(chips::add)
        }

    if (chips.isNotEmpty()) {
        groups += ChannelFilterGroup(title = null, isDropdown = false, options = chips)
    }

    findFirstRenderer("sortFilterSubMenuRenderer").objectOrNull()?.let { menu ->
        val options =
            menu["subMenuItems"]
                .arrayOrNull()
                .orEmpty()
                .mapNotNull { item ->
                    val value = item.objectOrNull() ?: return@mapNotNull null
                    val label = value["title"].youtubeText()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    ChannelFilterOption(
                        label = label,
                        selected = value["selected"].stringOrNull() == "true",
                        params = value["navigationEndpoint"].objectOrNull()?.browseParams(),
                    )
                }
        if (options.isNotEmpty()) {
            groups += ChannelFilterGroup(title = menu["title"].youtubeText(), isDropdown = true, options = options)
        }
    }

    return groups
}

private fun JsonObject.asChip(): ChannelFilterOption? {
    val label = this["text"].youtubeText()?.takeIf(String::isNotBlank) ?: return null
    val token = this["tapCommand"].firstContinuationToken() ?: return null
    return ChannelFilterOption(label = label, selected = this["selected"].stringOrNull() == "true", continuation = token)
}

/**
 * The sort control is a chip that opens a sheet, not a chip per option: its entries live under
 * `showSheetCommand`, and a parser that only reads `tapCommand.continuationCommand` sees the chip's
 * own reload token and no options at all.
 */
private fun JsonObject.dropdownOptions(): List<ChannelFilterOption> =
    this["tapCommand"]
        .objectOrNull()
        ?.get("innertubeCommand")
        .objectOrNull()
        ?.get("showSheetCommand")
        .objectOrNull()
        ?.get("panelLoadingStrategy")
        .objectOrNull()
        ?.get("inlineContent")
        .objectOrNull()
        ?.get("sheetViewModel")
        .objectOrNull()
        ?.get("content")
        .objectOrNull()
        ?.get("listViewModel")
        .objectOrNull()
        ?.get("listItems")
        .arrayOrNull()
        .orEmpty()
        .mapNotNull { entry ->
            val item = entry.objectOrNull()?.get("listItemViewModel").objectOrNull() ?: return@mapNotNull null
            val label = item["title"].youtubeText()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            ChannelFilterOption(
                label = label,
                selected = item["isSelected"].stringOrNull() == "true",
                continuation = item["rendererContext"].firstContinuationToken(),
            )
        }

/** The token sits behind a `commandExecutorCommand` wrapper whose depth varies by control. */
private fun JsonElement?.firstContinuationToken(): String? {
    var token: String? = null
    forEachObject { node ->
        if (token != null) return@forEachObject
        node["continuationCommand"]
            .objectOrNull()
            ?.get("token")
            .stringOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { token = it }
    }
    return token
}
