package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.Comment
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
            is JsonArray -> {
                element.forEach(::collect)
            }

            is JsonObject -> {
                val thread = element["backstagePostThreadRenderer"].objectOrNull()
                val renderer =
                    thread
                        ?.get("post")
                        .objectOrNull()
                        ?.get("backstagePostRenderer")
                        .objectOrNull()
                        ?: element["postRenderer"].objectOrNull()

                if (renderer != null) {
                    renderer
                        .toCommunityPost(fallbackAuthorName, fallbackAuthorAvatarUrl)
                        ?.let(posts::add)
                    return
                }

                if (continuation == null) {
                    continuation =
                        element["continuationItemRenderer"]
                            .objectOrNull()
                            ?.continuationToken()
                }
                element.values.forEach(::collect)
            }

            else -> {
                Unit
            }
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
    val mutations = commentMutations()

    val comments = mutableListOf<Comment>()
    var continuation: String? = null

    fun collect(element: JsonElement?) {
        when (element) {
            is JsonArray -> {
                element.forEach(::collect)
            }

            is JsonObject -> {
                val thread = element["commentThreadRenderer"].objectOrNull()
                if (thread != null) {
                    val rawViewModel = thread["commentViewModel"].objectOrNull()
                    val viewModel =
                        rawViewModel
                            ?.get("commentViewModel")
                            .objectOrNull()
                            ?: rawViewModel
                    val repliesRenderer =
                        thread["replies"]
                            .objectOrNull()
                            ?.get("commentRepliesRenderer")
                            .objectOrNull()
                    val comment =
                        viewModel?.toModernComment(mutations, repliesRenderer)
                            ?: thread["comment"]
                                .objectOrNull()
                                ?.get("commentRenderer")
                                .objectOrNull()
                                ?.toLegacyComment(repliesRenderer)
                    comment?.let(comments::add)
                    return
                }

                element["commentViewModel"]
                    .objectOrNull()
                    ?.toModernComment(mutations, null)
                    ?.let {
                        comments.add(it)
                        return
                    }

                element["commentRenderer"]
                    .objectOrNull()
                    ?.toLegacyComment(null)
                    ?.let {
                        comments.add(it)
                        return
                    }

                if (continuation == null) {
                    continuation =
                        element["continuationItemRenderer"]
                            .objectOrNull()
                            ?.continuationToken()
                }
                element.values.forEach(::collect)
            }

            else -> {
                Unit
            }
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
    val replyButton =
        this["actionButtons"]
            .objectOrNull()
            ?.get("commentActionButtonsRenderer")
            .objectOrNull()
            ?.get("replyButton")
            .objectOrNull()
            ?.get("buttonRenderer")
            .objectOrNull()
    val navigationEndpoint = replyButton?.get("navigationEndpoint").objectOrNull()
    val authorAvatar =
        this["authorThumbnail"].bestThumbnailUrl()
            ?: fallbackAuthorAvatarUrl
    return CommunityPost(
        id = id,
        authorName =
            this["authorText"]
                .youtubeText()
                ?.takeIf(String::isNotBlank)
                ?: fallbackAuthorName,
        authorAvatarUrl = normalizeImageUrl(authorAvatar),
        text = this["contentText"].youtubeText().orEmpty(),
        imageUrl = this["backstageAttachment"].findFirstBackstageImageUrl(),
        likeCountText = this["voteCount"].youtubeText().orEmpty(),
        commentCountText =
            replyButton?.get("text").youtubeText()
                ?: replyButton?.accessibilityLabel()?.countTextFromAccessibilityLabel()
                ?: "",
        commentEndpointParams =
            navigationEndpoint
                ?.get("browseEndpoint")
                .objectOrNull()
                ?.get("params")
                .stringOrNull()
                ?: navigationEndpoint
                    ?.get("signInEndpoint")
                    .objectOrNull()
                    ?.get("nextEndpoint")
                    .objectOrNull()
                    ?.get("browseEndpoint")
                    .objectOrNull()
                    ?.get("params")
                    .stringOrNull(),
        publishedTimeText = this["publishedTimeText"].youtubeText().orEmpty(),
    )
}

private fun JsonElement?.findFirstBackstageImageUrl(): String? {
    when (this) {
        is JsonArray -> {
            forEach { child -> child.findFirstBackstageImageUrl()?.let { return it } }
        }

        is JsonObject -> {
            this["backstageImageRenderer"]
                .objectOrNull()
                ?.get("image")
                .bestThumbnailUrl()
                ?.let { return normalizeImageUrl(it) }
            values.forEach { child -> child.findFirstBackstageImageUrl()?.let { return it } }
        }

        else -> {
            Unit
        }
    }
    return null
}

private fun findCommentCountText(root: JsonObject?): String? {
    val panels = root?.get("engagementPanels").arrayOrNull().orEmpty()
    panels.forEach { panel ->
        val section =
            panel
                .objectOrNull()
                ?.get("engagementPanelSectionListRenderer")
                .objectOrNull()
                ?: return@forEach
        val identifier = section["panelIdentifier"].stringOrNull()
        if (identifier == "comment-item-section" || identifier == "engagement-panel-comments-section") {
            section["header"]
                .objectOrNull()
                ?.get("engagementPanelTitleHeaderRenderer")
                .objectOrNull()
                ?.get("contextualInfo")
                .youtubeText()
                ?.let { return it }
        }
    }
    return null
}
