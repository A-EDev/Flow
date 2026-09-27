package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.utils.relativedate.RelativeUploadDateParser

internal data class LockupDateAndViews(
    val viewsText: String?,
    val uploadText: String,
    val uploadTimestamp: Long?,
)

/**
 * Tells a lockup's view count from its upload date by structure rather than English words, since
 * both arrive in the request's language ("1,2 M de visualizaciones", "hace 3 semanas"): the part
 * that reads as an age is the date, and the view count is the first other part carrying a number,
 * which YouTube always puts before the date.
 */
internal fun lockupDateAndViews(
    parts: List<String>,
    hl: String?,
    now: Long = System.currentTimeMillis(),
): LockupDateAndViews {
    val candidates = parts.filterNot { it.mentionsViewers() || it.mentionsWaiting() }
    val dated =
        candidates.firstNotNullOfOrNull { part ->
            RelativeUploadDateParser.parse(part, hl, now)?.let { part to it }
        }
    // A lone unread part has nothing to be positioned against: it stays the date row, as an
    // upcoming stream's "Premieres 10/12/26" must.
    val viewsText =
        parts.firstOrNull { it.mentionsViewers() }
            ?: candidates
                .firstOrNull { it != dated?.first && it.any(Char::isDigit) }
                ?.takeIf { dated != null || candidates.size > 1 }
    val uploadText = dated?.first ?: candidates.firstOrNull { it != viewsText }.orEmpty()
    return LockupDateAndViews(viewsText, uploadText, dated?.second)
}

private fun String.mentionsViewers(): Boolean = contains("view", ignoreCase = true) || contains("watching", ignoreCase = true)

private fun String.mentionsWaiting(): Boolean = contains("waiting", ignoreCase = true)
