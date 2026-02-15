package com.flow.youtube.data.lyrics

/**
 * Common interface for all lyrics providers.
 * Each provider returns structured List<LyricsEntry> directly,
 * preserving word-level timestamps when available.
 */
interface LyricsProvider {
    val name: String

    /**
     * Fetch lyrics for a given track.
     * @param id Video/track ID
     * @param title Track title
     * @param artist Artist name
     * @param duration Track duration in seconds
     * @return Result containing List<LyricsEntry> with optional word-level data
     */
    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<List<LyricsEntry>>
}
