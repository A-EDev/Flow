package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YouTubeUrlTest {
    @Test
    fun `reads the id out of every watch url shape`() {
        assertThat(videoIdFromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://youtu.be/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://www.youtube.com/embed/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://www.youtube.com/live/dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://www.youtube.com/watch?t=30&v=dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `reads the id off the supported front ends`() {
        assertThat(videoIdFromUrl("https://yewtu.be/watch?v=dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
        assertThat(videoIdFromUrl("https://piped.video/watch?v=dQw4w9WgXcQ")).isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun `an ordinary search phrase is not a url`() {
        assertThat(videoIdFromUrl("sam sulek")).isNull()
        assertThat(videoIdFromUrl("how to build a pc")).isNull()
        assertThat(isWatchUrl("linus tech tips")).isFalse()
    }

    @Test
    fun `a youtube link with no video carries no id`() {
        assertThat(videoIdFromUrl("https://www.youtube.com/@LinusTechTips")).isNull()
        assertThat(videoIdFromUrl("https://www.youtube.com/results?search_query=cats")).isNull()
    }
}
