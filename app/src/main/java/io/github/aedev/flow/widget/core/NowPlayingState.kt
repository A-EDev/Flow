package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Cross-process-safe snapshot of the music session for the Now Playing widget.
 * The widget renders only this snapshot — it never touches live player state
 * (Glance widgets must stay stateless/passive per the official guidance).
 */
data class NowPlayingSnapshot(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isLiked: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

private val Context.nowPlayingWidgetStore by preferencesDataStore(name = "now_playing_widget")

private object Keys {
    val MEDIA_ID = stringPreferencesKey("media_id")
    val TITLE = stringPreferencesKey("title")
    val ARTIST = stringPreferencesKey("artist")
    val ARTWORK_URL = stringPreferencesKey("artwork_url")
    val IS_PLAYING = booleanPreferencesKey("is_playing")
    val IS_LIKED = booleanPreferencesKey("is_liked")
    val POSITION_MS = longPreferencesKey("position_ms")
    val DURATION_MS = longPreferencesKey("duration_ms")
}

fun Context.nowPlayingSnapshotFlow(): Flow<NowPlayingSnapshot?> =
    nowPlayingWidgetStore.data.map { prefs ->
        val mediaId = prefs[Keys.MEDIA_ID] ?: return@map null
        NowPlayingSnapshot(
            mediaId = mediaId,
            title = prefs[Keys.TITLE].orEmpty(),
            artist = prefs[Keys.ARTIST].orEmpty(),
            artworkUrl = prefs[Keys.ARTWORK_URL],
            isPlaying = prefs[Keys.IS_PLAYING] ?: false,
            isLiked = prefs[Keys.IS_LIKED] ?: false,
            positionMs = prefs[Keys.POSITION_MS] ?: 0L,
            durationMs = prefs[Keys.DURATION_MS] ?: 0L,
        )
    }

suspend fun Context.writeNowPlayingSnapshot(snapshot: NowPlayingSnapshot) {
    nowPlayingWidgetStore.edit { prefs ->
        prefs[Keys.MEDIA_ID] = snapshot.mediaId
        prefs[Keys.TITLE] = snapshot.title
        prefs[Keys.ARTIST] = snapshot.artist
        snapshot.artworkUrl?.let { prefs[Keys.ARTWORK_URL] = it } ?: prefs.remove(Keys.ARTWORK_URL)
        prefs[Keys.IS_PLAYING] = snapshot.isPlaying
        prefs[Keys.IS_LIKED] = snapshot.isLiked
        prefs[Keys.POSITION_MS] = snapshot.positionMs
        prefs[Keys.DURATION_MS] = snapshot.durationMs
    }
}

/** Keeps the last track visible but paused — used when the music service is destroyed. */
suspend fun Context.markNowPlayingStopped() {
    nowPlayingWidgetStore.edit { prefs -> prefs[Keys.IS_PLAYING] = false }
}
