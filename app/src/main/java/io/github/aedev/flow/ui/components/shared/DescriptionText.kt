package io.github.aedev.flow.ui.components.shared

import android.text.style.URLSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat
import io.github.aedev.flow.ui.theme.DescriptionLinkBlue
import io.github.aedev.flow.utils.RICH_TEXT_CHANNEL
import io.github.aedev.flow.utils.RICH_TEXT_HASHTAG
import io.github.aedev.flow.utils.RICH_TEXT_SEEK
import io.github.aedev.flow.utils.RICH_TEXT_URL
import io.github.aedev.flow.utils.RICH_TEXT_VIDEO

fun parseHtmlDescription(
    rawHtml: String,
    linkColor: Color = DescriptionLinkBlue,
): AnnotatedString {
    // 1. Parse HTML into an Android Spanned object (Handles <br>, <a>, &amp;)
    val spanned = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString()

    return buildAnnotatedString {
        // 2. Append the clean text (no tags)
        append(text)

        // 3. Find all URLSpans created by the HTML parser and apply Compose styles
        val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
        val htmlLinkRanges: List<IntRange> =
            urlSpans.map {
                spanned.getSpanStart(it) until spanned.getSpanEnd(it)
            }
        for (span in urlSpans) {
            val start = spanned.getSpanStart(span).coerceAtMost(text.length)
            val end = spanned.getSpanEnd(span).coerceAtMost(text.length)
            if (start >= end) continue
            val rawUrl = span.url
            val absoluteUrl = if (rawUrl.startsWith("/")) "https://www.youtube.com$rawUrl" else rawUrl
            addStyle(
                style =
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold,
                    ),
                start = start,
                end = end,
            )
            addStringAnnotation(tag = "URL", annotation = absoluteUrl, start = start, end = end)
        }

        // 4. Find plain-text URLs (https://... not covered by an anchor tag)
        val htmlUrlStarts = urlSpans.map { spanned.getSpanStart(it) }.toSet()
        val urlRegex = Regex("""https?://[^\s]+""")
        urlRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            // Skip if already covered by an HTML anchor
            if (start !in htmlUrlStarts) {
                val end = matchResult.range.last + 1
                addStyle(
                    style =
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    start = start,
                    end = end,
                )
                addStringAnnotation(tag = "URL", annotation = matchResult.value, start = start, end = end)
            }
        }

        val timestampRegex = Regex("""\b(?:[0-9]{1,2}:)?[0-9]{1,2}:[0-9]{2}\b""")
        timestampRegex.findAll(text).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            addStyle(
                style =
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                start = start,
                end = end,
            )
            addStringAnnotation(tag = "TIMESTAMP", annotation = matchResult.value, start = start, end = end)
        }
    }
}

/**
 * Routes a tap in the description to whatever the span under it points at.
 *
 * Timestamps carry their seconds when the text came from InnerTube; a description that arrived as
 * HTML still yields a printed "1:57" that has to be parsed back.
 */
internal fun AnnotatedString.handleDescriptionTap(
    offset: Int,
    onSeekMs: (Long) -> Unit,
    onHashtagClick: ((String) -> Unit)?,
    onChannelClick: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    getStringAnnotations(RICH_TEXT_SEEK, offset, offset).firstOrNull()?.let { seek ->
        seek.item.toLongOrNull()?.let { onSeekMs(it * 1_000L) }
        return
    }
    getStringAnnotations(LEGACY_TIMESTAMP_TAG, offset, offset).firstOrNull()?.let { legacy ->
        onSeekMs(commentTimestampToMs(legacy.item))
        return
    }
    getStringAnnotations(RICH_TEXT_HASHTAG, offset, offset).firstOrNull()?.let { hashtag ->
        onHashtagClick?.invoke(hashtag.item.removePrefix("#"))
        return
    }
    getStringAnnotations(RICH_TEXT_CHANNEL, offset, offset).firstOrNull()?.let { channel ->
        onChannelClick(channel.item)
        return
    }
    (getStringAnnotations(RICH_TEXT_VIDEO, offset, offset) + getStringAnnotations(RICH_TEXT_URL, offset, offset))
        .firstOrNull()
        ?.let { link -> onOpenUrl(link.item) }
}

private const val LEGACY_TIMESTAMP_TAG = "TIMESTAMP"
