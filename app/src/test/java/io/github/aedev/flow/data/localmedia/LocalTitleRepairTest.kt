package io.github.aedev.flow.data.localmedia

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalTitleRepairTest {
    @Test
    fun `a title damaged byte by byte comes back from the file name`() {
        val title = "Sn??lla ge oss en st??d like f??r detta ???????? #dance #viral"
        val fileName = "Uploader - Snälla ge oss en stöd like för detta 🙏🙏 #dance #viral.mp4"

        assertThat(repairedTitle(title, fileName)).isEqualTo("Snälla ge oss en stöd like för detta 🙏🙏 #dance #viral")
    }

    @Test
    fun `a whole file name stem is used when it is the title`() {
        assertThat(repairedTitle("Bl??b??r", "Blåbær.mkv")).isEqualTo("Blåbær")
    }

    @Test
    fun `a real question mark is left alone`() {
        assertThat(repairedTitle("Why?", "Why？.mp4")).isEqualTo("Why?")
        assertThat(repairedTitle("Why?", "Why.mp4")).isEqualTo("Why?")
    }

    @Test
    fun `a title found twice is left alone`() {
        assertThat(repairedTitle("??", "é à.mp4")).isEqualTo("??")
    }

    @Test
    fun `a match that splits a character is left alone`() {
        assertThat(repairedTitle("?a", "ha€a.mp4")).isEqualTo("?a")
    }

    @Test
    fun `a clean title is never touched`() {
        assertThat(repairedTitle("Snälla", "Sn??lla.mp4")).isEqualTo("Snälla")
    }
}
