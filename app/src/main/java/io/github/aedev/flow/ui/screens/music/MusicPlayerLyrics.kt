package io.github.aedev.flow.ui.screens.music

import android.content.Context
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.lyrics.LyricsCandidate
import io.github.aedev.flow.data.lyrics.LyricsEntry
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The music player's lyrics: fetching for each track, refreshing, browsing other sources, manual
 * edits and timing. Everything it learns is written into the player's shared ui state.
 */
internal class MusicPlayerLyrics(
    private val context: Context,
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicPlayerUiState>,
    private val lyricsHelper: LyricsHelper,
    private val playerPreferences: PlayerPreferences,
) {
    private var lyricsJob: kotlinx.coroutines.Job? = null

    private fun cleanName(name: String): String =
        name
            .replace(Regex("(?i)\\s*-\\s*topic$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(\\[]official (audio|video|music video|lyric video)[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(\\[]lyrics?[)\\]]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[(]feat\\.? .*?[)]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("(?i)\\s*[\\[]feat\\.? .*?[\\]]", RegexOption.IGNORE_CASE), "")
            .trim()

    fun fetch(
        videoId: String,
        artist: String,
        title: String,
        duration: Int? = null,
        album: String? = null,
    ) {
        lyricsJob?.cancel()
        lyricsJob =
            scope.launch {
                uiState.update {
                    it.copy(
                        isLyricsLoading = true,
                        lyrics = null,
                        syncedLyrics = emptyList(),
                        lyricsProviderName = "",
                        lyricsSyncOffsetMs = 0L,
                        lyricsCandidates = emptyList(),
                    )
                }

                val cleanArtist = cleanName(artist)
                val cleanTitle = cleanName(title)
                val targetDuration = duration ?: (uiState.value.duration.toInt() / 1000)

                try {
                    val result = lyricsHelper.getLyrics(videoId, cleanTitle, cleanArtist, targetDuration, album)

                    if (result != null) {
                        val (entries, providerName) = result
                        val hasWords = entries.any { it.words != null }
                        val isSynced = lyricsHelper.entriesAreSynced(entries)
                        android.util.Log.d(
                            "MusicPlayerViewModel",
                            "Got ${entries.size} lyrics lines from $providerName (word-sync=$hasWords, synced=$isSynced)",
                        )

                        val plainText = entries.joinToString("\n") { it.text }
                        uiState.update {
                            it.copy(
                                isLyricsLoading = false,
                                lyrics = plainText.takeIf { it.isNotBlank() },
                                syncedLyrics = if (isSynced) entries else emptyList(),
                                lyricsProviderName = providerName,
                            )
                        }
                    } else {
                        uiState.update { it.copy(isLyricsLoading = false) }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MusicPlayerViewModel", "Lyrics fetch failed", e)
                    uiState.update { it.copy(isLyricsLoading = false) }
                }
            }
    }

    /**
     * Called when the player screen opens for a track that is ALREADY playing in
     * EnhancedMusicPlayerManager (same videoId). In that case, the currentTrack
     * StateFlow doesn't re-emit, so fetch is never triggered automatically.
     *
     * - If lyrics are already loaded for this track, does nothing (cache hit).
     * - Otherwise fetches lyrics as normal.
     */
    fun ensureLoaded(track: MusicTrack) {
        val state = uiState.value
        if (state.isLyricsLoading) return
        if (!state.syncedLyrics.isNullOrEmpty()) return
        if (!state.lyrics.isNullOrEmpty()) return
        fetch(
            videoId = track.videoId,
            artist = track.artist,
            title = track.title,
            duration = track.duration,
            album = track.album,
        )
    }

    fun refresh() {
        val track = uiState.value.currentTrack ?: return
        scope.launch {
            try {
                lyricsHelper.forceRefresh(track.videoId)
            } catch (e: Exception) {
                android.util.Log.w("MusicPlayerViewModel", "forceRefresh failed: ${e.message}")
            }
            fetch(
                videoId = track.videoId,
                artist = track.artist,
                title = track.title,
                duration = track.duration,
                album = track.album,
            )
        }
    }

    private var browseLyricsJob: kotlinx.coroutines.Job? = null

    fun browseCandidates() {
        val track = uiState.value.currentTrack ?: return
        browseLyricsJob?.cancel()
        browseLyricsJob =
            scope.launch {
                uiState.update { it.copy(isBrowsingLyrics = true, lyricsCandidates = emptyList()) }
                try {
                    lyricsHelper.getAllLyrics(
                        videoId = track.videoId,
                        title = cleanName(track.title),
                        artist = cleanName(track.artist),
                        duration = track.duration,
                        album = track.album,
                    ) { candidate ->
                        if (isActive) {
                            uiState.update { it.copy(lyricsCandidates = it.lyricsCandidates + candidate) }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w("MusicPlayerViewModel", "Lyrics browse failed: ${e.message}")
                } finally {
                    // cancel() does not wait: a superseded browse's finally can run after the
                    // replacement already set isBrowsingLyrics = true. Only the job that is
                    // still current may clear the flag.
                    if (browseLyricsJob === coroutineContext[kotlinx.coroutines.Job]) {
                        uiState.update { it.copy(isBrowsingLyrics = false) }
                    }
                }
            }
    }

    fun cancelBrowse() {
        browseLyricsJob?.cancel()
        uiState.update { it.copy(isBrowsingLyrics = false) }
    }

    fun applyCandidate(candidate: LyricsCandidate) {
        val track = uiState.value.currentTrack ?: return
        scope.launch {
            lyricsHelper.applyManualLyrics(track.videoId, candidate.entries)
            val plainText = candidate.entries.joinToString("\n") { it.text }
            uiState.update {
                it.copy(
                    lyrics = plainText.takeIf { text -> text.isNotBlank() },
                    syncedLyrics = if (candidate.synced) candidate.entries else emptyList(),
                    lyricsProviderName = candidate.providerName,
                    lyricsSyncOffsetMs = 0L,
                )
            }
        }
    }

    fun applyEdited(text: String) {
        val track = uiState.value.currentTrack ?: return
        scope.launch {
            val parsed =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    io.github.aedev.flow.data.lyrics.LyricsUtils
                        .parseLyrics(text)
                }
            val entries =
                parsed.ifEmpty {
                    text
                        .lines()
                        .map { line -> line.trim() }
                        .filter { line -> line.isNotBlank() }
                        .map { line -> LyricsEntry(0L, line) }
                }
            if (entries.isEmpty()) return@launch
            val synced = lyricsHelper.entriesAreSynced(entries)
            lyricsHelper.applyManualLyrics(track.videoId, entries)
            val plainText = entries.joinToString("\n") { it.text }
            uiState.update {
                it.copy(
                    lyrics = plainText.takeIf { t -> t.isNotBlank() },
                    syncedLyrics = if (synced) entries else emptyList(),
                    lyricsProviderName = context.getString(io.github.aedev.flow.R.string.lyrics_source_edited),
                )
            }
        }
    }

    fun adjustSyncOffset(deltaMs: Long) {
        uiState.update {
            it.copy(lyricsSyncOffsetMs = (it.lyricsSyncOffsetMs + deltaMs).coerceIn(-30_000L, 30_000L))
        }
    }

    fun resetSyncOffset() {
        uiState.update { it.copy(lyricsSyncOffsetMs = 0L) }
    }

    fun setTextAlign(align: String) {
        scope.launch { playerPreferences.setLyricsTextAlign(align) }
    }
}
