package io.github.aedev.flow.player

import android.content.ComponentCallbacks2

object MemoryPressurePolicy {
    @Suppress("DEPRECATION")
    fun shouldReleaseVideoPlayback(trimLevel: Int): Boolean =
        trimLevel >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            trimLevel in ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL until
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
}
