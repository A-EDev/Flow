package io.github.aedev.flow.player.stream

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.utils.NetworkState
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Pins what [PlaybackLoadResolver] hands back for each way a load can end, with both extraction
 * stacks driven from the same fakes the player-screen characterisation harness uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLoadResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val repository: YouTubeRepository = mockk(relaxed = true)
    private val viewHistory: ViewHistory = mockk(relaxed = true)
    private val playerPreferences: PlayerPreferences = mockk(relaxed = true)
    private val videoDownloadManager: VideoDownloadManager = mockk(relaxed = true)
    private val sponsorBlockRepository: SponsorBlockRepository = mockk(relaxed = true)
    private val downloads = MutableStateFlow<List<DownloadedVideo>>(emptyList())

    private lateinit var resolver: PlaybackLoadResolver

    @Before
    fun setUp() {
        mockkObject(InnerTubeVideoStreamExtractor)
        coEvery { InnerTubeVideoStreamExtractor.extract(any(), any()) } returns null

        mockkObject(NetworkState)
        every { NetworkState.isOnline(any()) } returns true

        mockkObject(PlayerDiagnostics)
        every { PlayerDiagnostics.logWarning(any(), any()) } just Runs

        every { videoDownloadManager.downloadedVideos } returns downloads
        coEvery { videoDownloadManager.getSponsorBlockData(any()) } returns null
        every { playerPreferences.defaultQualityWifi } returns flowOf(VideoQuality.AUTO)
        every { playerPreferences.defaultQualityCellular } returns flowOf(VideoQuality.AUTO)
        every { playerPreferences.preferredAudioLanguage } returns flowOf("original")
        every { playerPreferences.videoCodecPriority } returns flowOf("auto")
        every { playerPreferences.autoplayEnabled } returns flowOf(true)
        every { viewHistory.getPlaybackPosition(any()) } returns flowOf(0L)
        coEvery { repository.getVideoStreamInfo(any()) } throws RuntimeException("newpipe unavailable")
        every { repository.getRelatedVideosFromStreamInfo(any()) } returns emptyList()

        resolver =
            PlaybackLoadResolver(
                context = context,
                repository = repository,
                viewHistory = viewHistory,
                playerPreferences = playerPreferences,
                videoDownloadManager = videoDownloadManager,
                sponsorBlockRepository = sponsorBlockRepository,
                networkDispatcher = testDispatcher,
                ioDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `the direct ladder hands over NewPipe metadata before the merged result`() =
        runTest(testDispatcher) {
            coEvery { repository.getVideoStreamInfo(VIDEO_ID) } returns playableStreamInfo()
            coEvery { InnerTubeVideoStreamExtractor.extract(any(), any()) } coAnswers { awaitCancellation() }

            val steps = resolveSteps().second
            advanceUntilIdle()

            assertThat(steps).hasSize(2)
            assertThat(steps.first()).isInstanceOf(ResolvedPlayback.PrimaryMetadata::class.java)
            val merged = steps.last() as ResolvedPlayback.Merged
            assertThat(merged.streams.hasPlayableContent).isTrue()
            assertThat(merged.streams.isLiveType).isFalse()
            assertThat(merged.isUpcomingContent).isFalse()
            assertThat(merged.savedPositionMs).isEqualTo(0L)
            assertThat(merged.autoplayEnabled).isTrue()
            coVerify(exactly = 1) { repository.getVideoStreamInfo(VIDEO_ID) }
        }

    @Test
    fun `a forced SABR reload retries the full client ladder once before giving up`() =
        runTest(testDispatcher) {
            val steps = resolveSteps(escalateToSabr = true).second
            advanceUntilIdle()

            val failure = steps.single() as ResolvedPlayback.Failed
            assertThat(failure.failure).isEqualTo(PlaybackFailure.EXTRACTION)
            assertThat(failure.cause).isNull()
            assertThat(failure.relatedVideos).isNull()
            coVerify(exactly = 1) { InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = true) }
            coVerify(exactly = 1) { InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = false) }
            // NewPipe is started by the reload and only cancelled after its first attempt.
            coVerify(exactly = 1) { repository.getVideoStreamInfo(VIDEO_ID) }
        }

    @Test
    fun `a downloaded copy is handed over before resolution and ends the load when offline`() =
        runTest(testDispatcher) {
            val file = temporaryFolder.newFile("$VIDEO_ID.mp4")
            downloads.value = listOf(DownloadedVideo(video = downloadedVideo(), filePath = file.absolutePath))
            every { NetworkState.isOnline(any()) } returns false

            val steps = resolveSteps().second
            advanceUntilIdle()

            val local = steps.single() as ResolvedPlayback.LocalCopyReady
            assertThat(local.localFilePath).isEqualTo(file.absolutePath)
            assertThat(local.clearStreamInfo).isFalse()
            coVerify(exactly = 0) { InnerTubeVideoStreamExtractor.extract(VIDEO_ID, forceSabr = true) }
        }

    @Test
    fun `a video the premiere lookup flags resolves to a countdown rather than an error`() =
        runTest(testDispatcher) {
            val steps = resolveSteps(upcoming = UpcomingPremiere(isUpcoming = true, scheduledStartMs = 1_234L)).second
            advanceUntilIdle()

            val upcoming = steps.single() as ResolvedPlayback.Upcoming
            assertThat(upcoming.releaseTimeMs).isEqualTo(1_234L)
            assertThat(upcoming.relatedVideos).isEmpty()
        }

    @Test
    fun `a load that stopped being current hands back nothing`() =
        runTest(testDispatcher) {
            val steps = resolveSteps(isCurrent = { false }).second
            advanceUntilIdle()

            assertThat(steps).isEmpty()
        }

    @Test
    fun `cancelling the load between attempts stops the ladder`() =
        runTest(testDispatcher) {
            val gate = CompletableDeferred<StreamInfo?>()
            coEvery { repository.getVideoStreamInfo(VIDEO_ID) } coAnswers { gate.await() }
            coEvery { InnerTubeVideoStreamExtractor.extract(any(), any()) } coAnswers { awaitCancellation() }

            val (job, steps) = resolveSteps()
            runCurrent()
            coVerify(exactly = 1) { repository.getVideoStreamInfo(VIDEO_ID) }

            job.cancel()
            advanceUntilIdle()

            assertThat(steps).isEmpty()
            coVerify(exactly = 1) { repository.getVideoStreamInfo(VIDEO_ID) }
        }

    private fun TestScope.resolveSteps(
        escalateToSabr: Boolean = false,
        isCurrent: () -> Boolean = { true },
        upcoming: UpcomingPremiere = UpcomingPremiere.NOT_UPCOMING,
    ): Pair<Job, List<ResolvedPlayback>> {
        val steps = mutableListOf<ResolvedPlayback>()
        val job =
            launch(testDispatcher) {
                resolver.resolve(
                    scope = this,
                    request =
                        PlaybackResolutionRequest(
                            videoId = VIDEO_ID,
                            isWifi = true,
                            escalateToSabr = escalateToSabr,
                            resumePositionOverrideMs = null,
                            allowShorts = true,
                        ),
                    isCurrent = isCurrent,
                    resolveUpcoming = { _, _ -> upcoming },
                    onStep = { steps += it },
                )
            }
        return job to steps
    }

    private fun playableStreamInfo(): StreamInfo {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.streamType } returns StreamType.VIDEO_STREAM
        every { info.dashMpdUrl } returns "https://example.invalid/manifest.mpd"
        every { info.hlsUrl } returns null
        every { info.duration } returns 120L
        return info
    }

    private fun downloadedVideo(): Video =
        Video(
            id = VIDEO_ID,
            title = "Downloaded",
            channelName = "Channel",
            channelId = "channel",
            thumbnailUrl = "",
            duration = 120,
            viewCount = 0L,
            uploadDate = "2026-01-01",
        )

    private companion object {
        const val VIDEO_ID = "vid_resolver"
    }
}
