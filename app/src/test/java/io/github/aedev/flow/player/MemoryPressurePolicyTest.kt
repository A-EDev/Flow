package io.github.aedev.flow.player

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryPressurePolicyTest {
    @Suppress("DEPRECATION")
    @Test
    fun `ui hidden does not release video playback`() {
        assertThat(
            MemoryPressurePolicy.shouldReleaseVideoPlayback(
                ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
            )
        ).isFalse()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `running critical releases video playback on older Android versions`() {
        assertThat(
            MemoryPressurePolicy.shouldReleaseVideoPlayback(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
            )
        ).isTrue()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `background pressure releases rebuildable video playback`() {
        assertThat(
            MemoryPressurePolicy.shouldReleaseVideoPlayback(
                ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
            )
        ).isTrue()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `running low does not clear the active playlist`() {
        assertThat(
            MemoryPressurePolicy.shouldReleaseVideoPlayback(
                ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            )
        ).isFalse()
    }
}
