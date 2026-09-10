package io.github.aedev.flow.ui.screens.player

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.video.OfflineSubtitleStore
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.utils.NetworkState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream

/**
 * Pins the sequence [PlaybackPreparer] drives on one player manager for each way playback can be
 * armed: the same instance, the same calls, in the same order, with the resume position and the
 * remembered speed the preferences ask for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPreparerTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val playerManager: EnhancedPlayerManager = mockk(relaxed = true)
    private val playerPreferences: PlayerPreferences = mockk(relaxed = true)
    private val offlineSubtitleStore: OfflineSubtitleStore = mockk(relaxed = true)

    private lateinit var preparer: PlaybackPreparer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(NetworkState)
        every { NetworkState.isOnWifi(any()) } returns true

        every { playerManager.isPreparedForPlayback(any()) } returns false
        every { playerManager.isCurrentQueueVideo(any()) } returns false
        every { playerPreferences.rememberPlaybackSpeed } returns flowOf(false)
        every { playerPreferences.playbackSpeed } returns flowOf(1f)
        every { playerPreferences.autoplayEnabled } returns flowOf(true)
        every { playerPreferences.videoCodecPriority } returns flowOf("auto")
        every { playerPreferences.defaultQualityWifi } returns flowOf(VideoQuality.Q_1080P)
        every { playerPreferences.defaultQualityCellular } returns flowOf(VideoQuality.Q_480P)
        coEvery { offlineSubtitleStore.load(any()) } returns emptyList()

        preparer = PlaybackPreparer(context, playerManager, playerPreferences, offlineSubtitleStore)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `merged streams are initialised, pushed at the resumed position and played`() =
        runTest(testDispatcher) {
            every { playerPreferences.rememberPlaybackSpeed } returns flowOf(true)
            every { playerPreferences.playbackSpeed } returns flowOf(1.75f)

            preparer.prepareMergedStreams(
                videoId = VIDEO_ID,
                streamInfo = streamInfo(durationSeconds = 120L),
                videoStream = null,
                audioStream = null,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                subtitles = emptyList(),
                savedPosition = 30_000L,
                fallbackDurationSeconds = 0L,
                localFilePath = null,
                offlineSegments = null,
                hlsUrl = null,
                isAdaptiveMode = true,
                resumeOverrideRequested = false,
                isCurrent = { true },
                preferredVideoCodec = "vp9",
            )

            coVerifyOrder {
                playerManager.initialize(context)
                playerManager.setStreams(
                    videoId = VIDEO_ID,
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                    durationSeconds = 120L,
                    dashManifestUrl = DASH_URL,
                    hlsUrl = null,
                    streamType = StreamType.VIDEO_STREAM,
                    startPosition = 30_000L,
                    sabrInfo = null,
                    itVideoFormats = emptyList(),
                    itAudioFormats = emptyList(),
                    preferredVideoCodec = "vp9",
                    preferSabr = false,
                    preferredLiveQualityHeight = 0,
                )
                playerManager.setPlaybackSpeed(1.75f)
                playerManager.play()
            }
            verify(exactly = 0) { playerManager.playLocalFile(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a downloaded copy of a resolved video plays through playLocalFile with the stored subtitles`() =
        runTest(testDispatcher) {
            val stored = listOf(mockk<SubtitlesStream>())
            coEvery { offlineSubtitleStore.load(VIDEO_ID) } returns stored

            preparer.prepareMergedStreams(
                videoId = VIDEO_ID,
                streamInfo = streamInfo(durationSeconds = 120L),
                videoStream = null,
                audioStream = null,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                subtitles = emptyList(),
                savedPosition = 30_000L,
                fallbackDurationSeconds = 0L,
                localFilePath = "/downloads/vid.mp4",
                offlineSegments = null,
                hlsUrl = null,
                isAdaptiveMode = true,
                resumeOverrideRequested = false,
                isCurrent = { true },
            )

            verifyOrder {
                playerManager.playLocalFile(
                    videoId = VIDEO_ID,
                    filePath = "/downloads/vid.mp4",
                    savedSegments = null,
                    preservePosition = 30_000L,
                    subtitles = stored,
                )
                playerManager.play()
            }
            coVerify(exactly = 0) {
                playerManager.setStreams(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            }
        }

    @Test
    fun `a live manifest is pushed from the live edge with the speed locked to 1x`() =
        runTest(testDispatcher) {
            val started =
                preparer.prepareLiveStreams(
                    videoId = VIDEO_ID,
                    hlsUrl = HLS_URL,
                    dashManifestUrl = DASH_URL,
                    subtitles = emptyList(),
                    isCurrent = { true },
                )

            assertThat(started).isTrue()
            coVerifyOrder {
                playerManager.setStreams(
                    videoId = VIDEO_ID,
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                    durationSeconds = 0L,
                    dashManifestUrl = DASH_URL,
                    hlsUrl = HLS_URL,
                    streamType = StreamType.LIVE_STREAM,
                    startPosition = 0L,
                    sabrInfo = null,
                    itVideoFormats = emptyList(),
                    itAudioFormats = emptyList(),
                    preferredVideoCodec = "auto",
                    preferSabr = false,
                    preferredLiveQualityHeight = VideoQuality.Q_1080P.height,
                )
                playerManager.setPlaybackSpeed(1.0f)
                playerManager.play()
            }
        }

    @Test
    fun `an already prepared live video is not pushed again and reports that it did not start`() =
        runTest(testDispatcher) {
            every { playerManager.isPreparedForPlayback(VIDEO_ID) } returns true

            val started =
                preparer.prepareLiveStreams(
                    videoId = VIDEO_ID,
                    hlsUrl = HLS_URL,
                    dashManifestUrl = null,
                    subtitles = emptyList(),
                    isCurrent = { true },
                )

            assertThat(started).isFalse()
            verify(exactly = 0) { playerManager.play() }
        }

    @Test
    fun `the InnerTube VOD path pushes the converted streams at the resumed position`() =
        runTest(testDispatcher) {
            preparer.prepareVodStreams(
                videoId = VIDEO_ID,
                videoStream = null,
                audioStream = null,
                videoStreams = emptyList(),
                audioStreams = emptyList(),
                subtitles = emptyList(),
                durationSeconds = 600L,
                savedPositionMs = 90_000L,
                resumeOverrideRequested = false,
                isAdaptiveMode = false,
                sabrInfo = null,
                itVideoFormats = emptyList(),
                itAudioFormats = emptyList(),
                preferredVideoCodec = "av1",
                preferredLiveQualityHeight = 1080,
                isCurrent = { true },
            )

            coVerifyOrder {
                playerManager.setStreams(
                    videoId = VIDEO_ID,
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                    durationSeconds = 600L,
                    dashManifestUrl = null,
                    hlsUrl = null,
                    streamType = StreamType.VIDEO_STREAM,
                    startPosition = 90_000L,
                    sabrInfo = null,
                    itVideoFormats = emptyList(),
                    itAudioFormats = emptyList(),
                    preferredVideoCodec = "av1",
                    preferSabr = false,
                    preferredLiveQualityHeight = 1080,
                )
                playerManager.play()
            }
            verify(exactly = 0) { playerManager.initialize(any()) }
        }

    @Test
    fun `local media is initialised, resumed from the saved position and played`() =
        runTest(testDispatcher) {
            preparer.prepareLocalMedia(
                videoId = VIDEO_ID,
                localFilePath = "/movies/clip.mp4",
                offlineSegments = null,
                savedPosition = 12_000L,
                subtitles = emptyList(),
                isCurrent = { true },
            )

            verifyOrder {
                playerManager.initialize(context)
                playerManager.playLocalFile(
                    videoId = VIDEO_ID,
                    filePath = "/movies/clip.mp4",
                    savedSegments = null,
                    preservePosition = 12_000L,
                    subtitles = emptyList(),
                )
                playerManager.play()
            }
        }

    @Test
    fun `a load that stopped being current never reaches the player`() =
        runTest(testDispatcher) {
            preparer.prepareLocalMedia(
                videoId = VIDEO_ID,
                localFilePath = "/movies/clip.mp4",
                offlineSegments = null,
                savedPosition = 12_000L,
                subtitles = emptyList(),
                isCurrent = { false },
            )

            verify(exactly = 0) { playerManager.initialize(any()) }
            verify(exactly = 0) { playerManager.playLocalFile(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { playerManager.play() }
        }

    @Test
    fun `beginSession arms the player and the media notification before any streams`() =
        runTest(testDispatcher) {
            preparer.beginSession(VIDEO_ID, title = "Title", channel = "Channel", thumbnail = "thumb")

            verifyOrder {
                playerManager.initialize(context)
                playerManager.startBackgroundService(VIDEO_ID, "Title", "Channel", "thumb")
            }
        }

    @Test
    fun `autoplay candidates are published with the preference the caller then reuses`() =
        runTest(testDispatcher) {
            every { playerPreferences.autoplayEnabled } returns flowOf(false)

            val enabled = preparer.applyAutoplayCandidates(VIDEO_ID, emptyList())

            assertThat(enabled).isFalse()
            verify(exactly = 1) {
                playerManager.setAutoplayCandidates(sourceVideoId = VIDEO_ID, videos = emptyList(), enabled = false)
            }
        }

    private fun streamInfo(durationSeconds: Long): StreamInfo {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.streamType } returns StreamType.VIDEO_STREAM
        every { info.duration } returns durationSeconds
        every { info.dashMpdUrl } returns DASH_URL
        return info
    }

    private companion object {
        const val VIDEO_ID = "vid_preparer"
        const val DASH_URL = "https://example.invalid/manifest.mpd"
        const val HLS_URL = "https://example.invalid/live.m3u8"
    }
}
