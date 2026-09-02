package io.github.aedev.flow.data.paging

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPagingSourceTest {

    @Test
    fun testExtractVideoIdFormats() {
        val standardUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", SearchPagingSource.extractVideoId(standardUrl))

        val shortUrl = "https://youtu.be/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", SearchPagingSource.extractVideoId(shortUrl))

        val shortsUrl = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", SearchPagingSource.extractVideoId(shortsUrl))

        val fallbackUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ"
        assertEquals("dQw4w9WgXcQ", SearchPagingSource.extractVideoId(fallbackUrl))
    }
}
