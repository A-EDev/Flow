package com.flow.youtube.data.repository

import com.flow.youtube.data.model.Video
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class YouTubeRepositoryTest {

    private lateinit var repository: YouTubeRepository

    @Before
    fun setup() {
        repository = YouTubeRepository.getInstance()
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `toVideo handles watch URL properly`() {
        val streamItem = mockk<StreamInfoItem>(relaxed = true)
        every { streamItem.url } returns "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        every { streamItem.name } returns "Never Gonna Give You Up"
        every { streamItem.uploaderName } returns "Rick Astley"
        every { streamItem.duration } returns 212L
        every { streamItem.streamType } returns StreamType.VIDEO_STREAM

        // Access private toVideo via reflection for testing mapping logic
        val toVideoMethod = YouTubeRepository::class.java.getDeclaredMethod("toVideo", StreamInfoItem::class.java)
        toVideoMethod.isAccessible = true
        
        val video = toVideoMethod.invoke(repository, streamItem) as Video

        assertThat(video.id).isEqualTo("dQw4w9WgXcQ")
        assertThat(video.title).isEqualTo("Never Gonna Give You Up")
        assertThat(video.duration).isEqualTo(212)
        assertThat(video.isShort).isFalse()
    }

    @Test
    fun `toVideo handles shorts URL properly`() {
        val streamItem = mockk<StreamInfoItem>(relaxed = true)
        every { streamItem.url } returns "https://www.youtube.com/shorts/abcd123"
        every { streamItem.duration } returns 15L
        every { streamItem.streamType } returns StreamType.VIDEO_STREAM

        val toVideoMethod = YouTubeRepository::class.java.getDeclaredMethod("toVideo", StreamInfoItem::class.java)
        toVideoMethod.isAccessible = true
        
        val video = toVideoMethod.invoke(repository, streamItem) as Video

        assertThat(video.id).isEqualTo("abcd123")
        assertThat(video.isShort).isTrue()
    }

    @Test
    fun `toVideo handles live stream properly`() {
        val streamItem = mockk<StreamInfoItem>(relaxed = true)
        every { streamItem.url } returns "https://www.youtube.com/watch?v=live123"
        every { streamItem.streamType } returns StreamType.LIVE_STREAM
        every { streamItem.duration } returns -1L

        val toVideoMethod = YouTubeRepository::class.java.getDeclaredMethod("toVideo", StreamInfoItem::class.java)
        toVideoMethod.isAccessible = true
        
        val video = toVideoMethod.invoke(repository, streamItem) as Video

        assertThat(video.isLive).isTrue()
        assertThat(video.duration).isEqualTo(0)
    }

    @Test
    fun `toVideo handles NewPipe duration bug for shorts`() {
        val streamItem = mockk<StreamInfoItem>(relaxed = true)
        every { streamItem.url } returns "https://www.youtube.com/shorts/buggy"
        every { streamItem.duration } returns 0L // Common bug in some NewPipe versions

        val toVideoMethod = YouTubeRepository::class.java.getDeclaredMethod("toVideo", StreamInfoItem::class.java)
        toVideoMethod.isAccessible = true
        
        val video = toVideoMethod.invoke(repository, streamItem) as Video

        assertThat(video.isShort).isTrue()
        assertThat(video.duration).isEqualTo(60) // Should be forced to 60 as per repo logic
    }
}
