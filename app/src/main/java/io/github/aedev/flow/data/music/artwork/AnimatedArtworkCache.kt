package io.github.aedev.flow.data.music.artwork

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** A lookup answered before: the loop, or null when the album has none, and when it was asked. */
internal data class CachedLoop(
    val url: String?,
    val atMs: Long,
)

internal interface AnimatedArtworkCache {
    suspend fun get(key: String): CachedLoop?

    suspend fun put(
        key: String,
        loop: CachedLoop,
    )

    suspend fun remove(key: String)
}

private val Context.animatedArtworkStore by preferencesDataStore(name = "animated_artwork")

/** Answers kept across launches, so the service is asked about an album once in [MAX_ENTRIES]. */
internal class DataStoreAnimatedArtworkCache(
    private val context: Context,
) : AnimatedArtworkCache {
    override suspend fun get(key: String): CachedLoop? {
        val stored = context.animatedArtworkStore.data.first()[stringPreferencesKey(key)] ?: return null
        val atMs = stored.substringBefore(SEPARATOR).toLongOrNull() ?: return null
        return CachedLoop(stored.substringAfter(SEPARATOR).ifEmpty { null }, atMs)
    }

    override suspend fun put(
        key: String,
        loop: CachedLoop,
    ) {
        context.animatedArtworkStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = "${loop.atMs}$SEPARATOR${loop.url.orEmpty()}"
            val overflow = preferences.asMap().size - MAX_ENTRIES
            if (overflow > 0) {
                preferences
                    .asMap()
                    .entries
                    .sortedBy { (it.value as? String)?.substringBefore(SEPARATOR)?.toLongOrNull() ?: 0L }
                    .take(overflow)
                    .forEach { preferences -= it.key }
            }
        }
    }

    override suspend fun remove(key: String) {
        context.animatedArtworkStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private companion object {
        const val SEPARATOR = "|"
        const val MAX_ENTRIES = 600
    }
}
