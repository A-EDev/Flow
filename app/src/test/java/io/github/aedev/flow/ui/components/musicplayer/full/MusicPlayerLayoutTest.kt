package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MusicPlayerLayoutTest {
    private fun layoutFor(
        widthDp: Int,
        heightDp: Int,
    ) = musicPlayerLayoutFor(
        windowSizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp.toFloat(), heightDp.toFloat()),
        isLandscapeWindow = widthDp > heightDp,
    )

    @Test
    fun `a phone held upright keeps the single column`() {
        assertThat(layoutFor(411, 891)).isEqualTo(MusicPlayerLayout.COMPACT)
        assertThat(layoutFor(360, 780)).isEqualTo(MusicPlayerLayout.COMPACT)
    }

    @Test
    fun `a phone turned sideways splits the cover from the controls`() {
        assertThat(layoutFor(891, 411)).isEqualTo(MusicPlayerLayout.SPLIT)
        assertThat(layoutFor(780, 360)).isEqualTo(MusicPlayerLayout.SPLIT)
    }

    @Test
    fun `an upright tablet or unfolded foldable centres the column`() {
        assertThat(layoutFor(800, 1280)).isEqualTo(MusicPlayerLayout.PORTRAIT_LARGE)
        assertThat(layoutFor(673, 841)).isEqualTo(MusicPlayerLayout.PORTRAIT_LARGE)
    }

    @Test
    fun `a landscape tablet or unfolded foldable earns the side pane`() {
        assertThat(layoutFor(1280, 800)).isEqualTo(MusicPlayerLayout.WIDE)
        assertThat(layoutFor(841, 673)).isEqualTo(MusicPlayerLayout.WIDE)
    }

    @Test
    fun `a short landscape window stays split however wide it is`() {
        assertThat(layoutFor(1200, 440)).isEqualTo(MusicPlayerLayout.SPLIT)
    }
}
