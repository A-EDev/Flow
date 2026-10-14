package io.github.aedev.flow.data.video.downloader.work

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.github.aedev.flow.data.lyrics.lrcText
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.tags.displayArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up a song's lyrics once when it is downloaded, so they are written into the file and play
 * offline in Flow and in other players. Stored with the request, so a retry never asks again. The
 * retag sweep over older downloads never calls this, which would be one lookup per file.
 */
@Singleton
class DownloadLyrics internal constructor(
    private val lyricsHelper: LyricsHelper,
    private val downloadDao: DownloadDao,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        downloadDao: DownloadDao,
    ) : this(LyricsHelper(context), downloadDao)

    suspend fun addTo(request: DownloadRequest): DownloadRequest {
        val tags = request.tags
        if (!request.isMusic || tags.lyrics != null) return request
        val entries =
            try {
                withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                    lyricsHelper.getLyrics(
                        videoId = request.videoId,
                        title = tags.title,
                        artist = tags.displayArtist().orEmpty(),
                        duration = request.durationSeconds,
                        album = tags.album,
                    )
                }?.first
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "${request.videoId}: lyrics unavailable", e)
                null
            }
        if (entries.isNullOrEmpty()) return request
        val synced = lyricsHelper.entriesAreSynced(entries)
        val text = lrcText(if (synced) entries else emptyList(), entries.joinToString("\n") { it.text }).trim()
        if (text.isEmpty()) return request
        val withLyrics = request.copy(tags = tags.copy(lyrics = text))
        downloadDao.updateRequest(request.videoId, withLyrics.encode())
        return withLyrics
    }

    private companion object {
        const val TAG = "DownloadLyrics"
        const val LOOKUP_TIMEOUT_MS = 30_000L
    }
}
