package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThumbnailUrlResolverTest {
    @Test
    fun `normalizes jpg thumbnail variants to hq720`() {
        val result = ThumbnailUrlResolver.normalizeVideoThumbnail(
            "abc123",
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        )

        assertThat(result).isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }

    @Test
    fun `normalizes query and webp variants to hq720`() {
        val result = ThumbnailUrlResolver.normalizeVideoThumbnail(
            "fallback",
            "https://i.ytimg.com/vi_webp/abc123/mqdefault.webp?sqp=abc"
        )

        assertThat(result).isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }

    @Test
    fun `keeps non YouTube thumbnails unchanged`() {
        val raw = "https://example.com/thumb.jpg"

        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("abc123", raw)).isEqualTo(raw)
    }

    @Test
    fun `builds fallback when raw url is blank`() {
        assertThat(ThumbnailUrlResolver.normalizeVideoThumbnail("abc123", ""))
            .isEqualTo("https://i.ytimg.com/vi/abc123/hq720.jpg")
    }
}
