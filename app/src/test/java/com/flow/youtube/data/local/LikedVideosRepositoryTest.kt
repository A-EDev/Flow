package com.flow.youtube.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LikedVideosRepositoryTest {

    private lateinit var testFilesDir: File
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0
        
        testFilesDir = File("build/tmp/liked_videos_tests").apply { mkdirs() }
        mockkStatic("com.flow.youtube.data.local.LikedVideosRepositoryKt")
    }

    @After
    fun teardown() {
        // Reset Singleton Instance using reflection
        try {
            val instanceField = LikedVideosRepository.Companion::class.java.getDeclaredField("INSTANCE")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (e: Exception) {
            // Fallback for different field placement
            try {
                val instanceField = LikedVideosRepository::class.java.getDeclaredField("INSTANCE")
                instanceField.isAccessible = true
                instanceField.set(null, null)
            } catch (e2: Exception) {}
        } finally {
            unmockkAll()
        }
        
        testFilesDir.deleteRecursively()
    }

    private fun setupRepository(scope: kotlinx.coroutines.CoroutineScope): LikedVideosRepository {
        val uniqueId = java.util.UUID.randomUUID().toString()
        val testSubDir = File(testFilesDir, uniqueId).apply { mkdirs() }
        
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(testSubDir, "test_liked_${java.util.UUID.randomUUID()}.preferences_pb") }
        )
        
        // No need for context mocking or singleton getInstance here
        val constructor = LikedVideosRepository::class.java.declaredConstructors.firstOrNull { it.parameterCount == 2 }
        constructor?.isAccessible = true
        return constructor?.newInstance(mockk<Context>(relaxed = true), testDataStore) as LikedVideosRepository
    }

    @Test
    fun `likeVideo adds video to DataStore and order list`() = runTest(testDispatcher) {
        val repository = setupRepository(this)
        val video = LikedVideoInfo(
            videoId = "v1",
            title = "Title 1",
            thumbnail = "thumb",
            channelName = "Channel"
        )
        
        repository.likeVideo(video)
        advanceUntilIdle()
        
        val liked = repository.getLikedVideosFlow().first()
        assertThat(liked).hasSize(1)
        assertThat(liked[0].videoId).isEqualTo("v1")
        
        val state = repository.getLikeState("v1").first()
        assertThat(state).isEqualTo("LIKED")
    }

    @Test
    fun `dislikeVideo removes video from liked list and sets state`() = runTest(testDispatcher) {
        val repository = setupRepository(this)
        val video = LikedVideoInfo(videoId = "v1", title = "T", thumbnail = "t", channelName = "c")
        repository.likeVideo(video)
        advanceUntilIdle()
        
        repository.dislikeVideo("v1")
        advanceUntilIdle()
        
        val liked = repository.getLikedVideosFlow().first()
        assertThat(liked).isEmpty()
        
        val state = repository.getLikeState("v1").first()
        assertThat(state).isEqualTo("DISLIKED")
    }

    @Test
    fun `order is preserved newest first`() = runTest(testDispatcher) {
        val repository = setupRepository(this)
        repository.likeVideo(LikedVideoInfo(videoId = "v1", title = "T1", thumbnail = "t1", channelName = "c1"))
        advanceUntilIdle()
        repository.likeVideo(LikedVideoInfo(videoId = "v2", title = "T2", thumbnail = "t2", channelName = "c2"))
        advanceUntilIdle()
        
        val liked = repository.getAllLikedVideos().first()
        assertThat(liked).hasSize(2)
        assertThat(liked[0].videoId).isEqualTo("v2")
        assertThat(liked[1].videoId).isEqualTo("v1")
    }
}
