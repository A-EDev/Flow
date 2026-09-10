package io.github.aedev.flow.ui.screens.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.ResponseContext
import io.github.aedev.flow.innertube.models.Thumbnail
import io.github.aedev.flow.innertube.models.Thumbnails
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.MergedPlayback
import io.github.aedev.flow.player.stream.PlaybackFailure
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModelHarness.Companion.video
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Pins what one resolved step lands on: the state the screen holds afterwards, and the order the
 * player manager, the stream hand-off and the secondary loaders are entered in.
 *
 * The collaborators are mocks so the sequence itself is what is asserted; the state assertions are
 * the reducers' output seen through the flow the ViewModel shares with the applier. What each
 * reducer writes field by field is pinned in `PlayerPlaybackReducersTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionApplierTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var harness: VideoPlayerViewModelHarness

    private val uiState = MutableStateFlow(VideoPlayerUiState())
    private val playbackPreparer: PlaybackPreparer = mockk(relaxed = true)
    private val secondaryMetadata: PlayerSecondaryMetadataLoader = mockk(relaxed = true)
    private val liveChat: LiveChatController = mockk(relaxed = true)

    private val enteredUpcoming = mutableListOf<Triple<String, Long?, List<Video>>>()
    private var tryEnterUpcomingResult = false
    private var tryEnterUpcomingCalls = 0

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        harness = VideoPlayerViewModelHarness(testDispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        harness.close()
    }

    private fun CoroutineScope.applier(): PlaybackSessionApplier =
        PlaybackSessionApplier(
            context = harness.context,
            uiState = uiState,
            isLoadCurrent = { token -> token == CURRENT_TOKEN },
            playbackPreparer = playbackPreparer,
            streamPreparer = PlaybackStreamPreparer(),
            secondaryMetadata = secondaryMetadata,
            liveChat = liveChat,
            repository = harness.repository,
            viewHistory = harness.viewHistory,
            playerPreferences = harness.playerPreferences,
            sponsorBlockRepository = harness.sponsorBlockRepository,
            videoDownloadManager = harness.videoDownloadManager,
            offlineSubtitleStore = harness.offlineSubtitleStore,
            playerManager = harness.playerManager,
            scope = this,
            networkDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            enterUpcoming = { videoId, releaseMs, relatedVideos, _ ->
                enteredUpcoming += Triple(videoId, releaseMs, relatedVideos)
                true
            },
            tryEnterUpcoming = { _, _, _ ->
                tryEnterUpcomingCalls += 1
                tryEnterUpcomingResult
            },
        )

    @Test
    fun `a merged result arms autoplay, writes the streams, then loads the channel and the related lane`() =
        runTest(testDispatcher) {
            val streamInfo = mockk<StreamInfo>(relaxed = true)
            val related = listOf(video("rel_1"))

            applier().apply(merged(streamInfo, mergedPlayback(), relatedVideos = related), load())
            advanceUntilIdle()

            val state = uiState.value
            assertThat(state.streamInfo).isSameInstanceAs(streamInfo)
            assertThat(state.isLoading).isFalse()
            assertThat(state.savedPosition).isEqualTo(5_000L)
            assertThat(state.relatedVideos.map { it.id }).containsExactly("rel_1")

            coVerifyOrder {
                harness.playerManager.setAutoplayCandidates(VIDEO_ID, related, true)
                playbackPreparer.prepareMergedStreams(VIDEO_ID, any<ResolvedPlayback.Merged>(), any(), any())
                secondaryMetadata.loadChannelMetadata(VIDEO_ID, any(), any(), any(), CURRENT_TOKEN)
                secondaryMetadata.loadRelatedVideos(VIDEO_ID, related, CURRENT_TOKEN)
            }
        }

    @Test
    fun `merged upcoming content writes the countdown and hands nothing to the player`() =
        runTest(testDispatcher) {
            val step =
                merged(mockk(relaxed = true), mergedPlayback(), isUpcomingContent = true, upcomingReleaseTimeMs = 1_700L)

            applier().apply(step, load())
            advanceUntilIdle()

            assertThat(uiState.value.isUpcoming).isTrue()
            assertThat(uiState.value.upcomingReleaseTimeMs).isEqualTo(1_700L)
            coVerify(exactly = 0) { playbackPreparer.prepareMergedStreams(any(), any<ResolvedPlayback.Merged>(), any(), any()) }
            coVerify(exactly = 0) { secondaryMetadata.loadChannelMetadata(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { liveChat.start(any()) }
        }

    @Test
    fun `a merged live stream starts the chat and refreshes the live watch metadata`() =
        runTest(testDispatcher) {
            applier().apply(merged(mockk(relaxed = true), mergedPlayback(isLiveStream = true)), load())
            advanceUntilIdle()

            coVerifyOrder {
                liveChat.start(VIDEO_ID)
                secondaryMetadata.refreshLiveWatchMetadata(VIDEO_ID, any(), CURRENT_TOKEN)
            }
            coVerify(exactly = 0) { secondaryMetadata.loadRelatedVideos(any(), any(), any()) }
        }

    @Test
    fun `a merged result that needs a SponsorBlock backfill saves the segments and publishes them`() =
        runTest(testDispatcher) {
            val segments = listOf(segment())
            coEvery { harness.sponsorBlockRepository.getSegments(VIDEO_ID) } returns segments
            coEvery { harness.sponsorBlockRepository.serializeSegments(segments) } returns "[segments]"

            applier().apply(merged(mockk(relaxed = true), mergedPlayback(), backfillNeeded = true), load())
            advanceUntilIdle()

            coVerify(exactly = 1) { harness.videoDownloadManager.saveSponsorBlockData(VIDEO_ID, "[segments]") }
            assertThat(uiState.value.offlineSponsorBlockSegments).isEqualTo(segments)
        }

    @Test
    fun `a VOD from InnerTube arms the session, publishes autoplay and prepares the streams in order`() =
        runTest(testDispatcher) {
            coEvery { playbackPreparer.applyAutoplayCandidates(any(), any()) } returns true
            val related = listOf(video("rel_1"))

            applier().apply(vodStep(relatedVideos = related), load())
            advanceUntilIdle()

            val state = uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.videoStream).isNotNull()
            assertThat(state.availableQualities).contains(VideoQuality.Q_1080P)
            assertThat(state.relatedVideos.map { it.id }).containsExactly("rel_1")

            coVerifyOrder {
                playbackPreparer.beginSession(VIDEO_ID, "InnerTube title", "InnerTube channel", any())
                playbackPreparer.applyAutoplayCandidates(VIDEO_ID, related)
                secondaryMetadata.loadRelatedVideos(VIDEO_ID, related, CURRENT_TOKEN)
                secondaryMetadata.loadChannelMetadata(VIDEO_ID, null, "UC_innertube", any(), CURRENT_TOKEN)
                playbackPreparer.prepareVodStreams(VIDEO_ID, any(), any(), 0L, any())
            }
            verify { GlobalPlayerState.setCurrentVideo(match { it.id == VIDEO_ID && it.title == "InnerTube title" }) }
        }

    @Test
    fun `a VOD whose preparation throws asks the premiere check first and then writes the error`() =
        runTest(testDispatcher) {
            coEvery { playbackPreparer.prepareVodStreams(any(), any(), any(), any(), any()) } throws
                RuntimeException("prepare failed")

            applier().apply(vodStep(), load())
            advanceUntilIdle()

            assertThat(tryEnterUpcomingCalls).isEqualTo(1)
            assertThat(uiState.value.isLoading).isFalse()
            assertThat(uiState.value.error).isEqualTo("res:${R.string.error_generic}")
        }

    @Test
    fun `a VOD failure the premiere check claims leaves the error alone`() =
        runTest(testDispatcher) {
            tryEnterUpcomingResult = true
            coEvery { playbackPreparer.prepareVodStreams(any(), any(), any(), any(), any()) } throws
                RuntimeException("prepare failed")

            applier().apply(vodStep(), load())
            advanceUntilIdle()

            assertThat(tryEnterUpcomingCalls).isEqualTo(1)
            assertThat(uiState.value.error).isNull()
        }

    @Test
    fun `a live stream that starts loads the channel metadata and arms the chat`() =
        runTest(testDispatcher) {
            coEvery { playbackPreparer.prepareLiveStreams(any(), any(), any(), any(), any()) } returns true

            applier().apply(liveStep(), load())
            advanceUntilIdle()

            assertThat(uiState.value.hlsUrl).isEqualTo(LIVE_HLS_URL)
            assertThat(uiState.value.isLive).isTrue()
            coVerifyOrder {
                playbackPreparer.beginSession(VIDEO_ID, any(), any(), any())
                playbackPreparer.applyAutoplayCandidates(VIDEO_ID, any())
                playbackPreparer.prepareLiveStreams(VIDEO_ID, LIVE_HLS_URL, any(), any(), any())
                secondaryMetadata.loadChannelMetadata(VIDEO_ID, null, "UC_innertube", any(), CURRENT_TOKEN)
                liveChat.start(VIDEO_ID)
                secondaryMetadata.refreshLiveWatchMetadata(VIDEO_ID, any(), CURRENT_TOKEN)
            }
        }

    @Test
    fun `a live stream the player refuses stops before the channel metadata`() =
        runTest(testDispatcher) {
            coEvery { playbackPreparer.prepareLiveStreams(any(), any(), any(), any(), any()) } returns false

            applier().apply(liveStep(), load())
            advanceUntilIdle()

            assertThat(uiState.value.hlsUrl).isEqualTo(LIVE_HLS_URL)
            coVerify(exactly = 0) { secondaryMetadata.loadChannelMetadata(any(), any(), any(), any(), any()) }
            verify(exactly = 0) { liveChat.start(any()) }
            coVerify(exactly = 0) { secondaryMetadata.refreshLiveWatchMetadata(any(), any(), any()) }
        }

    @Test
    fun `a failure writes the error and leaves a retryable video unmarked`() =
        runTest(testDispatcher) {
            val related = listOf(video("rel_1"))

            applier().apply(
                ResolvedPlayback.Failed(PlaybackFailure.EXTRACTION, RuntimeException("newpipe unavailable"), related),
                load(),
            )
            advanceUntilIdle()

            val state = uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.error).isEqualTo("res:${R.string.error_generic}")
            assertThat(state.errorHint).isEqualTo("RuntimeException: newpipe unavailable")
            assertThat(state.relatedVideos.map { it.id }).containsExactly("rel_1")
            coVerify(exactly = 0) { harness.playerPreferences.markVideoUnplayable(any()) }
        }

    @Test
    fun `a step from a superseded load writes nothing`() =
        runTest(testDispatcher) {
            applier().apply(
                ResolvedPlayback.Failed(PlaybackFailure.EXTRACTION, RuntimeException("boom"), emptyList()),
                load(token = CURRENT_TOKEN + 1L),
            )
            advanceUntilIdle()

            assertThat(uiState.value).isEqualTo(VideoPlayerUiState())
        }

    @Test
    fun `a ready local copy is written and prepared from the saved position`() =
        runTest(testDispatcher) {
            val segments = listOf(segment())

            applier().apply(ResolvedPlayback.LocalCopyReady("/tmp/a.mp4", segments), load())
            advanceUntilIdle()

            assertThat(uiState.value.localFilePath).isEqualTo("/tmp/a.mp4")
            assertThat(uiState.value.localFileVideoId).isEqualTo(VIDEO_ID)
            coVerify(exactly = 1) {
                playbackPreparer.prepareLocalMedia(VIDEO_ID, "/tmp/a.mp4", segments, 0L, emptyList(), any())
            }
        }

    @Test
    fun `an upcoming step is handed to the premiere entry with what the load resolved`() =
        runTest(testDispatcher) {
            val related = listOf(video("rel_1"))

            applier().apply(ResolvedPlayback.Upcoming(related, releaseTimeMs = 2_500L), load())
            advanceUntilIdle()

            assertThat(enteredUpcoming).containsExactly(Triple(VIDEO_ID, 2_500L, related))
        }

    @Test
    fun `related metadata reaches the autoplay queue and the lane together`() =
        runTest(testDispatcher) {
            uiState.value = VideoPlayerUiState(cachedVideo = video(VIDEO_ID))
            val related = listOf(video("rel_1"))

            applier().applySecondary(SecondaryMetadata.Related(VIDEO_ID, CURRENT_TOKEN, related))
            advanceUntilIdle()

            coVerify(exactly = 1) { harness.playerManager.setAutoplayCandidates(VIDEO_ID, related, true) }
            assertThat(uiState.value.relatedVideos.map { it.id }).containsExactly("rel_1")
        }

    private fun load(token: Long = CURRENT_TOKEN): LoadContext = LoadContext(VIDEO_ID, token)

    private fun segment(): SponsorBlockSegment =
        SponsorBlockSegment(category = "sponsor", segment = listOf(0f, 1f), uuid = "uuid_1", actionType = "skip")

    private fun merged(
        streamInfo: StreamInfo,
        streams: MergedPlayback,
        relatedVideos: List<Video> = emptyList(),
        isUpcomingContent: Boolean = false,
        upcomingReleaseTimeMs: Long? = null,
        backfillNeeded: Boolean = false,
    ): ResolvedPlayback.Merged =
        ResolvedPlayback.Merged(
            streamInfo = streamInfo,
            streams = streams,
            relatedVideos = relatedVideos,
            savedPositionMs = 5_000L,
            autoplayEnabled = true,
            offlineSegments = null,
            sponsorBlockBackfillNeeded = backfillNeeded,
            isUpcomingContent = isUpcomingContent,
            upcomingReleaseTimeMs = upcomingReleaseTimeMs,
            resumeOverrideRequested = false,
        )

    private fun mergedPlayback(isLiveStream: Boolean = false): MergedPlayback =
        MergedPlayback(
            videoStreams = emptyList(),
            audioStreams = emptyList(),
            availableQualities = emptyList(),
            selectedVideoStream = null,
            selectedAudioStream = null,
            subtitles = emptyList(),
            chapters = emptyList(),
            streamSizes = emptyMap(),
            innerTubeVideoFormats = emptyList(),
            innerTubeAudioFormats = emptyList(),
            hlsUrl = null,
            dashManifestUrl = null,
            isLiveType = isLiveStream,
            isLiveStream = isLiveStream,
            hasPlayableContent = true,
            localFilePath = null,
            sabrInfo = null,
            preferSabr = false,
            preferredQuality = VideoQuality.AUTO,
            preferredCodecKey = "auto",
        )

    private fun vodStep(relatedVideos: List<Video> = emptyList()): ResolvedPlayback.VodFromInnerTube =
        ResolvedPlayback.VodFromInnerTube(
            result = extraction(),
            relatedVideos = relatedVideos,
            preferredQuality = VideoQuality.Q_1080P,
            preferredAudioLanguage = "original",
            preferredCodecKey = "auto",
            resumePositionOverrideMs = null,
            lateStreamInfo = null,
            streamError = null,
        )

    private fun liveStep(): ResolvedPlayback.Live =
        ResolvedPlayback.Live(
            result = extraction(isLive = true, liveHlsUrl = LIVE_HLS_URL),
            relatedVideos = emptyList(),
            lateStreamInfo = null,
        )

    private fun extraction(
        isLive: Boolean = false,
        liveHlsUrl: String? = null,
    ): InnerTubeVideoStreamExtractor.VideoExtractionResult =
        InnerTubeVideoStreamExtractor.VideoExtractionResult(
            videoFormats = fakeVideoFormats(),
            audioFormats = fakeAudioFormats(),
            playerResponse = playerResponse(),
            usedClient = YouTubeClient.WEB,
            sabrInfo = null,
            isLive = isLive,
            liveHlsUrl = liveHlsUrl,
            liveDashUrl = null,
        )

    private fun playerResponse(): PlayerResponse =
        PlayerResponse(
            responseContext = ResponseContext(visitorData = null, serviceTrackingParams = null),
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            playerConfig = null,
            streamingData = null,
            videoDetails =
                PlayerResponse.VideoDetails(
                    videoId = VIDEO_ID,
                    title = "InnerTube title",
                    author = "InnerTube channel",
                    channelId = "UC_innertube",
                    lengthSeconds = "300",
                    thumbnail = Thumbnails(listOf(Thumbnail(url = "https://example.invalid/it.jpg", width = 1280, height = 720))),
                ),
            playbackTracking = null,
        )

    private companion object {
        const val VIDEO_ID = "vid_applier"
        const val CURRENT_TOKEN = 7L
        const val LIVE_HLS_URL = "https://example.invalid/live.m3u8"
    }
}
