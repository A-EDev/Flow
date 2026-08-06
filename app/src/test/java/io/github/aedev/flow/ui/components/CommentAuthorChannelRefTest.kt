package io.github.aedev.flow.ui.components

import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.youtubeChannelUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentAuthorChannelRefTest {
    @Test
    fun channelIdAuthorsResolveToChannelUrls() {
        val ref = commentAuthorChannelRef(comment(authorChannelId = "UCpfi5mC9g2m_Tp5KwG8uXJQ"))

        assertEquals("UCpfi5mC9g2m_Tp5KwG8uXJQ", ref)
        assertEquals(
            "https://www.youtube.com/channel/UCpfi5mC9g2m_Tp5KwG8uXJQ",
            youtubeChannelUrl(ref),
        )
    }

    @Test
    fun handleAuthorsResolveToHandleUrls() {
        val ref = commentAuthorChannelRef(comment(authorChannelId = "@blacke"))

        assertEquals("https://www.youtube.com/@blacke", youtubeChannelUrl(ref))
    }

    @Test
    fun missingChannelIdFallsBackToTheDisplayedHandle() {
        val ref = commentAuthorChannelRef(comment(author = "@blacke"))

        assertEquals("blacke", ref)
        assertEquals("https://www.youtube.com/@blacke", youtubeChannelUrl(ref))
    }

    private fun comment(
        author: String = "Blacke",
        authorChannelId: String = "",
    ) = Comment(
        id = "comment-id",
        author = author,
        authorThumbnail = "",
        text = "",
        likeCount = 0,
        publishedTime = "",
        authorChannelId = authorChannelId,
    )
}
