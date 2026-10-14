package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.SheetOpenOrigin
import org.junit.Test

class SheetMorphTest {
    private val card = OpenOriginRect(left = 100f, top = 900f, width = 400f, height = 225f, cornerRadius = 32f)

    @Test
    fun `a thumbnail origin is moved into the layout's own coordinates`() {
        val rect =
            resolveOpenOriginRect(
                origin = SheetOpenOrigin.Thumbnail(Rect(120f, 950f, 520f, 1175f), cornerRadiusPx = 24f, imageKey = null),
                layoutLeft = 20f,
                layoutTop = 50f,
                screenHeight = 2400f,
                expandedVideoWidth = 1080f,
                expandedVideoHeight = 607f,
            )

        assertThat(rect).isEqualTo(OpenOriginRect(100f, 900f, 400f, 225f, 24f))
    }

    @Test
    fun `with no thumbnail in view the player starts just below the screen`() {
        val rect = resolveOpenOriginRect(SheetOpenOrigin.BelowScreen, 0f, 0f, 2400f, 1080f, 607f)

        assertThat(rect).isEqualTo(OpenOriginRect(0f, 2400f, 1080f, 607f, 0f))
    }

    @Test
    fun `no origin means no open is running`() {
        assertThat(resolveOpenOriginRect(null, 0f, 0f, 2400f, 1080f, 607f)).isNull()
    }

    @Test
    fun `the ground grows from the card to the whole layout`() {
        assertThat(openGroundRect(card, fraction = 1f, width = 1080f, height = 2400f))
            .isEqualTo(Rect(100f, 900f, 500f, 1125f))
        assertThat(openGroundRect(card, fraction = 0f, width = 1080f, height = 2400f))
            .isEqualTo(Rect(0f, 0f, 1080f, 2400f))
        assertThat(openGroundCornerRadius(card, 0.5f)).isEqualTo(16f)
    }

    @Test
    fun `the page follows the video in only once it has nearly landed`() {
        assertThat(openBodyAlpha(1f)).isEqualTo(0f)
        assertThat(openBodyAlpha(0.25f)).isEqualTo(0f)
        assertThat(openBodyAlpha(0.12f)).isWithin(0.1f).of(0.5f)
        assertThat(openBodyAlpha(0.02f)).isEqualTo(1f)
        assertThat(openBodyAlpha(0f)).isEqualTo(1f)
    }

    @Test
    fun `the box keeps the card's corner on screen while it grows`() {
        val atStart = morphCornerRadiusPx(1f, card, expandedVideoWidth = 1080f, miniCornerRadiusPx = 36f, visualMiniScale = 0.45f)
        val scaleAtStart = 400f / 1080f

        assertThat(atStart * scaleAtStart).isWithin(0.01f).of(32f)
        assertThat(morphCornerRadiusPx(0f, card, 1080f, 36f, 0.45f)).isEqualTo(0f)
    }

    @Test
    fun `without an open the mini player's corner applies past ten percent`() {
        assertThat(morphCornerRadiusPx(0.05f, null, 1080f, 36f, 0.45f)).isEqualTo(0f)
        assertThat(morphCornerRadiusPx(0.5f, null, 1080f, 36f, 0.45f)).isWithin(0.01f).of(80f)
    }

    @Test
    fun `the page behind the player stays opaque for the first stretch of a collapse`() {
        assertThat(collapsingGroundAlpha(0f)).isEqualTo(1f)
        assertThat(collapsingGroundAlpha(0.3f)).isEqualTo(1f)
        assertThat(collapsingGroundAlpha(0.625f)).isWithin(0.001f).of(0.5f)
        assertThat(collapsingGroundAlpha(0.95f)).isEqualTo(0f)
    }
}
