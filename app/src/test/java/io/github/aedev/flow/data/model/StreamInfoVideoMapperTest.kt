package io.github.aedev.flow.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.localization.DateWrapper
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The three player surfaces that rebuild the played video from the extracted stream share one
 * mapper, so this pins the fall-back chain and the defaults each caller relies on.
 */
class StreamInfoVideoMapperTest {
    private val cached =
        Video(
            id = "cached_id",
            title = "Cached title",
            channelName = "Cached channel",
            channelId = "cached_channel",
            thumbnailUrl = "https://example.invalid/cached.jpg",
            duration = 10,
            viewCount = 5L,
            likeCount = 7L,
            uploadDate = "2020-01-01",
            timestamp = 111L,
            description = "Cached description",
            channelThumbnailUrl = "https://example.invalid/cached-avatar.jpg",
            isMusic = true,
        )

    private fun streamInfo(
        name: String = "Extracted title",
        uploaderName: String? = "Extracted channel",
        uploaderUrl: String? = "https://www.youtube.com/channel/extracted_channel",
        thumbnails: List<Image> = emptyList(),
        description: Description? = Description("Extracted description", Description.PLAIN_TEXT),
        uploadDate: DateWrapper? = null,
    ): StreamInfo =
        StreamInfo(0, "https://example.invalid/watch", "https://example.invalid/watch", StreamType.VIDEO_STREAM, "extracted_id", name, 0)
            .apply {
                setUploaderName(uploaderName)
                setUploaderUrl(uploaderUrl)
                setThumbnails(thumbnails)
                setDuration(321L)
                setViewCount(999L)
                setLikeCount(42L)
                setDescription(description)
                setUploadDate(uploadDate)
            }

    private fun image(
        url: String,
        height: Int,
    ): Image = Image(url, height, Image.WIDTH_UNKNOWN, Image.ResolutionLevel.UNKNOWN)

    @Test
    fun `the extracted stream wins for every field it carries`() {
        val result =
            streamInfo(
                thumbnails =
                    listOf(
                        image("https://example.invalid/small.jpg", 90),
                        image("https://example.invalid/big.jpg", 720),
                    ),
            ).toVideo(base = cached)

        assertThat(result.id).isEqualTo("extracted_id")
        assertThat(result.title).isEqualTo("Extracted title")
        assertThat(result.channelName).isEqualTo("Extracted channel")
        assertThat(result.channelId).isEqualTo("extracted_channel")
        assertThat(result.thumbnailUrl).isEqualTo("https://example.invalid/big.jpg")
        assertThat(result.duration).isEqualTo(321)
        assertThat(result.viewCount).isEqualTo(999L)
        assertThat(result.description).isEqualTo("Extracted description")
    }

    @Test
    fun `an empty extracted field falls back to the cached video`() {
        val result =
            streamInfo(
                uploaderName = null,
                uploaderUrl = null,
                description = null,
            ).toVideo(base = cached)

        assertThat(result.channelName).isEqualTo("Cached channel")
        assertThat(result.channelId).isEqualTo("cached_channel")
        assertThat(result.thumbnailUrl).isEqualTo("https://example.invalid/cached.jpg")
        assertThat(result.description).isEqualTo("Cached description")
        assertThat(result.uploadDate).isEqualTo("2020-01-01")
    }

    @Test
    fun `the defaults are the values the quick-actions caller relies on`() {
        val result = streamInfo().toVideo(base = cached)

        assertThat(result.likeCount).isEqualTo(0L)
        assertThat(result.isMusic).isFalse()
        assertThat(result.channelThumbnailUrl).isEqualTo("https://example.invalid/cached-avatar.jpg")
    }

    @Test
    fun `a caller may override the title date avatar likes timestamp and music flag`() {
        val result =
            streamInfo().toVideo(
                base = cached,
                title = "DeArrow title",
                uploadDateText = "3 years ago",
                channelAvatarUrl = "https://example.invalid/fetched-avatar.jpg",
                likeCount = 42L,
                timestamp = cached.timestamp,
                isMusic = cached.isMusic,
            )

        assertThat(result.title).isEqualTo("DeArrow title")
        assertThat(result.uploadDate).isEqualTo("3 years ago")
        assertThat(result.channelThumbnailUrl).isEqualTo("https://example.invalid/fetched-avatar.jpg")
        assertThat(result.likeCount).isEqualTo(42L)
        assertThat(result.timestamp).isEqualTo(111L)
        assertThat(result.isMusic).isTrue()
    }

    @Test
    fun `the uploader channel id is the last segment of the uploader url`() {
        assertThat(streamInfo().uploaderChannelId).isEqualTo("extracted_channel")
        assertThat(streamInfo(uploaderUrl = null).uploaderChannelId).isNull()
    }

    @Test
    fun `the upload instant comes from the extracted date wrapper`() {
        val published = OffsetDateTime.of(2023, 1, 5, 12, 0, 0, 0, ZoneOffset.UTC)

        assertThat(streamInfo(uploadDate = DateWrapper(published)).uploadDateMillis)
            .isEqualTo(published.toInstant().toEpochMilli())
        assertThat(streamInfo().uploadDateMillis).isNull()
    }
}
