package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.utils.RICH_TEXT_CHANNEL
import io.github.aedev.flow.utils.RICH_TEXT_EMOJI_PREFIX
import io.github.aedev.flow.utils.RICH_TEXT_SEEK
import io.github.aedev.flow.utils.RICH_TEXT_URL
import io.github.aedev.flow.utils.formatRichText
import io.github.aedev.flow.utils.toAnnotatedString

private val EmojiSize = 1.2.em

/** A comment's text, its inline emoji, and what a tap at a character offset should do. */
@Immutable
internal data class CommentTextContent(
    val annotated: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
) {
    fun handleTap(
        offset: Int,
        onSeekMs: (Long) -> Unit,
        onOpenUrl: (String) -> Unit,
        onAuthorClick: (String) -> Unit,
    ): Boolean {
        annotated.getStringAnnotations(RICH_TEXT_SEEK, offset, offset).firstOrNull()?.let { seek ->
            seek.item.toLongOrNull()?.let { onSeekMs(it * 1_000L) }
            return true
        }
        annotated.getStringAnnotations(LEGACY_TIMESTAMP, offset, offset).firstOrNull()?.let { legacy ->
            onSeekMs(commentTimestampToMs(legacy.item))
            return true
        }
        annotated.getStringAnnotations(RICH_TEXT_CHANNEL, offset, offset).firstOrNull()?.let { channel ->
            onAuthorClick(channel.item)
            return true
        }
        annotated.getStringAnnotations(RICH_TEXT_URL, offset, offset).firstOrNull()?.let { url ->
            onOpenUrl(url.item)
            return true
        }
        return false
    }

    private companion object {
        const val LEGACY_TIMESTAMP = "TIMESTAMP"
    }
}

/**
 * The comment's text as the server described it, or the extractor's HTML parsed the old way when
 * the comment came from the fallback path.
 */
@Composable
internal fun rememberCommentText(comment: Comment): CommentTextContent {
    val linkColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface
    val richText = comment.richText
    val annotated =
        remember(richText, comment.text, linkColor, textColor) {
            richText?.toAnnotatedString(linkColor = linkColor, textColor = textColor)
                ?: formatRichText(text = comment.text, primaryColor = linkColor, textColor = textColor)
        }
    val inlineContent =
        richText?.emojis.orEmpty().associate { emoji ->
            RICH_TEXT_EMOJI_PREFIX + emoji.imageUrl to
                InlineTextContent(
                    placeholder =
                        Placeholder(
                            width = EmojiSize,
                            height = EmojiSize,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                        ),
                ) {
                    AsyncImage(
                        model = emoji.imageUrl,
                        contentDescription = emoji.label.takeIf { it.isNotBlank() },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        }
    return CommentTextContent(annotated = annotated, inlineContent = inlineContent)
}
