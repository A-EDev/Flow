package io.github.aedev.flow.ui.components.shared

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import io.github.aedev.flow.utils.RICH_TEXT_CHANNEL
import io.github.aedev.flow.utils.RICH_TEXT_SEEK
import io.github.aedev.flow.utils.RICH_TEXT_URL
import io.github.aedev.flow.utils.RICH_TEXT_VIDEO
import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionTapTest {
    private val text: AnnotatedString =
        buildAnnotatedString {
            append("seek video channel site plain text")
            addStringAnnotation(RICH_TEXT_SEEK, "90", 0, 4)
            addStringAnnotation(RICH_TEXT_VIDEO, "https://www.youtube.com/watch?v=dQw4w9WgXcQ", 5, 10)
            addStringAnnotation(RICH_TEXT_CHANNEL, "UCXuqSBlHAE6Xw-yeJA0Tunw", 11, 18)
            addStringAnnotation(RICH_TEXT_URL, "https://example.com", 19, 23)
        }

    private val events = mutableListOf<String>()

    private fun tap(offset: Int) =
        text.handleDescriptionTap(
            offset = offset,
            onSeekMs = { events += "seek:$it" },
            onHashtagClick = { events += "hashtag:$it" },
            onChannelClick = { events += "channel:$it" },
            onOpenUrl = { events += "open:$it" },
        )

    @Test
    fun `each span goes where it points`() {
        tap(1)
        tap(6)
        tap(12)
        tap(20)

        assertEquals(
            listOf(
                "seek:90000",
                "open:https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "channel:UCXuqSBlHAE6Xw-yeJA0Tunw",
                "open:https://example.com",
            ),
            events,
        )
    }

    @Test
    fun `a tap on plain text does nothing`() {
        tap(27)

        assertEquals(emptyList<String>(), events)
    }
}
