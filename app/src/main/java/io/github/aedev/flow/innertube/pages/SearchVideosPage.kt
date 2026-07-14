package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed interface WebSearchItem {
    val id: String
}

data class SearchVideoItem(
    override val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val channelThumbnailUrls: List<String>,
    val isLive: Boolean,
) : WebSearchItem

data class SearchChannelItem(
    override val id: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String,
    val url: String,
) : WebSearchItem

data class SearchPlaylistItem(
    override val id: String,
    val name: String,
    val thumbnailUrl: String,
    val videoCount: Int,
) : WebSearchItem

data class SearchVideosPage(
    val items: List<WebSearchItem>,
    val continuation: String?,
) {
    val videos: List<SearchVideoItem>
        get() = items.filterIsInstance<SearchVideoItem>()
}

fun JsonObject.toSearchVideosPage(): SearchVideosPage {
    val items = mutableListOf<WebSearchItem>()
    var continuation: String? = null

    fun collect(element: JsonElement) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                element["videoRenderer"].objectOrNull()?.toSearchVideoItem()?.let(items::add)
                element["channelRenderer"].objectOrNull()?.toSearchChannelItem()?.let(items::add)
                element["playlistRenderer"].objectOrNull()?.toSearchPlaylistItem()?.let(items::add)
                element["lockupViewModel"].objectOrNull()
                    ?.toSearchPlaylistLockupItem()
                    ?.let(items::add)
                element["continuationItemRenderer"].objectOrNull()
                    ?.findFirstString("token")
                    ?.let { continuation = it }
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return SearchVideosPage(
        items = items.distinctBy { item ->
            when (item) {
                is SearchVideoItem -> "video:${item.id}"
                is SearchChannelItem -> "channel:${item.id}"
                is SearchPlaylistItem -> "playlist:${item.id}"
            }
        },
        continuation = continuation,
    )
}

private fun JsonObject.toSearchVideoItem(): SearchVideoItem? {
    val id = this["videoId"].stringOrNull() ?: return null
    val title = this["title"].youtubeText()?.takeIf(String::isNotBlank) ?: return null
    val owner = this["ownerText"] ?: this["longBylineText"]
    val viewText = this["viewCountText"].youtubeText()
        ?: this["shortViewCountText"].youtubeText()
    val channelImages = this["channelThumbnailSupportedRenderers"]
        .collectImageUrls()
        .distinct()
        .takeLast(2)

    return SearchVideoItem(
        id = id,
        title = title,
        channelName = owner.youtubeText().orEmpty(),
        channelId = owner.findFirstString("browseId").orEmpty(),
        thumbnailUrl = this["thumbnail"].largestThumbnailUrl()
            ?: "https://i.ytimg.com/vi/$id/hq720.jpg",
        duration = parseDuration(this["lengthText"].youtubeText()),
        viewCount = parseYouTubeViewCount(viewText),
        uploadDate = this["publishedTimeText"].youtubeText().orEmpty(),
        channelThumbnailUrls = channelImages,
        isLive = viewText?.contains("watching", ignoreCase = true) == true ||
            containsString("style", "LIVE"),
    )
}

private fun JsonObject.toSearchChannelItem(): SearchChannelItem? {
    val id = this["channelId"].stringOrNull() ?: return null
    val canonicalUrl = findFirstString("canonicalBaseUrl")
    return SearchChannelItem(
        id = id,
        name = this["title"].youtubeText()?.takeIf(String::isNotBlank) ?: return null,
        thumbnailUrl = this["thumbnail"].largestThumbnailUrl().orEmpty(),
        subscriberCount = parseYouTubeViewCount(this["subscriberCountText"].youtubeText()),
        description = this["descriptionSnippet"].youtubeText().orEmpty(),
        url = when {
            canonicalUrl.isNullOrBlank() -> "https://www.youtube.com/channel/$id"
            canonicalUrl.startsWith("http") -> canonicalUrl
            else -> "https://www.youtube.com$canonicalUrl"
        },
    )
}

private fun JsonObject.toSearchPlaylistItem(): SearchPlaylistItem? {
    val id = this["playlistId"].stringOrNull() ?: return null
    val count = this["videoCount"].youtubeText()
        ?: this["videoCountText"].youtubeText()
    return SearchPlaylistItem(
        id = id,
        name = this["title"].youtubeText()?.takeIf(String::isNotBlank) ?: return null,
        thumbnailUrl = this["thumbnails"].largestThumbnailUrl().orEmpty(),
        videoCount = parseYouTubeViewCount(count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
}

private fun JsonObject.toSearchPlaylistLockupItem(): SearchPlaylistItem? {
    if (this["contentType"].stringOrNull() != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
    val id = this["contentId"].stringOrNull() ?: return null
    val metadata = this["metadata"].objectOrNull()
        ?.get("lockupMetadataViewModel").objectOrNull()
    val count = findFirstText(Regex("""\b[\d,.]+\s+videos?\b""", RegexOption.IGNORE_CASE))
    return SearchPlaylistItem(
        id = id,
        name = metadata?.get("title").youtubeText()?.takeIf(String::isNotBlank) ?: return null,
        thumbnailUrl = this["contentImage"].largestThumbnailUrl().orEmpty(),
        videoCount = parseYouTubeViewCount(count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
}

private fun JsonElement?.largestThumbnailUrl(): String? {
    var largest: Pair<Int, String>? = null

    fun collect(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                element["url"].stringOrNull()?.let { url ->
                    val width = element["width"].stringOrNull()?.toIntOrNull() ?: 0
                    if (largest == null || width >= largest!!.first) largest = width to url
                }
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return largest?.second
}

private fun JsonElement?.collectImageUrls(): List<String> {
    val urls = mutableListOf<String>()

    fun collect(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                val url = element["url"].stringOrNull()
                if (url != null && ("width" in element || "height" in element)) urls += url
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return urls
}

private fun JsonElement?.findFirstString(key: String): String? {
    when (this) {
        is JsonArray -> forEach { element -> element.findFirstString(key)?.let { return it } }
        is JsonObject -> {
            this[key].stringOrNull()?.let { return it }
            values.forEach { element -> element.findFirstString(key)?.let { return it } }
        }
        else -> Unit
    }
    return null
}

private fun JsonElement?.findFirstText(pattern: Regex): String? {
    stringOrNull()?.let { if (pattern.containsMatchIn(it)) return it }
    when (this) {
        is JsonArray -> forEach { element -> element.findFirstText(pattern)?.let { return it } }
        is JsonObject -> values.forEach { element -> element.findFirstText(pattern)?.let { return it } }
        else -> Unit
    }
    return null
}

private fun JsonElement?.containsString(key: String, value: String): Boolean =
    when (this) {
        is JsonArray -> any { it.containsString(key, value) }
        is JsonObject -> this[key].stringOrNull() == value || values.any { it.containsString(key, value) }
        else -> false
    }

private fun parseDuration(text: String?): Int {
    val parts = text?.split(':')?.mapNotNull(String::toIntOrNull) ?: return 0
    return when (parts.size) {
        3 -> parts[0] * 3_600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        else -> 0
    }
}
