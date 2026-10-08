package io.github.aedev.flow.innertube.pages

import androidx.compose.ui.graphics.Color
import io.github.aedev.flow.data.model.RichText
import io.github.aedev.flow.data.model.RichTextSpan
import io.github.aedev.flow.data.model.RichTextTarget
import io.github.aedev.flow.utils.RICH_TEXT_VIDEO
import io.github.aedev.flow.utils.toAnnotatedString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RichTextTargetsTest {
    private fun targetsOf(vararg commands: String): List<RichTextTarget> {
        val runs =
            commands.mapIndexed { index, command ->
                """{"startIndex": $index, "length": 1, "onTap": {"innertubeCommand": $command}}"""
            }
        val text = "x".repeat(commands.size)
        val json = """{"content": "$text", "commandRuns": [${runs.joinToString()}]}"""
        return requireNotNull(Json.parseToJsonElement(json).toRichText(ownVideoId = "ownVideo123")).spans.map { it.target }
    }

    private fun browse(id: String) = """{"browseEndpoint": {"browseId": "$id"}}"""

    @Test
    fun `a browse link opens what its id names`() {
        assertEquals(
            listOf(
                RichTextTarget.Channel("UCXuqSBlHAE6Xw-yeJA0Tunw"),
                RichTextTarget.Url("https://www.youtube.com/browse/VLPLabcdef"),
                RichTextTarget.Url("https://www.youtube.com/browse/MPREb_abc123"),
                RichTextTarget.Channel("FEunknown"),
            ),
            targetsOf(browse("UCXuqSBlHAE6Xw-yeJA0Tunw"), browse("VLPLabcdef"), browse("MPREb_abc123"), browse("FEunknown")),
        )
    }

    @Test
    fun `a hashtag browse link stays a hashtag`() {
        assertEquals(
            listOf(RichTextTarget.Hashtag("queen")),
            targetsOf("""{"browseEndpoint": {"browseId": "FEhashtag", "canonicalBaseUrl": "/hashtag/queen"}}"""),
        )
    }

    @Test
    fun `a link to another video carries its watch link`() {
        val text =
            RichText(
                text = "watch",
                spans = listOf(RichTextSpan(0, 5, RichTextTarget.Video("dQw4w9WgXcQ", startSeconds = 42))),
            ).toAnnotatedString(linkColor = Color.Blue, textColor = Color.Black)

        assertEquals(
            listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"),
            text.getStringAnnotations(RICH_TEXT_VIDEO, 0, 5).map { it.item },
        )
    }
}
