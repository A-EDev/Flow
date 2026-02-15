// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package com.flow.youtube.data.lyrics

/**
 * LyricsProvider implementation for the SimpMusic service.
 * Delegates to SimpMusicLyrics service which returns structured LyricsEntry with word timestamps.
 */
class SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<List<LyricsEntry>> {
        return SimpMusicLyrics.getLyrics(id, duration)
    }
}
