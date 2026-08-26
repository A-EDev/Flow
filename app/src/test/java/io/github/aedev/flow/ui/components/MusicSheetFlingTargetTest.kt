package io.github.aedev.flow.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicSheetFlingTargetTest {
    private fun target(
        velocity: Float,
        currentDp: Float,
        canDismiss: Boolean = true,
    ) = musicSheetFlingTarget(
        velocity = velocity,
        currentDp = currentDp,
        collapsedDp = COLLAPSED_DP,
        expandedDp = EXPANDED_DP,
        canDismiss = canDismiss,
    )

    @Test
    fun `flinging down from below the mini player dismisses`() {
        assertThat(target(velocity = -900f, currentDp = 40f)).isEqualTo(MusicSheetFlingTarget.Dismiss)
    }

    @Test
    fun `dragging the mini player fully down dismisses without a fling`() {
        assertThat(target(velocity = -20f, currentDp = 0f)).isEqualTo(MusicSheetFlingTarget.Dismiss)
    }

    @Test
    fun `a partial drag on the mini player snaps back`() {
        assertThat(target(velocity = -20f, currentDp = 60f)).isEqualTo(MusicSheetFlingTarget.Collapse)
    }

    @Test
    fun `nested scroll flings never dismiss`() {
        assertThat(target(velocity = -900f, currentDp = 0f, canDismiss = false))
            .isEqualTo(MusicSheetFlingTarget.Collapse)
    }

    @Test
    fun `flinging up expands`() {
        assertThat(target(velocity = 900f, currentDp = 40f)).isEqualTo(MusicSheetFlingTarget.Expand)
    }

    @Test
    fun `releasing past the halfway point expands`() {
        assertThat(target(velocity = 0f, currentDp = 600f)).isEqualTo(MusicSheetFlingTarget.Expand)
    }

    private companion object {
        const val COLLAPSED_DP = 80f
        const val EXPANDED_DP = 800f
    }
}
