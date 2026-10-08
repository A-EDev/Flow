package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaStoreThumbnailsTest {
    @Test
    fun `an embedded cover is decoded no smaller than the artwork asks for`() {
        assertThat(sampleSize(3000, 3000, 512)).isEqualTo(4)
        assertThat(sampleSize(1024, 1024, 512)).isEqualTo(2)
        assertThat(sampleSize(1000, 1000, 512)).isEqualTo(1)
        assertThat(sampleSize(4000, 600, 512)).isEqualTo(1)
    }

    @Test
    fun `an unreadable size is decoded as it is`() {
        assertThat(sampleSize(0, 0, 512)).isEqualTo(1)
        assertThat(sampleSize(800, 800, 0)).isEqualTo(1)
    }
}
