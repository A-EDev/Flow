package io.github.aedev.flow.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** #1054: a long history must load in full without any single cursor outgrowing its window. */
@RunWith(AndroidJUnit4::class)
class WatchHistoryPagingTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: WatchHistoryDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .build()
        dao = database.watchHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Long enough that the 5,000 rows together take several 2 MB CursorWindows.
    private fun row(index: Int) =
        WatchHistoryEntity(
            videoId = "video%05d".format(index),
            position = index * 1_000L,
            duration = 600_000L,
            timestamp = 1_000_000L + index / 3,
            title = "Title $index ".repeat(20),
            thumbnailUrl = "https://i.ytimg.com/vi/video$index/hqdefault.jpg?" + "sqp=x".repeat(60),
            channelName = "Channel $index",
            channelId = "UC" + "c".repeat(22),
            isMusic = index % 4 == 0,
            isShort = index % 5 == 0,
            isLocal = index % 50 == 0,
        )

    private fun seed(count: Int): List<WatchHistoryEntity> = runBlocking { (0 until count).map(::row).also { dao.upsertAll(it) } }

    @Test
    fun readsEveryRowNewestFirst() {
        val rows = seed(5_000)

        val read = runBlocking { dao.readHistory() }

        val expected = rows.sortedWith(compareByDescending<WatchHistoryEntity> { it.timestamp }.thenBy { it.videoId })
        assertEquals(expected.map { it.videoId }, read.map { it.videoId })
    }

    @Test
    fun filtersMatchTheOldWholeTableQueries() {
        val rows = seed(5_000)

        val videos = runBlocking { dao.readHistory(isMusic = 0, isLocal = 0) }
        val local = runBlocking { dao.readHistory(isLocal = 1) }
        val progress = runBlocking { dao.readProgress(isMusic = 0, isLocal = 0) }

        assertEquals(rows.count { !it.isMusic && !it.isLocal }, videos.size)
        assertEquals(rows.count { it.isLocal }, local.size)
        assertEquals(videos.map { it.videoId }, progress.map { it.videoId })
    }

    @Test
    fun readsInFullWhileWritesLand() {
        seed(5_000)

        val read =
            runBlocking {
                val writes =
                    async(Dispatchers.IO) {
                        repeat(200) { dao.upsert(row(it).copy(timestamp = 9_000_000L + it)) }
                    }
                val result = async(Dispatchers.IO) { dao.readHistory() }
                writes.await()
                result.await()
            }

        assertEquals(read.size, read.map { it.videoId }.toSet().size)
    }

    @Test
    fun recentVideoHistoryIsBounded() {
        seed(5_000)

        val recent = runBlocking { dao.getRecentVideoHistory(limit = 40, includeShorts = false) }

        assertEquals(40, recent.size)
        assertEquals(true, recent.none { it.isShort || it.isMusic || it.isLocal })
    }
}
