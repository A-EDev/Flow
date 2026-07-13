package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.Comment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

data class CommunityPost(
    val id: String,
    val authorName: String,
    val authorAvatarUrl: String,
    val text: String,
    val imageUrl: String?,
    val likeCountText: String,
    val commentCountText: String,
    val commentEndpointParams: String?,
    val publishedTimeText: String,
)

data class CommunityPostsPage(
    val posts: List<CommunityPost>,
    val continuation: String?,
)

data class CommunityCommentsPage(
    val comments: List<Comment>,
    val continuation: String?,
    val commentCountText: String? = null,
)

internal fun JsonElement.toCommunityPostsPage(
    fallbackAuthorName: String,
    fallbackAuthorAvatarUrl: String,
): CommunityPostsPage {
    val posts = mutableListOf<CommunityPost>()
    var continuation: String? = null

    fun collect(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                val thread = element["backstagePostThreadRenderer"].objectOrNull()
                val renderer = thread
                    ?.get("post").objectOrNull()
                    ?.get("backstagePostRenderer").objectOrNull()
                    ?: element["postRenderer"].objectOrNull()

                if (renderer != null) {
                    renderer.toCommunityPost(fallbackAuthorName, fallbackAuthorAvatarUrl)
                        ?.let(posts::add)
                    return
                }

                if (continuation == null) {
                    continuation = element["continuationItemRenderer"].objectOrNull()
                        ?.continuationToken()
                }
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return CommunityPostsPage(
        posts = posts.distinctBy(CommunityPost::id),
        continuation = continuation,
    )
}

internal fun JsonElement.toCommunityCommentsPage(): CommunityCommentsPage {
    val root = objectOrNull()
    val mutations = root
        ?.get("frameworkUpdates").objectOrNull()
        ?.get("entityBatchUpdate").objectOrNull()
        ?.get("mutations").arrayOrNull()
        .orEmpty()
        .mapNotNull { mutation ->
            val objectValue = mutation.objectOrNull() ?: return@mapNotNull null
            val key = objectValue["entityKey"].stringOrNull() ?: return@mapNotNull null
            key to objectValue["payload"]
        }
        .toMap()

    val comments = mutableListOf<Comment>()
    var continuation: String? = null

    fun collect(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::collect)
            is JsonObject -> {
                val thread = element["commentThreadRenderer"].objectOrNull()
                if (thread != null) {
                    val rawViewModel = thread["commentViewModel"].objectOrNull()
                    val viewModel = rawViewModel
                        ?.get("commentViewModel").objectOrNull()
                        ?: rawViewModel
                    val repliesRenderer = thread["replies"].objectOrNull()
                        ?.get("commentRepliesRenderer").objectOrNull()
                    val comment = viewModel?.toModernComment(mutations, repliesRenderer)
                        ?: thread["comment"].objectOrNull()
                            ?.get("commentRenderer").objectOrNull()
                            ?.toLegacyComment(repliesRenderer)
                    comment?.let(comments::add)
                    return
                }

                element["commentViewModel"].objectOrNull()
                    ?.toModernComment(mutations, null)
                    ?.let {
                        comments.add(it)
                        return
                    }

                element["commentRenderer"].objectOrNull()
                    ?.toLegacyComment(null)
                    ?.let {
                        comments.add(it)
                        return
                    }

                if (continuation == null) {
                    continuation = element["continuationItemRenderer"].objectOrNull()
                        ?.continuationToken()
                }
                element.values.forEach(::collect)
            }
            else -> Unit
        }
    }

    collect(this)
    return CommunityCommentsPage(
        comments = comments.distinctBy(Comment::id),
        continuation = continuation,
        commentCountText = findCommentCountText(root),
    )
}

