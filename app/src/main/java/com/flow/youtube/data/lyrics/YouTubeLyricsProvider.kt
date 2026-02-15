package com.flow.youtube.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LyricsProvider wrapping the existing InnertubeMusicService.fetchLyrics.
 * This provider only returns plain text lyrics (no sync), so it's always lowest priority.
 */
class YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<List<LyricsEntry>> = withContext(Dispatchers.IO) {
        try {
            val lyrics = com.flow.youtube.data.newmusic.InnertubeMusicService.fetchLyrics(id)
            if (lyrics != null) {
                Result.success(listOf(LyricsEntry(time = 0L, text = lyrics)))
            } else {
                Result.failure(Exception("No YouTube lyrics found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
