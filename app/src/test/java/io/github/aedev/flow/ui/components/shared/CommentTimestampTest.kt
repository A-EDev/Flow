package io.github.aedev.flow.ui.components.shared

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Comment
import org.junit.Test

/** Pins the pure helpers of the comments sheet: timestamp links, sort order and author handles. */
class CommentTimestampTest {
    private fun comment(
        id: String,
        likes: Int = 0,
        publishedTime: String = "1 day ago",
        pinned: Boolean = false,
    ) = Comment(
        id = id,
        author = "author",
        authorThumbnail = "",
        text = "",
        likeCount = likes,
        publishedTime = publishedTime,
        isPinned = pinned,
    )

    private fun ids(comments: List<Comment>) = comments.map { it.id }

    @Test
    fun `minutes and seconds convert to milliseconds`() {
        assertThat(commentTimestampToMs("1:05")).isEqualTo(65_000L)
        assertThat(commentTimestampToMs("01:05")).isEqualTo(65_000L)
        assertThat(commentTimestampToMs("0:00")).isEqualTo(0L)
        assertThat(commentTimestampToMs("90:00")).isEqualTo(5_400_000L)
    }

    @Test
    fun `an hours field is honoured`() {
        assertThat(commentTimestampToMs("1:02:03")).isEqualTo(3_723_000L)
        assertThat(commentTimestampToMs("10:00:00")).isEqualTo(36_000_000L)
    }

    @Test
    fun `text that is not a timestamp maps to the start`() {
        assertThat(commentTimestampToMs("abc")).isEqualTo(0L)
        assertThat(commentTimestampToMs("")).isEqualTo(0L)
    }

    @Test
    fun `a bare number is read as seconds`() {
        // Changed with the move onto the shared parser, which the SponsorBlock dialog also uses:
        // "5" used to map to 0. The comment link regex always carries a colon, so no rendered
        // timestamp reaches this branch.
        assertThat(commentTimestampToMs("5")).isEqualTo(5_000L)
    }

    @Test
    fun `a non numeric field rejects the whole timestamp`() {
        // Changed with the move onto the shared parser: a bad field used to count as 0, so "1:xx"
        // seeked to a minute in. Now nothing is parsed and the seek goes to the start.
        assertThat(commentTimestampToMs("1:xx")).isEqualTo(0L)
        assertThat(commentTimestampToMs("x:30")).isEqualTo(0L)
    }

    @Test
    fun `four fields are rejected`() {
        assertThat(commentTimestampToMs("1:2:3:4")).isEqualTo(0L)
    }

    @Test
    fun `pinned comments stay first in their original order for every filter`() {
        val comments =
            listOf(
                comment("a", likes = 1, publishedTime = "1 day ago"),
                comment("p1", likes = 0, publishedTime = "1 year ago", pinned = true),
                comment("b", likes = 5, publishedTime = "1 hour ago"),
                comment("p2", likes = 9, publishedTime = "1 minute ago", pinned = true),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.TOP))).containsExactly("p1", "p2", "b", "a").inOrder()
        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.NEWEST))).containsExactly("p1", "p2", "b", "a").inOrder()
        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.OLDEST))).containsExactly("p1", "p2", "a", "b").inOrder()
    }

    @Test
    fun `top sorts by likes descending and keeps ties in arrival order`() {
        val comments =
            listOf(
                comment("low", likes = 1),
                comment("tie1", likes = 7),
                comment("high", likes = 20),
                comment("tie2", likes = 7),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.TOP)))
            .containsExactly("high", "tie1", "tie2", "low")
            .inOrder()
    }

    @Test
    fun `newest sorts by the parsed relative age across every unit`() {
        val comments =
            listOf(
                comment("year", publishedTime = "1 year ago"),
                comment("day", publishedTime = "3 days ago"),
                comment("second", publishedTime = "30 seconds ago"),
                comment("month", publishedTime = "2 months ago"),
                comment("hour", publishedTime = "2 hours ago"),
                comment("week", publishedTime = "1 week ago"),
                comment("minute", publishedTime = "5 minutes ago"),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.NEWEST)))
            .containsExactly("second", "minute", "hour", "day", "week", "month", "year")
            .inOrder()
        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.OLDEST)))
            .containsExactly("year", "month", "week", "day", "hour", "minute", "second")
            .inOrder()
    }

    @Test
    fun `larger counts of a smaller unit are still newer than a bigger unit`() {
        val comments =
            listOf(
                comment("day", publishedTime = "1 day ago"),
                comment("hours", publishedTime = "23 hours ago"),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.NEWEST))).containsExactly("hours", "day").inOrder()
    }

    @Test
    fun `prefixes such as streamed or edited do not disturb the parse`() {
        val comments =
            listOf(
                comment("old", publishedTime = "Streamed 2 hours ago"),
                comment("new", publishedTime = "1 hour ago (edited)"),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.NEWEST))).containsExactly("new", "old").inOrder()
    }

    @Test
    fun `an unparsed age sorts as infinitely old`() {
        // Pins current behaviour: only full English unit names are recognised, so "just now", the
        // abbreviated "3 mins ago" and any localised string all rank as the oldest possible age.
        val comments =
            listOf(
                comment("justNow", publishedTime = "just now"),
                comment("abbrev", publishedTime = "3 mins ago"),
                comment("german", publishedTime = "vor 2 Stunden"),
                comment("parsed", publishedTime = "1 year ago"),
            )

        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.NEWEST)))
            .containsExactly("parsed", "justNow", "abbrev", "german")
            .inOrder()
        assertThat(ids(sortCommentsByFilter(comments, CommentSortFilter.OLDEST)))
            .containsExactly("justNow", "abbrev", "german", "parsed")
            .inOrder()
    }

    @Test
    fun `formatAuthorName always yields a single at prefix`() {
        assertThat(formatAuthorName("Blacke")).isEqualTo("@Blacke")
        assertThat(formatAuthorName("@Blacke")).isEqualTo("@Blacke")
        assertThat(formatAuthorName("  Blacke ")).isEqualTo("@Blacke")
    }

    @Test
    fun `formatAuthorName of a blank author is a lone at sign`() {
        // Pins current behaviour.
        assertThat(formatAuthorName("")).isEqualTo("@")
        assertThat(formatAuthorName("   ")).isEqualTo("@")
    }
}
