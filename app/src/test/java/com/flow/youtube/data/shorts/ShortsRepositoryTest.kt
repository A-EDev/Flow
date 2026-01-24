package com.flow.youtube.data.shorts

import android.content.Context
import android.util.Log
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.InterestProfile
import com.flow.youtube.data.repository.YouTubeRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsRepositoryTest {

    private val context: Context = mockk(relaxed = true)
    private val youtubeRepository: YouTubeRepository = mockk()
    private val interestProfile: InterestProfile = mockk()
    private lateinit var repository: ShortsRepository

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        
        mockkObject(YouTubeRepository)
        every { YouTubeRepository.getInstance() } returns youtubeRepository
        
        mockkObject(InterestProfile)
        every { InterestProfile.getInstance(any()) } returns interestProfile

        // Mock filesDir for DataStore
        val filesDir = java.io.File("build/tmp/shorts_tests").apply { mkdirs() }
        every { context.filesDir } returns filesDir
        every { context.applicationContext } returns context

        repository = ShortsRepository.getInstance(context)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun createVideo(id: String, duration: Int = 30) = Video(
        id = id,
        title = "Short $id",
        channelName = "Channel",
        channelId = "channelId",
        thumbnailUrl = "thumb",
        duration = duration,
        viewCount = 1000L,
        uploadDate = "today",
        isShort = true,
        likeCount = 0
    )

    @Test
    fun `shuffleForSession produces deterministic results for same seed`() {
        val videos = listOf(createVideo("1"), createVideo("2"), createVideo("3"))
        
        val result1 = repository.shuffleForSession(videos, 123L)
        val result2 = repository.shuffleForSession(videos, 123L)
        val result3 = repository.shuffleForSession(videos, 456L)
        
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun `fetchShortsWithQuery filters by duration`() = runTest {
        val videos = listOf(
            createVideo("short", 30),
            createVideo("long", 120)
        )
        coEvery { youtubeRepository.searchVideos(any()) } returns Pair(videos, null)
        
        val result = repository.fetchShortsWithQuery("test")
        
        assertThat(result.map { it.id }).containsExactly("short")
    }
}
