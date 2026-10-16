package io.github.aedev.flow.player.analytics

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** The video a first frame belongs to: the window that rendered it, else the player's current video. */
internal fun renderedVideoId(
    renderedMediaId: String?,
    currentVideoId: String?,
): String? = renderedMediaId?.takeIf { it.isNotBlank() } ?: currentVideoId

/**
 * Reports which video's first frame reached the surface.
 *
 * On a gapless advance Media3 releases the next item's first frame as the position reaches it, before
 * `onMediaItemTransition` promotes that item, so the player's current video is still the previous one
 * when `Player.Listener.onRenderedFirstFrame` runs (#1313). The analytics event is stamped with the
 * reading period, so the window it names is the one on screen.
 */
@UnstableApi
internal class RenderedFirstFrameListener(
    private val currentVideoId: () -> String?,
    private val onRendered: (videoId: String?) -> Unit,
) : AnalyticsListener {
    private val window = Timeline.Window()

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long,
    ) {
        onRendered(renderedVideoId(mediaIdOf(eventTime), currentVideoId()))
    }

    private fun mediaIdOf(eventTime: AnalyticsListener.EventTime): String? {
        val timeline = eventTime.timeline
        if (eventTime.windowIndex !in 0 until timeline.windowCount) return null
        return timeline.getWindow(eventTime.windowIndex, window).mediaItem.mediaId
    }
}
