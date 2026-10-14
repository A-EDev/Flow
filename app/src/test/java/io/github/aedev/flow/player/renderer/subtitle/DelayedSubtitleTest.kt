package io.github.aedev.flow.player.renderer.subtitle

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.Subtitle
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DelayedSubtitleTest {
    /** One caption from 1 s to 3 s. */
    private val subtitle =
        object : Subtitle {
            private val cue = Cue.Builder().setText("Line").build()
            private val times = longArrayOf(1_000_000L, 3_000_000L)

            override fun getNextEventTimeIndex(timeUs: Long): Int = times.indexOfFirst { it > timeUs }.takeIf { it >= 0 } ?: -1

            override fun getEventTimeCount(): Int = times.size

            override fun getEventTime(index: Int): Long = times[index]

            override fun getCues(timeUs: Long): List<Cue> = if (timeUs in 1_000_000L until 3_000_000L) listOf(cue) else emptyList()
        }

    @Test
    fun `a later offset moves the caption and its event times later`() {
        var offsetUs = 2_000_000L
        val delayed = DelayedSubtitle(subtitle) { offsetUs }

        assertThat(delayed.getCues(2_000_000L)).isEmpty()
        assertThat(delayed.getCues(3_500_000L)).hasSize(1)
        assertThat(delayed.getEventTime(0)).isEqualTo(3_000_000L)
        assertThat(delayed.getNextEventTimeIndex(2_500_000L)).isEqualTo(0)

        offsetUs = -500_000L
        assertThat(delayed.getCues(700_000L)).hasSize(1)
        assertThat(delayed.getEventTime(1)).isEqualTo(2_500_000L)
    }
}
