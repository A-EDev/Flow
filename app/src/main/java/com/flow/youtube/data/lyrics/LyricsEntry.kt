package com.flow.youtube.data.lyrics

/**
 * Represents a single line of lyrics with optional word-level timestamps.
 * All time values are in milliseconds for consistency with ExoPlayer's currentPosition.
 */
data class LyricsEntry(
    val time: Long,           
    val text: String,
    val words: List<WordTimestamp>? = null,
    val agent: String? = null,
    val isBackground: Boolean = false
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)
}

/**
 * Word-level timestamp for karaoke-style highlighting.
 * All times are in milliseconds.
 */
data class WordTimestamp(
    val text: String,
    val startTime: Long,  
    val endTime: Long     
)
