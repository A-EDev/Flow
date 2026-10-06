package io.github.aedev.flow.player.cache

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import io.github.aedev.flow.player.config.PlayerConfig
import java.io.File

/**
 * Owns the app's two player caches: one for videos and Shorts ([PlayerCacheManager], the Shorts
 * pool) and one for songs and animated artwork (DownloadUtil, the music player). Each folder may
 * only ever have one [SimpleCache] open on it, so both are created here and nowhere else.
 */
@UnstableApi
object SharedPlayerCacheProvider {
    private const val TAG = "SharedPlayerCache"

    @Volatile private var cache: SimpleCache? = null

    @Volatile private var musicCache: SimpleCache? = null

    @Volatile private var database: DatabaseProvider? = null

    /**
     * The one database every Media3 cache in the app indexes into. Hilt hands out this same
     * instance, so no second open helper ever works on the same file.
     */
    @Synchronized
    fun databaseProvider(context: Context): DatabaseProvider =
        database ?: StandaloneDatabaseProvider(context.applicationContext).also { database = it }

    /**
     * Returns the video cache, creating it on first call.
     *
     * All callers share the same instance regardless of the [maxCacheSizeBytes] they pass; only the
     * first call's value is used.
     */
    @Synchronized
    fun getOrCreate(
        context: Context,
        maxCacheSizeBytes: Long = PlayerConfig.CACHE_SIZE_BYTES,
    ): SimpleCache = cache ?: create(context, PlayerConfig.CACHE_DIR_NAME, maxCacheSizeBytes).also { cache = it }

    /** Returns the song cache, creating it on first call; only the first call's size is used. */
    @Synchronized
    fun getOrCreateMusic(
        context: Context,
        maxCacheSizeBytes: Long,
    ): SimpleCache = musicCache ?: create(context, PlayerConfig.MUSIC_CACHE_DIR_NAME, maxCacheSizeBytes).also { musicCache = it }

    /**
     * The video cache if it already exists, without creating one.
     *
     * [getOrCreate] opens a SQLite index and scans the cache directory on first call, so callers
     * that run on the main thread (the Shorts pool builds its data sources there, as ExoPlayer
     * requires) must use this and arrange creation elsewhere rather than block.
     */
    fun existing(): SimpleCache? = cache

    /** Release the caches (call only on full app shutdown / tests). */
    @Synchronized
    fun release() {
        cache?.release()
        cache = null
        musicCache?.release()
        musicCache = null
        Log.d(TAG, "Player caches released")
    }

    private fun create(
        context: Context,
        folder: String,
        maxCacheSizeBytes: Long,
    ): SimpleCache {
        val cacheDir = File(context.applicationContext.cacheDir, folder)
        val evictor = if (maxCacheSizeBytes <= 0) NoOpCacheEvictor() else LeastRecentlyUsedCacheEvictor(maxCacheSizeBytes)
        Log.d(TAG, "Creating SimpleCache: dir=$cacheDir, maxBytes=$maxCacheSizeBytes")
        return SimpleCache(cacheDir, evictor, databaseProvider(context))
    }
}
