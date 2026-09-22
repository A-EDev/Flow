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
                cachedVideo = video("vid_a"),
                isArchivedLivestream = false,
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
                storyboard = emptyList(),
            )

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
                isLoading = true,
                error = "boom",
                errorHint = "hint",
                innerTubeVideoFormats = listOf(mockk(relaxed = true)),
                isUpcoming = true,
                upcomingReleaseTimeMs = 12L,
            )

        val next = before.applyLiveStreams(listOf(video("rel_1")), "https://example.invalid/live.m3u8")

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
            storyboard = emptyList(),
        )
}
