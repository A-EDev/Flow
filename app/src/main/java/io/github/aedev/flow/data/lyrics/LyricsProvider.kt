package io.github.aedev.flow.data.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean = true

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null
    ): Result<List<LyricsEntry>>
}
