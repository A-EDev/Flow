package io.github.aedev.flow.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.VideoDao
import io.github.aedev.flow.data.local.entity.VideoEntity
import kotlinx.coroutines.test.runTest
import org.junit.AssumptionViolatedException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs the real SQL: a song copied from a music page must not erase what the video side stored. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class VideoMetadataMergeTest {
    /**
     * Runs [block] against a fresh in-memory database. SQLite is a native library Robolectric loads
     * on first use, including on close; where it cannot load (the Linux CI image) the test is
     * skipped, like the suite's other environment-bound tests, instead of failing.
     */
    private fun withDatabase(block: suspend (VideoDao) -> Unit) =
        runTest {
            try {
                val database =
                    Room
                        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                        .allowMainThreadQueries()
                        .build()
                try {
                    block(database.videoDao())
                } finally {
                    database.close()
                }
            } catch (missing: LinkageError) {
                throw AssumptionViolatedException("SQLite unavailable here: $missing")
            }
        }

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
        withDatabase { dao ->
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
        withDatabase { dao ->
            dao.insertVideo(entity(duration = 0))

            dao.mergeMetadata(listOf(entity(title = "Holocene (Live)", duration = 240, viewCount = 12L)))

            val stored = dao.getVideo("dQw4w9WgXcQ")!!
            assertThat(stored.title).isEqualTo("Holocene (Live)")
            assertThat(stored.duration).isEqualTo(240)
            assertThat(stored.viewCount).isEqualTo(12L)
        }

    @Test
    fun `an unknown song is inserted as given`() =
        withDatabase { dao ->
            dao.mergeMetadata(listOf(entity(duration = 226)))

            assertThat(dao.getVideo("dQw4w9WgXcQ")?.duration).isEqualTo(226)
        }
}
