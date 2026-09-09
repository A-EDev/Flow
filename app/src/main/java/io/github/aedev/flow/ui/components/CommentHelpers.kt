package io.github.aedev.flow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.utils.formatTimeAgo

enum class CommentSortFilter {
    TOP,
    NEWEST,
    OLDEST,
}

private fun relativeTimeToSeconds(timeStr: String): Long {
    val lower = timeStr.lowercase().trim()
    val number = Regex("\\d+").find(lower)?.value?.toLongOrNull() ?: 0L
    return when {
        "second" in lower -> number
        "minute" in lower -> number * 60L
        "hour" in lower -> number * 3_600L
        "day" in lower -> number * 86_400L
        "week" in lower -> number * 604_800L
        "month" in lower -> number * 2_592_000L
        "year" in lower -> number * 31_536_000L
        else -> Long.MAX_VALUE
    }
}

/** Sorts comments for the given filter, keeping pinned comments first. */
fun sortCommentsByFilter(
    comments: List<Comment>,
    filter: CommentSortFilter,
): List<Comment> {
    val pinned = comments.filter { it.isPinned }
    val unpinned = comments.filterNot { it.isPinned }
    val sortedUnpinned =
        when (filter) {
            CommentSortFilter.TOP -> unpinned.sortedByDescending { it.likeCount }
            CommentSortFilter.NEWEST -> unpinned.sortedBy { relativeTimeToSeconds(it.publishedTime) }
            CommentSortFilter.OLDEST -> unpinned.sortedByDescending { relativeTimeToSeconds(it.publishedTime) }
        }
    return pinned + sortedUnpinned
}

/** Converts a "H:MM:SS" / "MM:SS" comment timestamp into milliseconds. */
fun commentTimestampToMs(timestamp: String): Long {
    val parts = timestamp.split(":").map { it.toLongOrNull() ?: 0L }
    val seconds =
        when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0L
        }
    return seconds * 1000L
}

fun formatAuthorName(author: String): String {
    val trimmed = author.trim()
    return if (trimmed.startsWith("@")) {
        trimmed
    } else {
        "@$trimmed"
    }
}

/**
 * Channel reference for a comment author, in one of the forms `youtubeChannelUrl` accepts:
 * a `UC…` channel id, an `@handle`, or a bare handle.
 *
 * Callers must forward the value unchanged — re-prefixing it with `@` turns a channel id into a
 * handle that does not exist, which is why comment authors used to open a 404 page.
 */
fun commentAuthorChannelRef(comment: Comment): String = comment.authorChannelId.trim().ifBlank { comment.author.trim().removePrefix("@") }

internal fun toHighQualityAvatarUrl(url: String): String {
    if (url.isBlank()) return url

    return url
        .replace(Regex("=s\\d+"), "=s1024")
        .replace(Regex("/s\\d+-"), "/s1024-")
        .replace(Regex("=w\\d+-h\\d+"), "=w1024-h1024")
}

@Composable
internal fun localizedCommentPublishedTime(publishedTime: String): String {
    val editedSuffix = Regex("\\s*\\(?edited\\)?\\s*$", RegexOption.IGNORE_CASE)
    val isEdited = editedSuffix.containsMatchIn(publishedTime)
    val time = formatTimeAgo(publishedTime.replace(editedSuffix, "").trim())
    return if (isEdited) {
        stringResource(R.string.comment_time_edited_template, time, stringResource(R.string.comment_edited))
    } else {
        time
    }
}