private fun JsonObject.toCommunityPost(
    fallbackAuthorName: String,
    fallbackAuthorAvatarUrl: String,
): CommunityPost? {
    val id = this["postId"].stringOrNull()?.takeIf(String::isNotBlank) ?: return null
    val replyButton = this["actionButtons"].objectOrNull()
        ?.get("commentActionButtonsRenderer").objectOrNull()
        ?.get("replyButton").objectOrNull()
        ?.get("buttonRenderer").objectOrNull()
    val navigationEndpoint = replyButton?.get("navigationEndpoint").objectOrNull()
    val authorAvatar = this["authorThumbnail"].largestThumbnailUrl()
        ?: fallbackAuthorAvatarUrl
    return CommunityPost(
        id = id,
        authorName = this["authorText"].youtubeText()
            ?.takeIf(String::isNotBlank)
            ?: fallbackAuthorName,
        authorAvatarUrl = normalizeImageUrl(authorAvatar),
        text = this["contentText"].youtubeText().orEmpty(),
        imageUrl = this["backstageAttachment"].findFirstBackstageImageUrl(),
        likeCountText = this["voteCount"].youtubeText().orEmpty(),
        commentCountText = replyButton?.get("text").youtubeText()
            ?: replyButton?.accessibilityLabel()?.countTextFromAccessibilityLabel()
            ?: "",
        commentEndpointParams = navigationEndpoint
            ?.get("browseEndpoint").objectOrNull()
            ?.get("params").stringOrNull()
            ?: navigationEndpoint
                ?.get("signInEndpoint").objectOrNull()
                ?.get("nextEndpoint").objectOrNull()
                ?.get("browseEndpoint").objectOrNull()
                ?.get("params").stringOrNull(),
        publishedTimeText = this["publishedTimeText"].youtubeText().orEmpty(),
    )
}

private fun JsonObject.toModernComment(
    mutations: Map<String, JsonElement?>,
    repliesRenderer: JsonObject?,
): Comment? {
    val commentKey = this["commentKey"].stringOrNull() ?: return null
    val entity = mutations[commentKey].objectOrNull()
        ?.get("commentEntityPayload").objectOrNull()
        ?: return null
    val properties = entity["properties"].objectOrNull() ?: return null
    val author = entity["author"].objectOrNull()
    val toolbar = entity["toolbar"].objectOrNull()
    val id = properties["commentId"].stringOrNull()
        ?: this["commentId"].stringOrNull()
        ?: return null
    val avatar = entity["avatar"].objectOrNull()
        ?.get("image").objectOrNull()
        ?.get("sources").largestThumbnailUrl()
        ?: author?.get("avatar").objectOrNull()
            ?.get("image").objectOrNull()
            ?.get("sources").largestThumbnailUrl()
        ?: ""
    return Comment(
        id = id,
        author = author?.get("displayName").stringOrNull().orEmpty(),
        authorThumbnail = normalizeImageUrl(avatar),
        text = properties["content"].youtubeText().orEmpty(),
        likeCount = parseCount(toolbar?.get("likeCountNotliked")),
        publishedTime = properties["publishedTime"].stringOrNull().orEmpty(),
        replyCount = parseCount(toolbar?.get("replyCount")),
        isPinned = properties["pinnedText"] != null,
        continuationToken = repliesRenderer?.findReplyContinuation(),
        authorChannelId = author?.get("channelId").stringOrNull()
            ?: author?.get("navigationEndpoint").objectOrNull()
                ?.get("browseEndpoint").objectOrNull()
                ?.get("browseId").stringOrNull()
            ?: "",
    )
}

private fun JsonObject.toLegacyComment(repliesRenderer: JsonObject?): Comment? {
    val id = this["commentId"].stringOrNull()?.takeIf(String::isNotBlank) ?: return null
    return Comment(
        id = id,
        author = this["authorText"].youtubeText().orEmpty(),
        authorThumbnail = normalizeImageUrl(this["authorThumbnail"].largestThumbnailUrl().orEmpty()),
        text = this["contentText"].youtubeText().orEmpty(),
        likeCount = parseCount(this["voteCount"]),
        publishedTime = this["publishedTimeText"].youtubeText().orEmpty(),
        replyCount = parseCount(this["replyCount"]),
        isPinned = this["pinnedCommentBadge"] != null,
        continuationToken = repliesRenderer?.findReplyContinuation(),
        authorChannelId = this["authorEndpoint"].objectOrNull()
            ?.get("browseEndpoint").objectOrNull()
            ?.get("browseId").stringOrNull()
            ?: "",
    )
}

