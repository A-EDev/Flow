package io.github.aedev.flow.data.download

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.di.DownloadCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Songs older versions kept offline inside a Media3 download cache instead of saving them as files.
 * They stay playable from that cache, read only, until each one is downloaded again as a file and
 * its cached copy is removed; nothing is ever added to it any more.
 */
@OptIn(UnstableApi::class)
@Singleton
class LegacySongDownloads
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        databaseProvider: DatabaseProvider,
        @DownloadCache private val cache: SimpleCache,
    ) {
        private val index = DefaultDownloadIndex(databaseProvider)
        private val lock = Mutex()

        @Volatile
        private var completed: Set<String>? = null

        /** The songs cached in full, read from the old download index once. */
        suspend fun completedIds(): Set<String> =
            completed ?: lock.withLock {
                completed ?: withContext(Dispatchers.IO) { readCompleted() }.also { completed = it }
            }

        /** [completedIds] as far as it has been read; empty before the first read. */
        fun isComplete(videoId: String): Boolean = completed?.contains(videoId) == true

        /** Whether enough of [videoId] is cached to start playing it with no network. */
        fun isCachedForOffline(videoId: String): Boolean =
            runCatching { cache.getCachedSpans(videoId).sumOf { it.length } >= MIN_OFFLINE_BYTES }.getOrDefault(false)

        /** Drops the cached copy of a song that now has its own file. */
        suspend fun remove(videoId: String) =
            withContext(Dispatchers.IO) {
                lock.withLock {
                    runCatching {
                        index.removeDownload(videoId)
                        cache.removeResource(videoId)
                    }.onFailure { Log.w(TAG, "Could not remove the cached copy of $videoId", it) }
                    completed = completed?.minus(videoId)
                }
            }

        /** Stops what the removed download service left scheduled and its notification channel. */
        fun retireService() {
            runCatching { PlatformScheduler(context, LEGACY_JOB_ID).cancel() }
            context.getSystemService(NotificationManager::class.java)?.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }

        private fun readCompleted(): Set<String> =
            runCatching {
                index.getDownloads(Download.STATE_COMPLETED).use { cursor ->
                    buildSet { while (cursor.moveToNext()) add(cursor.download.request.id) }
                }
            }.onFailure { Log.w(TAG, "Could not read the old download index", it) }
                .getOrDefault(emptySet())

        private companion object {
            const val TAG = "LegacySongDownloads"
            const val MIN_OFFLINE_BYTES = 100 * 1024L
            const val LEGACY_JOB_ID = 1
            const val LEGACY_CHANNEL_ID = "download_channel"
        }
    }

/** What moving the cached songs to files does next. Pure, so it is unit tested. */
internal data class CachedSongMigration(
    /** Songs whose file exists, so their cached copy can go. */
    val drop: List<String>,
    /** Songs with no download row yet, to download as files. */
    val queue: List<String>,
) {
    companion object {
        fun of(
            storedIds: List<String>,
            cached: Set<String>,
            hasFile: (String) -> Boolean,
            hasRow: (String) -> Boolean,
        ): CachedSongMigration {
            val candidates = storedIds.distinct().filter { it in cached }
            val drop = candidates.filter(hasFile)
            return CachedSongMigration(drop, (candidates - drop.toSet()).filterNot(hasRow))
        }
    }
}
