package io.github.aedev.flow.player

import android.content.ComponentCallbacks2

/**
 * Android 14 stopped delivering every trim level except [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN]
 * and [ComponentCallbacks2.TRIM_MEMORY_BACKGROUND]; Android 15 deprecated the rest. Neither
 * surviving level signals pressure — BACKGROUND arrives whenever the process lands on the LRU list
 * — so treating it as critical cleared a paused video a few minutes after backgrounding and lost
 * the user's position (#920). Recovery could not undo that either: it replays the already-extracted
 * stream URLs, which have expired by then.
 *
 * The levels below do mean the system is walking the LRU list to reclaim memory, and releasing
 * there still buys a low-memory device some headroom before it kills us outright. They are all
 * deprecated and only reach pre-34 devices, so on Android 14 and above this always returns false.
 */
object MemoryPressurePolicy {
    @Suppress("DEPRECATION")
    fun shouldReleaseVideoPlayback(trimLevel: Int): Boolean =
        trimLevel >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            trimLevel in ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL until
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
}
