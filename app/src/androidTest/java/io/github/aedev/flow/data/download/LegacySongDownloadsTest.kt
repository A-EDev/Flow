package io.github.aedev.flow.data.download

import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Reads and drains a Media3 download index and cache written the way the removed download service
 * wrote them, in an in-memory database and a temporary cache folder, never the app's own cache.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class LegacySongDownloadsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database = SQLiteDatabase.create(null)
    private val databaseProvider =
        object : DatabaseProvider {
            override fun getWritableDatabase(): SQLiteDatabase = database

            override fun getReadableDatabase(): SQLiteDatabase = database
        }
    private val index = DefaultDownloadIndex(databaseProvider)
    private val cacheDir = File(context.cacheDir, "legacy-songs-${System.nanoTime()}")
    private lateinit var cache: SimpleCache

    @Before
    fun setUp() {
        cache = SimpleCache(cacheDir, NoOpCacheEvictor(), databaseProvider)
        store("done", Download.STATE_COMPLETED, bytes = 200 * 1024)
        store("partial", Download.STATE_STOPPED, bytes = 10 * 1024)
    }

    @After
    fun tearDown() {
        cache.release()
        cacheDir.deleteRecursively()
        database.close()
    }

    private fun store(
        id: String,
        state: Int,
        bytes: Int,
    ) {
        val request = DownloadRequest.Builder(id, Uri.parse("music://$id")).build()
        index.putDownload(Download(request, state, 0L, 0L, bytes.toLong(), Download.STOP_REASON_NONE, Download.FAILURE_REASON_NONE))
        val spec =
            DataSpec
                .Builder()
                .setUri("music://$id")
                .setKey(id)
                .build()
        CacheWriter(CacheDataSource(cache, ByteArrayDataSource(ByteArray(bytes))), spec, null, null).cache()
    }

    @Test
    fun onlyFinishedSongsCountAsKeptOffline() =
        runBlocking {
            val legacy = LegacySongDownloads(context, databaseProvider, cache)

            assertFalse(legacy.isComplete("done"))
            assertEquals(setOf("done"), legacy.completedIds())
            assertTrue(legacy.isComplete("done"))
            assertTrue(legacy.isCachedForOffline("done"))
            assertFalse(legacy.isCachedForOffline("partial"))
        }

    @Test
    fun removingASongDropsItsCachedBytesAndIndexEntry() =
        runBlocking {
            val legacy = LegacySongDownloads(context, databaseProvider, cache)
            legacy.completedIds()

            legacy.remove("done")

            assertTrue(legacy.completedIds().isEmpty())
            assertTrue(cache.getCachedSpans("done").isEmpty())
            assertNull(index.getDownload("done"))
            assertEquals(10 * 1024L, cache.getCachedSpans("partial").sumOf { it.length })
        }
}
