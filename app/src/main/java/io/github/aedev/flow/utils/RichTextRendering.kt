package io.github.aedev.flow.utils

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.aedev.flow.data.model.RichText
import io.github.aedev.flow.data.model.RichTextTarget

const val RICH_TEXT_SEEK = "SEEK_SECONDS"
const val RICH_TEXT_URL = "URL"
const val RICH_TEXT_CHANNEL = "CHANNEL"
const val RICH_TEXT_VIDEO = "VIDEO"
const val RICH_TEXT_HASHTAG = "HASHTAG"
const val RICH_TEXT_EMOJI_PREFIX = "emoji:"

/**
 * Renders text the server already described, so nothing here has to be recovered with a regex.
 *
 * Emoji are appended as inline content whose placeholder is the range they replace, which keeps
 * every later span index the same as the one the response gave.
 */
fun RichText.toAnnotatedString(
    linkColor: Color,
    textColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        val emojiByStart = emojis.associateBy { it.start }
        var index = 0
        while (index < text.length) {
            val emoji = emojiByStart[index]
            if (emoji != null && emoji.end() <= text.length) {
                appendInlineContent(
                    id = RICH_TEXT_EMOJI_PREFIX + emoji.imageUrl,
                    alternateText = text.substring(emoji.start, emoji.end()),
                )
                index = emoji.end()
                continue
            }
            append(text[index])
            index++
        }

        if (text.isNotEmpty()) addStyle(SpanStyle(color = textColor), 0, text.length)

        spans.forEach { span ->
            val end = span.end.coerceAtMost(text.length)
            if (span.start >= end) return@forEach
            when (val target = span.target) {
                is RichTextTarget.Timestamp -> {
                    addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold), span.start, end)
                    addStringAnnotation(RICH_TEXT_SEEK, target.seconds.toString(), span.start, end)
                }

                is RichTextTarget.Video -> {
                    addStyle(linkStyle(linkColor), span.start, end)
                    addStringAnnotation(RICH_TEXT_VIDEO, target.videoId, span.start, end)
                }

                is RichTextTarget.Channel -> {
                    addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium), span.start, end)
                    addStringAnnotation(RICH_TEXT_CHANNEL, target.browseId, span.start, end)
                }

                is RichTextTarget.Hashtag -> {
                    addStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold), span.start, end)
                    addStringAnnotation(RICH_TEXT_HASHTAG, target.tag, span.start, end)
                }

                is RichTextTarget.Url -> {
                    addStyle(linkStyle(linkColor), span.start, end)
                    addStringAnnotation(RICH_TEXT_URL, target.url, span.start, end)
                }
            }
        }
    }

private fun linkStyle(linkColor: Color) =
    SpanStyle(
        color = linkColor,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Medium,
    )

private fun io.github.aedev.flow.data.model.RichTextEmoji.end(): Int = start + length
