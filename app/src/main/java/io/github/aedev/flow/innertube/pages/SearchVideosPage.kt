package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

data class SearchVideoItem(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int,
    val viewCount: Long,
    val uploadDate: String,
    val channelThumbnailUrls: List<String>,
    val isLive: Boolean,
)

data class SearchVideosPage(
    val videos: List<SearchVideoItem>,
    val continuation: String?,
)

fun JsonObject.toSearchVideosPage(): SearchVideosPage {
    val videos = mutableListOf<SearchVideoItem>()
    var continuation: String? = null

    fun collect(element: JsonElement) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                element["videoRenderer"].objectOrNull()?.toSearchVideoItem()?.let(videos::add)
                element["continuationItemRenderer"].objectOrNull()
                    ?.findFirstString("token")
                    ?.let { continuation = it }
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return SearchVideosPage(videos.distinctBy { it.id }, continuation)
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

private fun JsonElement?.largestThumbnailUrl(): String? =
    objectOrNull()?.get("thumbnails").arrayOrNull()
        ?.mapNotNull { thumbnail ->
            thumbnail.objectOrNull()?.let { value ->
                value["url"].stringOrNull()?.let { url ->
                    (value["width"].stringOrNull()?.toIntOrNull() ?: 0) to url
                }
            }
        }
        ?.maxByOrNull { it.first }
        ?.second

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
