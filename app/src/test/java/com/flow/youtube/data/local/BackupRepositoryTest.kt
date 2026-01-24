package com.flow.youtube.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.flow.youtube.data.local.dao.PlaylistDao
import com.flow.youtube.data.local.dao.VideoDao
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.VideoEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import androidx.room.withTransaction
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val contentResolver: ContentResolver = mockk()
    private val viewHistory: ViewHistory = mockk()
    private val searchHistoryRepo: SearchHistoryRepository = mockk()
    private val subscriptionRepo: SubscriptionRepository = mockk()
    private val appDatabase: AppDatabase = mockk()
    private val playlistDao: PlaylistDao = mockk()
    private val videoDao: VideoDao = mockk()

    private lateinit var repository: BackupRepository

    @Before
    fun setup() {
        every { context.contentResolver } returns contentResolver
        
        mockkObject(ViewHistory)
        every { ViewHistory.getInstance(any()) } returns viewHistory
        
        mockkObject(SubscriptionRepository)
        every { SubscriptionRepository.getInstance(any()) } returns subscriptionRepo
        
        mockkObject(AppDatabase)
        every { AppDatabase.getDatabase(any()) } returns appDatabase
        every { appDatabase.playlistDao() } returns playlistDao
        every { appDatabase.videoDao() } returns videoDao
        
        // Mock constructor-created repo
        mockkConstructor(SearchHistoryRepository::class)
        coEvery { anyConstructed<SearchHistoryRepository>().saveSearchQuery(any(), any()) } just Runs
        every { anyConstructed<SearchHistoryRepository>().getSearchHistoryFlow() } returns flowOf(emptyList())

        // Mock Room withTransaction
        mockkStatic("androidx.room.RoomDatabaseKt")
        val transactionSlot = slot<suspend () -> Any>()
        coEvery { appDatabase.withTransaction(capture(transactionSlot)) } coAnswers {
            transactionSlot.captured.invoke()
        }

        repository = BackupRepository(context)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `exportData writes JSON to stream`() = runTest {
        val uri = mockk<Uri>()
        val outputStream = ByteArrayOutputStream()
        
        every { contentResolver.openOutputStream(uri) } returns outputStream
        every { viewHistory.getAllHistory() } returns flowOf(emptyList())
        every { subscriptionRepo.getAllSubscriptions() } returns flowOf(emptyList())
        coEvery { playlistDao.getAllPlaylists() } returns flowOf(emptyList())
        coEvery { playlistDao.getAllPlaylistVideoCrossRefs() } returns emptyList()
        coEvery { videoDao.getAllVideos() } returns emptyList()

        val result = repository.exportData(uri)
        
        assertThat(result.isSuccess).isTrue()
        verify { contentResolver.openOutputStream(uri) }
    }

    @Test
    fun `importData parses JSON and calls save methods`() = runTest {
        val uri = mockk<Uri>()
        // Provide all required fields to avoid GSON nullability issues in Kotlin
        val json = """{
            "version":1,
            "viewHistory":[{
                "videoId":"v1",
                "title":"T1",
                "thumbnailUrl":"thumb",
                "position":100,
                "duration":200,
                "timestamp":12345
            }]
        }"""
        val inputStream = ByteArrayInputStream(json.toByteArray())
        
        every { contentResolver.openInputStream(uri) } returns inputStream
        coEvery { viewHistory.savePlaybackPosition(any(), any(), any(), any(), any(), any(), any(), any()) } just Runs
        
        val result = repository.importData(uri)
        
        assertThat(result.isSuccess).isTrue()
        coVerify { viewHistory.savePlaybackPosition(videoId = "v1", title = "T1", position = 100L, duration = any(), thumbnailUrl = any(), channelName = any(), channelId = any(), isMusic = any()) }
    }
}
