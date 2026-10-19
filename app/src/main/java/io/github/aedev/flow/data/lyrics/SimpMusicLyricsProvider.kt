// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package io.github.aedev.flow.data.lyrics

import okhttp3.OkHttpClient

/**
 * LyricsProvider implementation for the SimpMusic service.
 * Delegates to SimpMusicLyrics service which returns structured LyricsEntry with word timestamps.
 */
class SimpMusicLyricsProvider(
    client: OkHttpClient,
) : LyricsProvider {
    override val name = "SimpMusic"

    private val simpMusic = SimpMusicLyrics(client)

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<List<LyricsEntry>> = simpMusic.getLyrics(id, duration)
}
