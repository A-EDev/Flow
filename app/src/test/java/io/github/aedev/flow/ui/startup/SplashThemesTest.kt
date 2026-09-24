package io.github.aedev.flow.ui.startup

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.util.AppIcons
import org.junit.Test

class SplashThemesTest {
    @Test
    fun `every launcher alias has a splash on every tone`() {
        val styles = SplashTone.entries.flatMap { tone -> AppIcons.ALL_SUFFIXES.map { splashThemeFor(it, tone) } }

        assertThat(styles).doesNotContain(0)
    }

    @Test
    fun `aliases with their own art get their own splash`() {
        assertThat(splashThemeFor(".IconAmoled", SplashTone.BLACK)).isEqualTo(R.style.Theme_Flow_Starting_Black_Amoled)
        assertThat(splashThemeFor(".IconFlowRed", SplashTone.DARK)).isEqualTo(R.style.Theme_Flow_Starting_Dark)
    }

    @Test
    fun `the white ghost outline never lands on a light splash`() {
        assertThat(splashThemeFor(".IconGhost", SplashTone.LIGHT)).isEqualTo(R.style.Theme_Flow_Starting_Light)
    }

    @Test
    fun `the tone follows the theme background`() {
        assertThat(splashTone(Color.Black)).isEqualTo(SplashTone.BLACK)
        assertThat(splashTone(Color(0xFF0F0F0F))).isEqualTo(SplashTone.DARK)
        assertThat(splashTone(Color.White)).isEqualTo(SplashTone.LIGHT)
    }
}
