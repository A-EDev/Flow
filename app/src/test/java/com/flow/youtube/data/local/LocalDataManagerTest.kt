package com.flow.youtube.data.local

import android.content.Context
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.ThemeMode
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LocalDataManagerTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var dataManager: LocalDataManager
    private lateinit var testFilesDir: File
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        testFilesDir = File("build/tmp/local_data_tests").apply { mkdirs() }
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        mockkStatic("com.flow.youtube.data.local.LocalDataManagerKt")
    }

    @After
    fun teardown() {
        unmockkAll()
        testFilesDir.deleteRecursively()
    }

    private fun setupDataManager() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(testFilesDir, "test_local_${java.util.UUID.randomUUID()}.preferences_pb") }
        )
        every { any<Context>().dataStore } returns testDataStore
        dataManager = LocalDataManager(context)
    }

    private fun createVideo(id: String) = Video(
        id = id,
        title = "Title $id",
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumb",
        duration = 100,
        viewCount = 1000L,
        uploadDate = "today",
        likeCount = 0
    )

    @Test
    fun `themeMode defaults to LIGHT and persists change`() = runTest(testDispatcher) {
        setupDataManager()
        assertThat(dataManager.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
        
        dataManager.setThemeMode(ThemeMode.DARK)
        
        assertThat(dataManager.themeMode.first()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `addToWatchHistory keeps history unique and capped`() = runTest(testDispatcher) {
        setupDataManager()
        val v1 = createVideo("1")
        val v2 = createVideo("2")
        
        dataManager.addToWatchHistory(v1)
        dataManager.addToWatchHistory(v2)
        dataManager.addToWatchHistory(v1) // Moves to front
        
        val history = dataManager.watchHistory.first()
        assertThat(history.size).isEqualTo(2)
        assertThat(history[0].id).isEqualTo("1")
        assertThat(history[1].id).isEqualTo("2")
    }

    @Test
    fun `toggleLike adds and removes video`() = runTest(testDispatcher) {
        setupDataManager()
        val v1 = createVideo("1")
        
        dataManager.toggleLike(v1)
        assertThat(dataManager.likedVideos.first()).hasSize(1)
        
        dataManager.toggleLike(v1)
        assertThat(dataManager.likedVideos.first()).isEmpty()
    }

    @Test
    fun `addSearchQuery caps history size`() = runTest(testDispatcher) {
        setupDataManager()
        for (i in 1..25) {
            dataManager.addSearchQuery("query $i")
        }
        
        val history = dataManager.searchHistory.first()
        assertThat(history.size).isAtMost(20)
    }
}
