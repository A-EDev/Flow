package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalMediaDetailsTest {
    @Test
    fun `the description wins over the long description and the comment`() {
        assertThat(shownDescription("About this video", "Long", "https://example.com")).isEqualTo("About this video")
        assertThat(shownDescription(" ", "Long", "https://example.com")).isEqualTo("Long")
    }

    @Test
    fun `the comment shows when nothing else describes the file`() {
        assertThat(shownDescription(null, null, " Notes from the uploader ")).isEqualTo("Notes from the uploader")
        assertThat(shownDescription(null, "", null)).isNull()
    }
}
