package io.github.aedev.flow.player.analytics

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@UnstableApi
class RenderedFirstFrameListenerTest {
    @Test
    fun `a frame from the appended window names that window's video while the old one is still current`() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame(eventTime(windowIndex = 1, currentWindowIndex = 0), Any(), 0L)

        assertThat(rendered).isEqualTo("second")
    }

    @Test
    fun `a frame from the current window names the current video`() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame(eventTime(windowIndex = 0, currentWindowIndex = 0), Any(), 0L)

        assertThat(rendered).isEqualTo("first")
    }

    @Test
    fun `an empty timeline falls back to the current video`() {
        var rendered: String? = null
        val listener = RenderedFirstFrameListener(currentVideoId = { "first" }) { rendered = it }

        listener.onRenderedFirstFrame(eventTime(windowIndex = 0, currentWindowIndex = 0, timeline = Timeline.EMPTY), Any(), 0L)

        assertThat(rendered).isEqualTo("first")
    }

    @Test
    fun `a window without a media id falls back to the current video`() {
        assertThat(renderedVideoId("", "first")).isEqualTo("first")
        assertThat(renderedVideoId(null, "first")).isEqualTo("first")
        assertThat(renderedVideoId("second", "first")).isEqualTo("second")
    }

    private fun eventTime(
        windowIndex: Int,
        currentWindowIndex: Int,
        timeline: Timeline = TwoVideoTimeline,
    ) = AnalyticsListener.EventTime(
        0L,
        timeline,
        windowIndex,
        null,
        0L,
        timeline,
        currentWindowIndex,
        null,
        0L,
        0L,
    )

    private object TwoVideoTimeline : Timeline() {
        private val ids = listOf("first", "second")

        override fun getWindowCount(): Int = ids.size

        override fun getWindow(
            windowIndex: Int,
            window: Window,
            defaultPositionProjectionUs: Long,
        ): Window =
            window.set(
                ids[windowIndex],
                MediaItem.Builder().setMediaId(ids[windowIndex]).build(),
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                true,
                false,
                null,
                0L,
                10_000_000L,
                windowIndex,
                windowIndex,
                0L,
            )

        override fun getPeriodCount(): Int = ids.size

        override fun getPeriod(
            periodIndex: Int,
            period: Period,
            setIds: Boolean,
        ): Period = period.set(ids[periodIndex], ids[periodIndex], periodIndex, 10_000_000L, 0L)

        override fun getIndexOfPeriod(uid: Any): Int = ids.indexOf(uid)

        override fun getUidOfPeriod(periodIndex: Int): Any = ids[periodIndex]
    }
}
