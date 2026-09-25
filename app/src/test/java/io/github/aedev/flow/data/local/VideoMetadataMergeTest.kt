package io.github.aedev.flow.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.VideoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs the real SQL: a song copied from a music page must not erase what the video side stored. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class VideoMetadataMergeTest {
    private val database =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    private val dao = database.videoDao()

    // Robolectric's native SQLite loads on developer machines but not on every CI image; there the
    // test is skipped rather than failed, like the suite's other environment-bound tests.
    @Before
    fun requireSqlite() {
        val failure = runCatching { database.openHelper.writableDatabase }.exceptionOrNull()
        // After a failed native load, later tests in the same JVM see NoClassDefFoundError instead.
        assumeTrue("Native SQLite unavailable: $failure", failure !is UnsatisfiedLinkError && failure !is NoClassDefFoundError)
    }

    @After
    fun close() = database.close()

    private fun entity(
        title: String = "Holocene",
        duration: Int = 0,
        viewCount: Long = 0L,
        uploadDate: String = "",
        timestamp: Long = 0L,
        description: String = "",
    ) = VideoEntity(
        id = "dQw4w9WgXcQ",
        title = title,
        channelName = "Bon Iver",
        channelId = "",
        thumbnailUrl = "",
        duration = duration,
        viewCount = viewCount,
        uploadDate = uploadDate,
        description = description,
        channelThumbnailUrl = "",
        timestamp = timestamp,
        isMusic = true,
    )

    @Test
    fun `blanks from a music copy keep what was known`() =
        runTest {
            dao.insertVideo(
                entity(duration = 226, viewCount = 9_000L, uploadDate = "13 years ago", timestamp = 1_000L, description = "Live"),
            )

            dao.mergeMetadata(listOf(entity(timestamp = 5_000L)))

            val stored = dao.getVideo("dQw4w9WgXcQ")!!
            assertThat(stored.duration).isEqualTo(226)
            assertThat(stored.viewCount).isEqualTo(9_000L)
            assertThat(stored.uploadDate).isEqualTo("13 years ago")
            assertThat(stored.description).isEqualTo("Live")
            assertThat(stored.timestamp).isEqualTo(1_000L)
        }

    @Test
    fun `real values still update the row`() =
        runTest {
            dao.insertVideo(entity(duration = 0))

            dao.mergeMetadata(listOf(entity(title = "Holocene (Live)", duration = 240, viewCount = 12L)))

            val stored = dao.getVideo("dQw4w9WgXcQ")!!
            assertThat(stored.title).isEqualTo("Holocene (Live)")
            assertThat(stored.duration).isEqualTo(240)
            assertThat(stored.viewCount).isEqualTo(12L)
        }

    @Test
    fun `an unknown song is inserted as given`() =
        runTest {
            dao.mergeMetadata(listOf(entity(duration = 226)))

            assertThat(dao.getVideo("dQw4w9WgXcQ")?.duration).isEqualTo(226)
        }
}
