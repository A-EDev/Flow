package io.github.aedev.flow.data.video

import io.github.aedev.flow.data.model.Video

/** How a load resolved, as far as an automatic download cares. */
enum class AutoDownloadResolution {
    /** An ordinary video with streams: the one case that can be downloaded. */
    VIDEO,

    /** Live, upcoming, or a copy already on the device: never downloaded. */
    NOT_DOWNLOADABLE,

    /** Nothing playable this time; a retry of the same open may still resolve. */
    FAILED,
}

/**
 * The one video the viewer opened that may still download on its own, tied to the load that
 * resolves it. Each open is consumed at most once, and a load for any other video drops it, so a
 * re-fired step, a stale load or a video the viewer has already left never queues anything.
 */
internal class AutoDownloadArm {
    private var video: Video? = null
    private var loadToken: Long? = null

    /** The viewer opened [video]; a video that only followed another clears the arm instead. */
    @Synchronized
    fun arm(
        video: Video,
        userOpened: Boolean,
    ) {
        this.video = video.takeIf { userOpened }
        loadToken = null
    }

    /** A load for [videoId] started as [token]: a retry of the armed video carries the arm along. */
    @Synchronized
    fun onLoadStarted(
        videoId: String,
        token: Long,
    ) {
        if (video?.id == videoId) loadToken = token else clear()
    }

    /** The video to download when ([videoId], [token]) is the armed load resolving as a video. */
    @Synchronized
    fun consume(
        videoId: String,
        token: Long,
        resolution: AutoDownloadResolution,
    ): Video? {
        val armed = video?.takeIf { it.id == videoId && loadToken == token } ?: return null
        if (resolution == AutoDownloadResolution.FAILED) return null
        clear()
        return armed.takeIf { resolution == AutoDownloadResolution.VIDEO }
    }

    private fun clear() {
        video = null
        loadToken = null
    }
}
