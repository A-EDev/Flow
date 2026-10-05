package io.github.aedev.flow.data.notes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteMomentsTest {
    @Test
    fun `reads times the way descriptions write them, with the rest of the line as the label`() {
        val note = "0:45 Board setup\n7:10 - Flashing OpenWrt\n1:02:03: the long part"

        assertThat(NoteMoments.find(note).map { it.positionMs to it.label })
            .containsExactly(45_000L to "Board setup", 430_000L to "Flashing OpenWrt", 3_723_000L to "the long part")
            .inOrder()
    }

    @Test
    fun `the range covers exactly the time, for the link`() {
        val note = "see 12:34 again"
        val moment = NoteMoments.find(note).single()

        assertThat(note.substring(moment.range)).isEqualTo("12:34")
    }

    @Test
    fun `things that only look like times are left alone`() {
        assertThat(NoteMoments.find("ratio 1:75, version 1.2:30, at 10:30:00:00, score 3:2")).isEmpty()
        assertThat(NoteMoments.find("a time like 5:61 does not exist")).isEmpty()
    }

    @Test
    fun `a time past the end of the video is not one of its moments`() {
        assertThat(NoteMoments.find("2:00 fine\n25:00 too late", durationMs = 600_000L).map { it.positionMs })
            .containsExactly(120_000L)
    }

    @Test
    fun `two times on one line each keep only their own words`() {
        assertThat(NoteMoments.find("3:00 intro 4:00 demo").map { it.label }).containsExactly("intro", "demo").inOrder()
    }

    @Test
    fun `the timeline lists each time once, earliest first`() {
        assertThat(NoteMoments.timeline("5:00 b\n1:00 a\n5:00 again").map { it.positionMs })
            .containsExactly(60_000L, 300_000L)
            .inOrder()
    }

    @Test
    fun `inserting starts a new line unless the cursor already begins one`() {
        assertThat(NoteMoments.insert("", 0, 754_000L)).isEqualTo("12:34 " to 6)
        assertThat(NoteMoments.insert("first", 5, 61_000L)).isEqualTo("first\n1:01 " to 11)
        assertThat(NoteMoments.insert("a\n", 2, 3_723_000L)).isEqualTo("a\n1:02:03 " to 10)
    }
}
