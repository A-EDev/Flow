package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.error.VideoErrorMapper
import io.github.aedev.flow.player.stream.MergedPlayback
import io.github.aedev.flow.player.stream.ResolvedPlayback
import io.github.aedev.flow.ui.screens.player.SecondaryMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Pins the fields every playback outcome writes onto [VideoPlayerUiState]. These are the reducers
 * the ViewModel drives from `_uiState.update`, so a change here is a change to what the player
 * screen shows, whatever the ordering around it.
 */
class PlayerPlaybackReducersTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `a ready local copy replaces the path and clears the load`() {
        val segments = listOf(segment())
        val state = VideoPlayerUiState(isLoading = true, error = "boom", errorHint = "hint", isUpcoming = true)

        val next =
            state.applyLocalCopyReady(
                videoId = "vid_a",
                step = ResolvedPlayback.LocalCopyReady(localFilePath = "/tmp/a.mp4", offlineSegments = segments),
            )

        assertThat(next.localFilePath).isEqualTo("/tmp/a.mp4")
        assertThat(next.localFileVideoId).isEqualTo("vid_a")
        assertThat(next.offlineSponsorBlockSegments).isEqualTo(segments)
        assertThat(next.isLoading).isFalse()
        assertThat(next.error).isNull()
        assertThat(next.errorHint).isNull()
        assertThat(next.isUpcoming).isFalse()
        assertThat(next.upcomingReleaseTimeMs).isNull()
    }

    @Test
    fun `a ready local copy keeps the stream info unless the step asks for it to be cleared`() {
        val streamInfo = mockk<StreamInfo>(relaxed = true)
        val state = VideoPlayerUiState(streamInfo = streamInfo)

        val kept =
            state.applyLocalCopyReady("vid_a", ResolvedPlayback.LocalCopyReady("/tmp/a.mp4", null, clearStreamInfo = false))
        val cleared =
            state.applyLocalCopyReady("vid_a", ResolvedPlayback.LocalCopyReady("/tmp/a.mp4", null, clearStreamInfo = true))

        assertThat(kept.streamInfo).isSameInstanceAs(streamInfo)
        assertThat(cleared.streamInfo).isNull()
    }

    @Test
    fun `a local copy after a failure only clears the load and the error`() {
        val video = video("vid_a")
        val state = VideoPlayerUiState(cachedVideo = video, isLoading = true, error = "boom", errorHint = "hint")

        val next = state.applyLocalCopyAfterFailure()

        assertThat(next).isEqualTo(state.copy(isLoading = false, error = null, errorHint = null))
    }

    @Test
    fun `an offline fallback fills in the lane around a copy that is already playing`() {
        val related = listOf(video("rel_1"))
        val state = VideoPlayerUiState(isLoading = true, isUpcoming = true, upcomingReleaseTimeMs = 42L)

        val next =
            state.applyOfflineFallback(
                ResolvedPlayback.OfflineFallback(localFilePath = "/tmp/a.mp4", offlineSegments = null, relatedVideos = related),
            )

        assertThat(next.relatedVideos.map { it.id }).containsExactly("rel_1")
        assertThat(next.localFilePath).isEqualTo("/tmp/a.mp4")
        assertThat(next.isLoading).isFalse()
        assertThat(next.isUpcoming).isFalse()
        assertThat(next.upcomingReleaseTimeMs).isNull()
    }

    @Test
    fun `the merged result writes the streams, qualities, chapters and manifests it resolved`() {
        val videoStream = videoStream("1080p")
        val audioStream = mockk<AudioStream>(relaxed = true)
        val streams =
            mergedPlayback(
                selectedVideoStream = videoStream,
                selectedAudioStream = audioStream,
                availableQualities = listOf(VideoQuality.Q_1080P, VideoQuality.Q_720P),
                streamSizes = mapOf("137" to 100L),
                hlsUrl = "https://example.invalid/live.m3u8",
                isLiveStream = true,
                preferredQuality = VideoQuality.Q_1080P,
            )

        val next = VideoPlayerUiState(isLoading = true).applyMergedPlayback("vid_a", merged(streams))

        assertThat(next.videoStream).isSameInstanceAs(videoStream)
        assertThat(next.audioStream).isSameInstanceAs(audioStream)
        assertThat(next.availableQualities).containsExactly(VideoQuality.Q_1080P, VideoQuality.Q_720P).inOrder()
        assertThat(next.selectedQuality).isEqualTo(VideoQuality.Q_1080P)
        assertThat(next.streamSizes).containsExactly("137", 100L)
        assertThat(next.hlsUrl).isEqualTo("https://example.invalid/live.m3u8")
        assertThat(next.isLive).isTrue()
        assertThat(next.isAdaptiveMode).isFalse()
        assertThat(next.isLoading).isFalse()
        assertThat(next.savedPosition).isEqualTo(5_000L)
        assertThat(next.localFileVideoId).isNull()
        assertThat(next.isUpcoming).isFalse()
    }

    @Test
    fun `merged upcoming content keeps the countdown and drops every stream the merge produced`() {
        val streams = mergedPlayback(selectedVideoStream = videoStream("720p"), hlsUrl = "https://example.invalid/live.m3u8")

        val next =
            VideoPlayerUiState().applyMergedPlayback(
                "vid_a",
                merged(streams, isUpcomingContent = true, upcomingReleaseTimeMs = 1_700L),
            )

        assertThat(next.videoStream).isNull()
        assertThat(next.audioStream).isNull()
        assertThat(next.hlsUrl).isNull()
        assertThat(next.isLive).isFalse()
        assertThat(next.isUpcoming).isTrue()
        assertThat(next.upcomingReleaseTimeMs).isEqualTo(1_700L)
    }

    @Test
    fun `a merged local file stamps the video id it belongs to`() {
        val streams = mergedPlayback(localFilePath = "/tmp/a.mp4")

        val next = VideoPlayerUiState().applyMergedPlayback("vid_a", merged(streams))

        assertThat(next.localFilePath).isEqualTo("/tmp/a.mp4")
        assertThat(next.localFileVideoId).isEqualTo("vid_a")
    }

    @Test
    fun `the InnerTube VOD path writes its own field set and leaves the merged-only fields alone`() {
        val videoStream = videoStream("720p")
        val chapters = emptyList<org.schabi.newpipe.extractor.stream.StreamSegment>()
        val before =
            VideoPlayerUiState(
                isLoading = true,
                error = "boom",
                chapters = chapters,
                offlineSponsorBlockSegments = listOf(segment()),
                localFilePath = "/tmp/kept.mp4",
                localFileVideoId = "vid_a",
            )

        val next =
            before.applyVodStreams(
                relatedVideos = listOf(video("rel_1")),
                videoStream = videoStream,
                audioStream = null,
                availableQualities = listOf(VideoQuality.Q_720P),
                savedPositionMs = 9_000L,
                isAdaptiveMode = true,
                autoplayEnabled = false,
                innerTubeVideoFormats = emptyList(),
                innerTubeAudioFormats = emptyList(),
                streamSizes = mapOf("136" to 7L),
            )

        assertThat(next.streamInfo).isNull()
        assertThat(next.videoStream).isSameInstanceAs(videoStream)
        assertThat(next.selectedQuality).isEqualTo(VideoQuality.Q_720P)
        assertThat(next.savedPosition).isEqualTo(9_000L)
        assertThat(next.isAdaptiveMode).isTrue()
        assertThat(next.autoplayEnabled).isFalse()
        assertThat(next.streamSizes).containsExactly("136", 7L)
        assertThat(next.isLoading).isFalse()
        assertThat(next.error).isNull()
        assertThat(next.isLive).isFalse()
        assertThat(next.isUpcoming).isFalse()
        // The second-writer gap this path has always had: these stay as the load left them.
        assertThat(next.chapters).isSameInstanceAs(chapters)
        assertThat(next.offlineSponsorBlockSegments).isEqualTo(before.offlineSponsorBlockSegments)
        assertThat(next.localFilePath).isEqualTo("/tmp/kept.mp4")
        assertThat(next.localFileVideoId).isEqualTo("vid_a")
    }

    @Test
    fun `the InnerTube live path publishes the manifest and empties the InnerTube format lists`() {
        val before =
            VideoPlayerUiState(
                streamInfo = mockk(relaxed = true),
                isLoading = true,
                error = "boom",
                errorHint = "hint",
                innerTubeVideoFormats = listOf(mockk(relaxed = true)),
                isUpcoming = true,
                upcomingReleaseTimeMs = 12L,
            )

        val next = before.applyLiveStreams(listOf(video("rel_1")), "https://example.invalid/live.m3u8")

        assertThat(next.streamInfo).isNull()
        assertThat(next.hlsUrl).isEqualTo("https://example.invalid/live.m3u8")
        assertThat(next.isLive).isTrue()
        assertThat(next.isLoading).isFalse()
        assertThat(next.error).isNull()
        assertThat(next.errorHint).isNull()
        assertThat(next.innerTubeVideoFormats).isEmpty()
        assertThat(next.innerTubeAudioFormats).isEmpty()
        assertThat(next.isUpcoming).isFalse()
        assertThat(next.upcomingReleaseTimeMs).isNull()
    }

    @Test
    fun `a VOD failure keeps the lane the load had already gathered`() {
        val next =
            VideoPlayerUiState(isLoading = true).applyVodFailure(
                relatedVideos = listOf(video("rel_1")),
                videoError = VideoErrorMapper.VideoError(message = "no", hint = "try later"),
            )

        assertThat(next.isLoading).isFalse()
        assertThat(next.relatedVideos.map { it.id }).containsExactly("rel_1")
        assertThat(next.error).isEqualTo("no")
        assertThat(next.errorHint).isEqualTo("try later")
    }

    @Test
    fun `a playback failure with no lane of its own leaves the one on screen alone`() {
        val onScreen = listOf(video("rel_1"))
        val error = VideoErrorMapper.VideoError(message = "no", hint = null)

        val kept = VideoPlayerUiState(relatedVideos = onScreen).applyPlaybackFailure(null, error)
        val replaced = VideoPlayerUiState(relatedVideos = onScreen).applyPlaybackFailure(listOf(video("rel_2")), error)

        assertThat(kept.relatedVideos).isEqualTo(onScreen)
        assertThat(kept.error).isEqualTo("no")
        assertThat(kept.errorHint).isNull()
        assertThat(replaced.relatedVideos.map { it.id }).containsExactly("rel_2")
    }

    @Test
    fun `channel metadata prefers the fetched avatar and folds it into the cached video`() {
        val state = VideoPlayerUiState(cachedVideo = video("vid_a").copy(channelThumbnailUrl = "embedded.jpg"))

        val next =
            state.applyChannelMetadata(
                SecondaryMetadata.Channel(
                    videoId = "vid_a",
                    loadToken = 1L,
                    fetchedAvatarUrl = "fetched.jpg",
                    embeddedAvatarUrl = "step-embedded.jpg",
                    subscriberCount = 42L,
                ),
            )

        assertThat(next.channelAvatarUrl).isEqualTo("fetched.jpg")
        assertThat(next.channelSubscriberCount).isEqualTo(42L)
        assertThat(next.cachedVideo?.channelThumbnailUrl).isEqualTo("fetched.jpg")
        assertThat(next.cachedVideo?.channelThumbnailUrls).containsExactly("fetched.jpg").inOrder()
    }

    @Test
    fun `channel metadata falls through the second stage to the avatar the cached video carries`() {
        val state =
            VideoPlayerUiState(
                cachedVideo = video("vid_a").copy(channelThumbnailUrl = "cached.jpg", channelThumbnailUrls = listOf("cached.jpg")),
            )

        val next =
            state.applyChannelMetadata(
                SecondaryMetadata.Channel(
                    videoId = "vid_a",
                    loadToken = 1L,
                    fetchedAvatarUrl = null,
                    embeddedAvatarUrl = null,
                    subscriberCount = null,
                ),
            )

        assertThat(next.channelAvatarUrl).isEqualTo("cached.jpg")
        assertThat(next.cachedVideo?.channelThumbnailUrls).containsExactly("cached.jpg")
        assertThat(next.channelSubscriberCount).isNull()
    }

    @Test
    fun `channel metadata for a video the screen has moved past changes nothing`() {
        val state = VideoPlayerUiState(cachedVideo = video("vid_b"))

        val next =
            state.applyChannelMetadata(
                SecondaryMetadata.Channel("vid_a", 1L, "fetched.jpg", null, 42L),
            )

        assertThat(next).isSameInstanceAs(state)
    }

    @Test
    fun `the related lane is published only for the video the screen is showing`() {
        val videos = listOf(video("rel_1"))
        val cached = VideoPlayerUiState(cachedVideo = video("vid_a"))

        assertThat(cached.applyRelatedVideos("vid_a", videos).relatedVideos).isEqualTo(videos)
        assertThat(cached.applyRelatedVideos("vid_b", videos)).isSameInstanceAs(cached)
    }

    @Test
    fun `enriched metadata merges its stream sizes into the map the download dialog reads`() {
        val streamInfo = mockk<StreamInfo>(relaxed = true)
        every { streamInfo.streamSegments } returns null
        every { streamInfo.videoStreams } returns emptyList()
        every { streamInfo.videoOnlyStreams } returns emptyList()
        every { streamInfo.audioStreams } returns emptyList()
        val enriched = video("vid_a").copy(title = "Enriched")
        val before = VideoPlayerUiState(streamSizes = mapOf("137" to 10L), relatedVideos = listOf(video("rel_1")))

        val next =
            before.applyEnrichedMetadata(
                SecondaryMetadata.Enriched(
                    videoId = "vid_a",
                    loadToken = 1L,
                    video = enriched,
                    streamInfo = streamInfo,
                    relatedVideos = emptyList(),
                ),
            )

        assertThat(next.cachedVideo).isEqualTo(enriched)
        assertThat(next.streamInfo).isSameInstanceAs(streamInfo)
        assertThat(next.streamSizes).containsExactly("137", 10L)
        assertThat(next.relatedVideos.map { it.id }).containsExactly("rel_1")
    }

    @Test
    fun `the live watch refresh keeps the avatar and count it could not resolve`() {
        val before = VideoPlayerUiState(channelAvatarUrl = "old.jpg", channelSubscriberCount = 7L)
        val refreshed = video("vid_a").copy(title = "Live now")

        val next =
            before.applyLiveWatchMetadata(
                SecondaryMetadata.LiveWatch(
                    videoId = "vid_a",
                    loadToken = 1L,
                    video = refreshed,
                    relatedVideos = emptyList(),
                    channelAvatarUrl = null,
                    subscriberCount = null,
                ),
            )

        assertThat(next.cachedVideo).isEqualTo(refreshed)
        assertThat(next.channelAvatarUrl).isEqualTo("old.jpg")
        assertThat(next.channelSubscriberCount).isEqualTo(7L)
    }

    @Test
    fun `primary metadata enriches the cached video and stops when the title is blank`() {
        val streamInfo = mockk<StreamInfo>(relaxed = true)
        every { streamInfo.name } returns "Real title"
        every { streamInfo.uploaderName } returns "Real channel"
        every { streamInfo.uploaderUrl } returns "https://youtube.invalid/channel/UC_real"
        every { streamInfo.thumbnails } returns emptyList()
        every { streamInfo.duration } returns 300L
        val cached = video("vid_a").copy(channelId = "", thumbnailUrl = "cached.jpg", duration = 10)

        val enriched = VideoPlayerUiState(cachedVideo = cached).primaryMetadataVideo("vid_a", streamInfo)

        assertThat(enriched?.title).isEqualTo("Real title")
        assertThat(enriched?.channelName).isEqualTo("Real channel")
        assertThat(enriched?.channelId).isEqualTo("UC_real")
        assertThat(enriched?.thumbnailUrl).isEqualTo("cached.jpg")
        assertThat(enriched?.duration).isEqualTo(300)

        every { streamInfo.name } returns "  "
        assertThat(VideoPlayerUiState(cachedVideo = cached).primaryMetadataVideo("vid_a", streamInfo)).isNull()
    }

    @Test
    fun `the neuro signal video carries the description and tags a title-only stub would lose`() {
        val streamInfo = mockk<StreamInfo>(relaxed = true)
        every { streamInfo.name } returns "Title"
        every { streamInfo.uploaderName } returns "Channel"
        every { streamInfo.uploaderUrl } returns "https://youtube.invalid/channel/UC_real"
        every { streamInfo.thumbnails } returns emptyList()
        every { streamInfo.duration } returns 42L
        every { streamInfo.viewCount } returns 99L
        every { streamInfo.tags } returns listOf("kotlin")

        val signal = neuroSignalVideo("vid_a", streamInfo)

        assertThat(signal.id).isEqualTo("vid_a")
        assertThat(signal.duration).isEqualTo(42)
        assertThat(signal.viewCount).isEqualTo(99L)
        assertThat(signal.tags).containsExactly("kotlin")
    }

    @Test
    fun `the live watch fallback video falls back to the cached metadata field by field`() {
        val streamInfo = mockk<StreamInfo>(relaxed = true)
        every { streamInfo.name } returns null
        every { streamInfo.uploaderName } returns null
        every { streamInfo.uploaderUrl } returns null
        every { streamInfo.thumbnails } returns emptyList()
        every { streamInfo.description } returns null
        every { streamInfo.viewCount } returns 5L
        val cached = video("vid_a").copy(title = "Cached title", channelName = "Cached channel", thumbnailUrl = "cached.jpg")

        val fallback = VideoPlayerUiState(cachedVideo = cached).liveWatchFallbackVideo("vid_a", streamInfo)

        assertThat(fallback.title).isEqualTo("Cached title")
        assertThat(fallback.channelName).isEqualTo("Cached channel")
        assertThat(fallback.thumbnailUrl).isEqualTo("cached.jpg")
        assertThat(fallback.duration).isEqualTo(0)
        assertThat(fallback.isLive).isTrue()
    }

    @Test
    fun `a blank video is only built when the screen holds nothing for the id`() {
        val cached = video("vid_a")

        assertThat(blankVideo("vid_a", cached)).isSameInstanceAs(cached)
        assertThat(blankVideo("vid_a", null).id).isEqualTo("vid_a")
        assertThat(blankVideo("vid_a", null).title).isEmpty()
    }

    private fun video(id: String): Video =
        Video(
            id = id,
            title = "Title $id",
            channelName = "Channel $id",
            channelId = "channel_$id",
            thumbnailUrl = "https://example.invalid/$id.jpg",
            duration = 120,
            viewCount = 1L,
            uploadDate = "2026-01-01",
        )

    private fun segment(): SponsorBlockSegment =
        SponsorBlockSegment(category = "sponsor", segment = listOf(0f, 1f), uuid = "uuid_1", actionType = "skip")

    private fun videoStream(resolution: String): VideoStream =
        VideoStream
            .Builder()
            .setId(resolution)
            .setContent("https://example.invalid/$resolution.mp4", true)
            .setMediaFormat(MediaFormat.MPEG_4)
            .setResolution(resolution)
            .setIsVideoOnly(true)
            .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
            .build()

    private fun mergedPlayback(
        selectedVideoStream: VideoStream? = null,
        selectedAudioStream: AudioStream? = null,
        availableQualities: List<VideoQuality> = emptyList(),
        streamSizes: Map<String, Long> = emptyMap(),
        hlsUrl: String? = null,
        isLiveStream: Boolean = false,
        localFilePath: String? = null,
        preferredQuality: VideoQuality = VideoQuality.AUTO,
    ): MergedPlayback =
        MergedPlayback(
            videoStreams = emptyList(),
            audioStreams = emptyList(),
            availableQualities = availableQualities,
            selectedVideoStream = selectedVideoStream,
            selectedAudioStream = selectedAudioStream,
            subtitles = emptyList(),
            chapters = emptyList(),
            streamSizes = streamSizes,
            innerTubeVideoFormats = emptyList(),
            innerTubeAudioFormats = emptyList(),
            hlsUrl = hlsUrl,
            dashManifestUrl = null,
            isLiveType = isLiveStream,
            isLiveStream = isLiveStream,
            hasPlayableContent = true,
            localFilePath = localFilePath,
            sabrInfo = null,
            preferSabr = false,
            preferredQuality = preferredQuality,
            preferredCodecKey = "auto",
        )

    private fun merged(
        streams: MergedPlayback,
        isUpcomingContent: Boolean = false,
        upcomingReleaseTimeMs: Long? = null,
    ): ResolvedPlayback.Merged =
        ResolvedPlayback.Merged(
            streamInfo = mockk(relaxed = true),
            streams = streams,
            relatedVideos = emptyList(),
            savedPositionMs = 5_000L,
            autoplayEnabled = true,
            offlineSegments = null,
            sponsorBlockBackfillNeeded = false,
            isUpcomingContent = isUpcomingContent,
            upcomingReleaseTimeMs = upcomingReleaseTimeMs,
            resumeOverrideRequested = false,
        )
}
