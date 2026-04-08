package io.github.aedev.flow.utils

import android.text.style.URLSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

/**
 * Pure (non-Composable) utility to format comment/reply text with:
 * - HTML entity decoding (&apos;, &quot;, etc.)
 * - Line break handling (<br> to \n)
 * - Clickable URLs (preserving actual href from <a> tags, not just visible text)
 * - Clickable Timestamps (0:00) — TIMESTAMP takes priority over URL for
 *   YouTube chapter/timestamp links like <a href="...?t=74">1:14</a>
 * - Clickable Hashtags (#hashtag)
 *
 * Call from a Composable wrapped in remember(text) for efficiency.
 */
fun formatRichText(
    text: String,
    primaryColor: Color,
    textColor: Color
): AnnotatedString {
    // 1. Replace <br> with newlines, then parse HTML into a Spanned to decode entities and extract URLSpans.
    val processedHtml = text.replace(Regex("(?i)<br\\s*/?>"), "\n")
    val spanned = HtmlCompat.fromHtml(processedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val plainText = spanned.toString().trimEnd()

    val urlSpans = spanned.getSpans(0, spanned.length, URLSpan::class.java)
    val htmlLinkRanges: List<IntRange> = urlSpans.map {
        spanned.getSpanStart(it) until spanned.getSpanEnd(it)
    }

    return buildAnnotatedString {
        append(plainText)

        // ── 1. Timestamps (highest priority) ──────────────────────────────────
        val timestampPattern = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}""")
        val annotatedTimestampRanges = mutableListOf<IntRange>()
        for (match in timestampPattern.findAll(plainText)) {
            val s = match.range.first
            val e = match.range.last + 1
            annotatedTimestampRanges += s until e
            addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), s, e)
            addStringAnnotation("TIMESTAMP", match.value, s, e)
        }

        // ── 2. URLs from HTML anchor tags (href preserved) ────────────────────
        for (span in urlSpans) {
            val s = spanned.getSpanStart(span).coerceAtMost(plainText.length)
            val e = spanned.getSpanEnd(span).coerceAtMost(plainText.length)
            if (s >= e) continue
            val rawUrl = span.url
            val absoluteUrl = if (rawUrl.startsWith("/")) "https://www.youtube.com$rawUrl" else rawUrl
            if (annotatedTimestampRanges.any { range -> s in range || (s <= range.first && e >= range.last + 1) }) continue
            addStyle(
                SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                s, e
            )
            addStringAnnotation("URL", absoluteUrl, s, e)
        }

        // ── 3. Plain-text URLs (not inside an HTML anchor) ────────────────────
        val urlRegex = Regex("""https?://\S+""")
        for (match in urlRegex.findAll(plainText)) {
            val s = match.range.first
            val e = match.range.last + 1
            if (htmlLinkRanges.any { s in it } || annotatedTimestampRanges.any { s in it }) continue
            addStyle(
                SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                s, e
            )
            addStringAnnotation("URL", match.value, s, e)
        }

        // ── 4. Hashtags (not inside a link or timestamp) ──────────────────────
        val hashtagRegex = Regex("""#\w+""")
        for (match in hashtagRegex.findAll(plainText)) {
            val s = match.range.first
            val e = match.range.last + 1
            if (htmlLinkRanges.any { s in it } || annotatedTimestampRanges.any { s in it }) continue
            addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), s, e)
            addStringAnnotation("HASHTAG", match.value, s, e)
        }
    }
}
