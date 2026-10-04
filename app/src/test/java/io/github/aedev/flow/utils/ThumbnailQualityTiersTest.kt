package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ThumbnailQuality
import org.junit.Test

class ThumbnailQualityTiersTest {
    private val id = "dQw4w9WgXcQ"

    private fun tiers(
        quality: ThumbnailQuality,
        portrait: Boolean = false,
    ) = ThumbnailUrlResolver.youtubeThumbnailCandidates(id, quality, portrait).map { it.substringAfterLast('/') }

    @Test
    fun `high is the ladder every card loaded before the setting existed`() {
        assertThat(ThumbnailUrlResolver.youtubeThumbnailCandidates(id, ThumbnailQuality.HIGH))
            .isEqualTo(ThumbnailUrlResolver.youtubeThumbnailCandidates(id))
        assertThat(tiers(ThumbnailQuality.HIGH)).containsExactly("hq720.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun `medium and low start smaller and still end on the tier every video has`() {
        assertThat(tiers(ThumbnailQuality.MEDIUM)).containsExactly("sddefault.jpg", "hqdefault.jpg").inOrder()
        assertThat(tiers(ThumbnailQuality.LOW)).containsExactly("mqdefault.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun `a portrait card never drops below hqdefault`() {
        assertThat(tiers(ThumbnailQuality.LOW, portrait = true)).containsExactly("hqdefault.jpg")
        assertThat(tiers(ThumbnailQuality.MEDIUM, portrait = true)).containsExactly("sddefault.jpg", "hqdefault.jpg").inOrder()
    }

    @Test
    fun `a DeArrow thumbnail stays first at every quality`() {
        val deArrow = "https://dearrow-thumb.ajay.app/api/v1/getThumbnail?videoID=$id&time=12"

        ThumbnailQuality.entries.forEach { quality ->
            assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates(id, deArrow, quality).first()).isEqualTo(deArrow)
        }
    }

    @Test
    fun `the video id is recovered from a stored portrait or webp url`() {
        val stored = "https://i.ytimg.com/vi_webp/$id/oar2.webp?sqp=abc"

        assertThat(ThumbnailUrlResolver.resolveVideoThumbnailCandidates("", stored, ThumbnailQuality.LOW))
            .containsExactly("https://i.ytimg.com/vi/$id/mqdefault.jpg", "https://i.ytimg.com/vi/$id/hqdefault.jpg")
            .inOrder()
    }

    @Test
    fun `the network picks which preference applies`() {
        assertThat(ThumbnailQuality.effective(isWifi = true, wifi = ThumbnailQuality.HIGH, cellular = ThumbnailQuality.LOW))
            .isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.effective(isWifi = false, wifi = ThumbnailQuality.HIGH, cellular = ThumbnailQuality.LOW))
            .isEqualTo(ThumbnailQuality.LOW)
    }

    @Test
    fun `an unknown or missing stored value reads as high`() {
        assertThat(ThumbnailQuality.fromName(null)).isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.fromName("ULTRA")).isEqualTo(ThumbnailQuality.HIGH)
        assertThat(ThumbnailQuality.fromName("LOW")).isEqualTo(ThumbnailQuality.LOW)
    }
}