private fun JsonObject.continuationToken(): String? =
    this["continuationEndpoint"].objectOrNull()
        ?.get("continuationCommand").objectOrNull()
        ?.get("token").stringOrNull()
        ?: this["button"].objectOrNull()
            ?.get("buttonRenderer").objectOrNull()
            ?.get("command").objectOrNull()
            ?.get("continuationCommand").objectOrNull()
            ?.get("token").stringOrNull()

private fun JsonObject.findReplyContinuation(): String? =
    this["contents"].arrayOrNull()
        ?.firstNotNullOfOrNull { content ->
            content.objectOrNull()
                ?.get("continuationItemRenderer").objectOrNull()
                ?.continuationToken()
        }

private fun JsonElement?.findFirstBackstageImageUrl(): String? {
    when (this) {
        is JsonArray -> forEach { child -> child.findFirstBackstageImageUrl()?.let { return it } }
        is JsonObject -> {
            this["backstageImageRenderer"].objectOrNull()
                ?.get("image").largestThumbnailUrl()
                ?.let { return normalizeImageUrl(it) }
            values.forEach { child -> child.findFirstBackstageImageUrl()?.let { return it } }
        }
        else -> Unit
    }
    return null
}

private fun JsonElement?.largestThumbnailUrl(): String? {
    val thumbnails = objectOrNull()?.get("thumbnails").arrayOrNull()
        ?: arrayOrNull()
        ?: return null
    return thumbnails.maxByOrNull { thumbnail ->
        val objectValue = thumbnail.objectOrNull()
        val width = (objectValue?.get("width") as? JsonPrimitive)?.intOrNull ?: 0
        val height = (objectValue?.get("height") as? JsonPrimitive)?.intOrNull ?: 0
        width.toLong() * height.toLong()
    }?.objectOrNull()?.let { thumbnail ->
        thumbnail["url"].stringOrNull() ?: thumbnail["uri"].stringOrNull()
    }
}

private fun JsonObject.accessibilityLabel(): String? =
    this["accessibility"].objectOrNull()?.get("label").stringOrNull()
        ?: this["accessibilityData"].objectOrNull()
            ?.get("accessibilityData").objectOrNull()
            ?.get("label").stringOrNull()

private fun String.countTextFromAccessibilityLabel(): String =
    Regex("""[\d.,]+\s*[KkMmBb]?""").find(this)?.value?.trim() ?: this

private fun findCommentCountText(root: JsonObject?): String? {
    val panels = root?.get("engagementPanels").arrayOrNull().orEmpty()
    panels.forEach { panel ->
        val section = panel.objectOrNull()
            ?.get("engagementPanelSectionListRenderer").objectOrNull()
            ?: return@forEach
        val identifier = section["panelIdentifier"].stringOrNull()
        if (identifier == "comment-item-section" || identifier == "engagement-panel-comments-section") {
            section["header"].objectOrNull()
                ?.get("engagementPanelTitleHeaderRenderer").objectOrNull()
                ?.get("contextualInfo").youtubeText()
                ?.let { return it }
        }
    }
    return null
}

private fun parseCount(element: JsonElement?): Int {
    val primitive = element as? JsonPrimitive
    primitive?.intOrNull?.let { return it.coerceAtLeast(0) }
    primitive?.longOrNull?.let { return it.coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
    return parseYouTubeViewCount(element.youtubeText() ?: primitive?.content)
        .coerceIn(0, Int.MAX_VALUE.toLong())
        .toInt()
}

private fun normalizeImageUrl(url: String): String =
    if (url.startsWith("//")) "https:$url" else url
