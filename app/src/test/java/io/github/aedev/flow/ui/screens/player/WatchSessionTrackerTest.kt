package io.github.aedev.flow.ui.screens.player

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Pins how a view is graded and when the related prewarm is allowed to spend a request: one
 * terminal signal per video, thresholds that ignore navigation noise, and a prewarm that never
 * runs twice for the same video.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchSessionTrackerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val viewHistory: ViewHistory = mockk(relaxed = true)
    private val repository: YouTubeRepository = mockk(relaxed = true)
    private val homeFeedCacheRepository: HomeFeedCacheRepository = mockk(relaxed = true)
    private val trackerScope = CoroutineScope(testDispatcher)

    private var relatedLane: List<Video> = emptyList()
    private var richVideo: Video? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(FlowNeuroEngine.Companion)
        every { FlowNeuroEngine.onVideoInteractionAsync(any(), any(), any(), any()) } just Runs
        coEvery { repository.getRelatedCandidates(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        trackerScope.cancel()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun tracker(): WatchSessionTracker =
        WatchSessionTracker(
            context = context,
            viewHistory = viewHistory,
            repository = repository,
            homeFeedCacheRepository = homeFeedCacheRepository,
            scope = trackerScope,
            networkDispatcher = testDispatcher,
            shortsEnabled = { true },
            relatedVideosFor = { relatedLane },
            richVideoFor = { richVideo },
        )

    private fun WatchSessionTracker.report(
        videoId: String,
        positionMs: Long,
        durationMs: Long = 120_000L,
    ) = savePlaybackPosition(
        videoId = videoId,
        positionMs = positionMs,
        durationMs = durationMs,
        title = "Title $videoId",
        thumbnailUrl = "https://example.invalid/$videoId.jpg",
        channelName = "Channel",
        channelId = "channel",
        isShort = false,
        isLocal = false,
    )

    @Test
    fun `an unknown duration earns no signal`() {
        assertThat(watchSignalFor(positionMs = 30_000L, durationMs = 0L)).isNull()
    }

    @Test
    fun `an instant bounce is navigation noise rather than a skip`() {
        assertThat(watchSignalFor(positionMs = 5_000L, durationMs = 120_000L)).isNull()
    }

    @Test
    fun `a real attempt abandoned under a fifth of the video is a skip`() {
        val signal = watchSignalFor(positionMs = 11_000L, durationMs = 120_000L)

        assertThat(signal?.type).isEqualTo(InteractionType.SKIPPED)
        assertThat(signal?.fractionWatched).isWithin(TOLERANCE).of(11_000f / 120_000f)
    }

    @Test
    fun `a fifth of the video or more is watched`() {
        val signal = watchSignalFor(positionMs = 24_000L, durationMs = 120_000L)

        assertThat(signal?.type).isEqualTo(InteractionType.WATCHED)
        assertThat(signal?.fractionWatched).isWithin(TOLERANCE).of(0.2f)
    }

    @Test
    fun `the session is graded once when the next video takes it over`() =
        runTest(testDispatcher) {
            val tracker = tracker()

            tracker.report("v1", positionMs = 30_000L)
            tracker.report("v1", positionMs = 60_000L)
            tracker.report("v2", positionMs = 1_000L)
            advanceUntilIdle()

            verify(exactly = 1) {
                FlowNeuroEngine.onVideoInteractionAsync(any(), match { it.id == "v1" }, InteractionType.WATCHED, any())
            }
        }

    @Test
    fun `finalising twice still reports the open session only once`() =
        runTest(testDispatcher) {
            val tracker = tracker()

            tracker.report("v1", positionMs = 30_000L)
            tracker.finalizeActiveSession()
            tracker.finalizeActiveSession()
            advanceUntilIdle()

            verify(exactly = 1) {
                FlowNeuroEngine.onVideoInteractionAsync(any(), match { it.id == "v1" }, any(), any())
            }
        }

    @Test
    fun `the rich video the screen still holds is graded instead of the session stub`() =
        runTest(testDispatcher) {
            richVideo = video("v1").copy(description = "rich")
            val tracker = tracker()

            tracker.report("v1", positionMs = 30_000L)
            tracker.finalizeActiveSession()
            advanceUntilIdle()

            verify(exactly = 1) {
                FlowNeuroEngine.onVideoInteractionAsync(any(), match { it.description == "rich" }, any(), any())
            }
        }

    @Test
    fun `an early position never spends a prewarm request`() =
        runTest(testDispatcher) {
            val tracker = tracker()

            tracker.report("v1", positionMs = 5_000L)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.getRelatedCandidates(any()) }
            coVerify(exactly = 0) { homeFeedCacheRepository.saveRelated(any(), any(), any()) }
        }

    @Test
    fun `the prewarm runs once per video and reuses the lane the screen already has`() =
        runTest(testDispatcher) {
            relatedLane = listOf(video("v2"), video("v1"), video("v2"))
            val tracker = tracker()

            tracker.report("v1", positionMs = 21_000L)
            tracker.report("v1", positionMs = 40_000L)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.getRelatedCandidates(any()) }
            coVerify(exactly = 1) {
                homeFeedCacheRepository.saveRelated(
                    "v1",
                    match<List<Video>> {
                        it.map { video -> video.id } ==
                            listOf("v2")
                    },
                    any(),
                )
            }
        }

    @Test
    fun `an empty lane falls back to one related request`() =
        runTest(testDispatcher) {
            coEvery { repository.getRelatedCandidates("v1") } returns listOf(video("v2"))
            val tracker = tracker()

            tracker.report("v1", positionMs = 21_000L)
            tracker.report("v1", positionMs = 40_000L)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.getRelatedCandidates("v1") }
            coVerify(exactly = 1) {
                homeFeedCacheRepository.saveRelated(
                    "v1",
                    match<List<Video>> {
                        it.map { video -> video.id } ==
                            listOf("v2")
                    },
                    any(),
                )
            }
        }

    private companion object {
        const val TOLERANCE = 0.0001f

        fun video(id: String): Video =
            Video(
                id = id,
                title = "Title $id",
                channelName = "Channel",
                channelId = "channel",
                thumbnailUrl = "https://example.invalid/$id.jpg",
                duration = 120,
                viewCount = 1L,
                uploadDate = "2026-01-01",
            )
    }
}
