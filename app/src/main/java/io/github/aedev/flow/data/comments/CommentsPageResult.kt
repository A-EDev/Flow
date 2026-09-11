package io.github.aedev.flow.data.comments

import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.innertube.pages.VideoCommentSort
import org.schabi.newpipe.extractor.Page

/**
 * One page of comments and whatever the source needs to fetch the next one.
 *
 * Two sources produce this. InnerTube pages with [continuation] and describes the orders the
 * section offers in [sortOptions]; the extractor, which stands in when a response cannot be
 * parsed, pages with [legacyPage] and offers no orders at all.
 */
data class CommentsPageResult(
    val comments: List<Comment> = emptyList(),
    val continuation: String? = null,
    val legacyPage: Page? = null,
    val sortOptions: List<VideoCommentSort> = emptyList(),
    val totalText: String? = null,
    val totalCount: Long? = null,
) {
    val hasMore: Boolean get() = continuation != null || legacyPage != null

    companion object {
        val EMPTY = CommentsPageResult()
    }
}
