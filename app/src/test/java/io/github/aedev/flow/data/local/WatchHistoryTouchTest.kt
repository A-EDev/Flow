package io.github.aedev.flow.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.WatchHistoryDao
import io.github.aedev.flow.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class WatchHistoryTouchTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: WatchHistoryDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.watchHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun touchingAnEntryKeepsStoredProgressAndMetadataWhenTheOpenHasNoMetadata() =
        runBlocking {
            val existing = entry("video_1", position = 48_000L, duration = 180_000L, title = "Saved title")
            dao.upsert(existing)

            dao.insertIfAbsent(existing.copy(position = 0L, duration = 0L, title = "", thumbnailUrl = ""))
            touch(existing.videoId, timestamp = 2_000L)

            val touched = dao.getEntry(existing.videoId).first()!!
            assertThat(touched.position).isEqualTo(48_000L)
            assertThat(touched.duration).isEqualTo(180_000L)
            assertThat(touched.title).isEqualTo("Saved title")
            assertThat(touched.thumbnailUrl).isEqualTo("saved thumbnail")
            assertThat(touched.channelName).isEqualTo("Saved channel")
            assertThat(touched.channelId).isEqualTo("saved channel id")
            assertThat(touched.timestamp).isEqualTo(2_000L)
        }

    @Test
    fun touchingAnEntryAfterProgressSaveDoesNotResetItsPosition() =
        runBlocking {
            val stub = entry("video_2")
            dao.insertIfAbsent(stub)
            dao.upsert(stub.copy(position = 64_000L, duration = 200_000L, title = "Real title"))

            touch(stub.videoId, timestamp = 3_000L)

            val touched = dao.getEntry(stub.videoId).first()!!
            assertThat(touched.position).isEqualTo(64_000L)
            assertThat(touched.duration).isEqualTo(200_000L)
            assertThat(touched.title).isEqualTo("Real title")
        }

    private suspend fun touch(
        videoId: String,
        timestamp: Long,
    ) = dao.touchExistingEntry(
        videoId = videoId,
        duration = 0L,
        timestamp = timestamp,
        title = "",
        thumbnailUrl = "",
        channelName = "",
        channelId = "",
        isShort = false,
        isLocal = false,
    )

    private fun entry(
        videoId: String,
        position: Long = 0L,
        duration: Long = 0L,
        title: String = "",
    ) = WatchHistoryEntity(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = 1_000L,
        title = title,
        thumbnailUrl = "saved thumbnail",
        channelName = "Saved channel",
        channelId = "saved channel id",
        isMusic = false,
    )
}
