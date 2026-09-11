package io.github.aedev.flow.data.model

/**
 * A stretch of text YouTube marked as actionable, with the target it resolved to.
 *
 * The ranges come from the response's own `commandRuns`, so a timestamp carries the seconds the
 * endpoint names rather than a number recovered from the printed text, and a link carries the real
 * destination rather than the `redirect?q=` wrapper YouTube prints.
 */
data class RichTextSpan(
    val start: Int,
    val length: Int,
    val target: RichTextTarget,
) {
    val end: Int get() = start + length
}

sealed interface RichTextTarget {
    /** A position inside the video the text belongs to. */
    data class Timestamp(
        val seconds: Long,
    ) : RichTextTarget

    /** Another video, optionally at a position. */
    data class Video(
        val videoId: String,
        val startSeconds: Long? = null,
    ) : RichTextTarget

    data class Channel(
        val browseId: String,
    ) : RichTextTarget

    data class Hashtag(
        val tag: String,
    ) : RichTextTarget

    data class Url(
        val url: String,
    ) : RichTextTarget
}

/** A custom emoji YouTube substitutes for a range of the text. */
data class RichTextEmoji(
    val start: Int,
    val length: Int,
    val imageUrl: String,
    val label: String,
)

/**
 * Text plus the spans and emoji the server described for it.
 *
 * Null on any surface still fed by the extractor, which hands over HTML and leaves the caller to
 * recover structure with regex.
 */
data class RichText(
    val text: String,
    val spans: List<RichTextSpan> = emptyList(),
    val emojis: List<RichTextEmoji> = emptyList(),
) {
    val hasTimestamp: Boolean get() = spans.any { it.target is RichTextTarget.Timestamp }
}
