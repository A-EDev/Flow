package io.github.aedev.flow.player

import android.content.ComponentCallbacks2
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@Suppress("DEPRECATION")
class MemoryPressurePolicyTest {
    private val nonPressureLevels =
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        )

    private val pressureLevels =
        listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )

    @Test
    fun `running critical while the video is visible keeps the picture and drops the preload`() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, videoVisible = true))
            .isEqualTo(MemoryPressureResponse.DROP_PRELOAD)
    }

    @Test
    fun `running critical while hidden releases video playback`() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun `moderate background pressure while hidden releases rebuildable video playback`() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_MODERATE, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun `complete pressure while hidden releases rebuildable video playback`() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, videoVisible = false))
            .isEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
    }

    @Test
    fun `complete pressure in picture in picture does not release video playback`() {
        assertThat(MemoryPressurePolicy.responseTo(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, videoVisible = true))
            .isEqualTo(MemoryPressureResponse.DROP_PRELOAD)
    }

    @Test
    fun `a visible video never releases at any level`() {
        for (level in pressureLevels + nonPressureLevels) {
            assertThat(MemoryPressurePolicy.responseTo(level, videoVisible = true))
                .isNotEqualTo(MemoryPressureResponse.RELEASE_VIDEO)
        }
    }

    @Test
    fun `levels that do not signal pressure change nothing whether visible or hidden`() {
        for (level in nonPressureLevels) {
            for (visible in listOf(true, false)) {
                assertThat(MemoryPressurePolicy.responseTo(level, visible))
                    .isEqualTo(MemoryPressureResponse.NONE)
            }
        }
    }
}
