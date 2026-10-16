package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class OriginalTextPolicyTest {
    private val original = video(title = "XIAOMI 17T - ПРОСТОЙ, ПРИЯТНЫЙ ФЛАГМАН!", description = "Обзор смартфона")
    private val translated =
        original.copy(
            title = "XIAOMI 17T - A SIMPLE, PLEASANT FLAGSHIP!",
            description = "Smartphone review",
            viewCount = 9,
        )

    @Test
    fun `off, the translated title a list showed stays`() {
        assertThat(OriginalTextPolicy.streamTitle(original.title, translated.title, preferOriginal = false)).isEqualTo(translated.title)
    }

    @Test
    fun `on, the player's original title wins`() {
        assertThat(OriginalTextPolicy.streamTitle(original.title, translated.title, preferOriginal = true)).isEqualTo(original.title)
    }

    @Test
    fun `either way a blank title falls back to the other`() {
        assertThat(OriginalTextPolicy.streamTitle(" ", translated.title, preferOriginal = true)).isEqualTo(translated.title)
        assertThat(OriginalTextPolicy.streamTitle(original.title, "", preferOriginal = false)).isEqualTo(original.title)
        assertThat(OriginalTextPolicy.streamTitle(null, null, preferOriginal = false)).isNull()
    }

    @Test
    fun `the player's description replaces the known one only when wanted`() {
        assertThat(OriginalTextPolicy.streamDescription("Обзор", "", preferOriginal = true)).isEqualTo("Обзор")
        assertThat(OriginalTextPolicy.streamDescription("Обзор", "", preferOriginal = false)).isEmpty()
        assertThat(OriginalTextPolicy.streamDescription("  ", "known", preferOriginal = true)).isEqualTo("known")
    }

    @Test
    fun `off, the watch page's translation is applied whole`() {
        assertThat(OriginalTextPolicy.keepOriginalText(translated, original, preferOriginal = false)).isEqualTo(translated)
    }

    @Test
    fun `on, the watch page adds its counts but keeps the original text (#1230)`() {
        val kept = OriginalTextPolicy.keepOriginalText(translated, original, preferOriginal = true)

        assertThat(kept.title).isEqualTo(original.title)
        assertThat(kept.description).isEqualTo(original.description)
        assertThat(kept.viewCount).isEqualTo(9)
    }

    private fun video(
        title: String,
        description: String,
    ) = Video(
        id = "2BAEyzqxAME",
        title = title,
        channelName = "DimaViper",
        channelId = "UCs2DTZsK_6t6JD-m3yTapxg",
        thumbnailUrl = "",
        duration = 0,
        viewCount = 0,
        uploadDate = "",
        description = description,
    )
}
