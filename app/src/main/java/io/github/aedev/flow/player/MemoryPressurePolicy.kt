package io.github.aedev.flow.player

import android.content.ComponentCallbacks2

enum class MemoryPressureResponse {
    NONE,

    /** Drop the preloaded next item and its in-flight extraction; the playing video is untouched. */
    DROP_PRELOAD,

    /** Go audio-only while playing, or clear a paused player so it can be rebuilt later. */
    RELEASE_VIDEO,
}

/**
 * Android 14 stopped delivering every trim level except [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN]
 * and [ComponentCallbacks2.TRIM_MEMORY_BACKGROUND]; Android 15 deprecated the rest. Neither
 * surviving level signals pressure (BACKGROUND arrives whenever the process lands on the LRU list),
 * so treating it as critical cleared a paused video a few minutes after backgrounding and lost the
 * user's position (#920). Recovery could not undo that either: it replays the already-extracted
 * stream URLs, which have expired by then.
 *
 * MODERATE and COMPLETE mean the system is walking the LRU list of cached processes.
 * RUNNING_CRITICAL is a foreground level: it reaches a process that is still visible or holds a
 * foreground service. Disabling the video track there blacked out the picture the user was
 * watching (#1074), so a visible player only gives up its preload.
 *
 * Every level acted on here is deprecated and only reaches pre-34 devices, so on Android 14 and
 * above the response is always [MemoryPressureResponse.NONE].
 */
object MemoryPressurePolicy {
    @Suppress("DEPRECATION")
    private fun isCriticalLevel(trimLevel: Int): Boolean =
        trimLevel >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            trimLevel in ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL until
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN

    /** @param videoVisible the activity is at least STARTED or in picture-in-picture. */
    fun responseTo(
        trimLevel: Int,
        videoVisible: Boolean,
    ): MemoryPressureResponse =
        when {
            !isCriticalLevel(trimLevel) -> MemoryPressureResponse.NONE
            videoVisible -> MemoryPressureResponse.DROP_PRELOAD
            else -> MemoryPressureResponse.RELEASE_VIDEO
        }

    /**
     * A PiP window shows the video, so an audio-only mode or a deferred surface restore left over
     * from earlier would otherwise keep it black. Explicit background playback keeps its audio-only
     * mode: the PiP window then belongs to another player.
     */
    fun shouldRestoreVideoOnPipEntry(
        isInPictureInPictureMode: Boolean,
        isAudioOnly: Boolean,
        isVideoRestorePending: Boolean,
        explicitBackgroundPlaybackActive: Boolean,
    ): Boolean =
        isInPictureInPictureMode &&
            !explicitBackgroundPlaybackActive &&
            (isAudioOnly || isVideoRestorePending)
}
