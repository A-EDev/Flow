package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.SheetOpenOrigin
import org.junit.Test

class OpenFractionVelocityTest {
    private fun thumbnailAt(
        top: Float,
        velocityY: Float,
    ) = SheetOpenOrigin.Thumbnail(Rect(20f, top, 220f, top + 112f), 12f, null, velocityY)

    @Test
    fun `an upward fling starts the open at that speed across the remaining travel`() {
        assertThat(openFractionVelocity(thumbnailAt(top = 2000f, velocityY = -3000f))).isWithin(0.0001f).of(-1.5f)
    }

    @Test
    fun `a tap or a downward release starts from rest`() {
        assertThat(openFractionVelocity(thumbnailAt(top = 2000f, velocityY = 0f))).isEqualTo(0f)
        assertThat(openFractionVelocity(thumbnailAt(top = 2000f, velocityY = 900f))).isEqualTo(0f)
        assertThat(openFractionVelocity(SheetOpenOrigin.BelowScreen)).isEqualTo(0f)
    }

    @Test
    fun `a hard fling near the top is capped`() {
        assertThat(openFractionVelocity(thumbnailAt(top = 100f, velocityY = -9000f))).isEqualTo(-8f)
    }
}
