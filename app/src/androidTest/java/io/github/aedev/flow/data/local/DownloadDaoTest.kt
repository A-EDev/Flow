package io.github.aedev.flow.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun updatingADownloadRowKeepsItsFiles() =
        runTest {
            val dao = database.downloadDao()
            dao.insertDownload(download("v1", "First"))
            dao.insertItems(listOf(item("v1", "/a.mp4")))

            dao.insertDownload(download("v1", "Renamed"))

            val stored = dao.getDownloadWithItems("v1")!!
            assertEquals("Renamed", stored.download.title)
            assertEquals(1, stored.items.size)
        }

    @Test
    fun twoDownloadsCannotClaimOneFile() =
        runTest {
            val dao = database.downloadDao()
            dao.insertDownload(download("v1", "One"))
            dao.insertDownload(download("v2", "Two"))
            dao.insertItems(listOf(item("v1", "/same.mp4")))

            try {
                dao.insertItems(listOf(item("v2", "/same.mp4")))
                fail("a second download took over the first one's file")
            } catch (_: SQLiteConstraintException) {
            }

            assertEquals(1, dao.getDownloadWithItems("v1")!!.items.size)
        }

    @Test
    fun replacingADownloadDropsOnlyItsOwnEarlierFiles() =
        runTest {
            val dao = database.downloadDao()
            dao.replaceDownload(download("v1", "One"), listOf(item("v1", "/one-720.mp4")))
            dao.replaceDownload(download("v2", "Two"), listOf(item("v2", "/two.mp4")))

            dao.replaceDownload(download("v1", "One"), listOf(item("v1", "/one-1080.mp4")))

            assertEquals(listOf("/one-1080.mp4"), dao.getDownloadWithItems("v1")!!.items.map { it.filePath })
            assertTrue(dao.getDownloadWithItems("v2")!!.items.isNotEmpty())
        }

    private fun download(
        id: String,
        title: String,
    ) = DownloadEntity(videoId = id, title = title, uploader = "Uploader")

    private fun item(
        id: String,
        path: String,
    ) = DownloadItemEntity(
        videoId = id,
        fileType = DownloadFileType.VIDEO,
        fileName = path.substringAfterLast('/'),
        filePath = path,
        status = DownloadItemStatus.COMPLETED,
    )
}
