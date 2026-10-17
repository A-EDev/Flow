package io.github.aedev.flow.ui.components.musicplayer.common

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.ui.components.shared.MediaPalette
import io.github.aedev.flow.ui.theme.contrastRatio
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs on Robolectric: toning goes through android.graphics.Color, which a plain JVM test stubs to zero. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MusicPlayerThemeTest {
    private val appError = Color(0xFFB3261E)

    private val covers =
        mapOf(
            "near black" to MediaPalette(Color(0xFF050505), Color(0xFF1A1A1A), Color.White),
            "near white" to MediaPalette(Color(0xFFF7F7F2), Color(0xFFFFFFFF), Color.Black),
            "grey" to MediaPalette(Color(0xFF7A7A7A), Color(0xFF9E9E9E), Color.White),
            "saturated orange" to MediaPalette(Color(0xFFD9481A), Color(0xFFFF6A00), Color.White),
            "saturated yellow" to MediaPalette(Color(0xFFE8D000), Color(0xFFFFF200), Color.Black),
            "saturated blue" to MediaPalette(Color(0xFF0B1FD6), Color(0xFF2962FF), Color.White),
            "saturated green" to MediaPalette(Color(0xFF00C853), Color(0xFF64DD17), Color.Black),
        )

    @Test
    fun `light scheme keeps text and controls readable on every cover`() {
        covers.forEach { (name, palette) ->
            val scheme = paletteLightColorScheme(palette, appError)
            val surface = scheme.surface
            assertWithMessage("$name body text").that(contrastRatio(scheme.onSurface, surface)).isAtLeast(7f)
            assertWithMessage("$name secondary text")
                .that(contrastRatio(scheme.onSurfaceVariant.compositeOver(surface), surface))
                .isAtLeast(4.5f)
            assertWithMessage("$name primary").that(contrastRatio(scheme.primary, surface)).isAtLeast(4.5f)
            assertWithMessage("$name on primary").that(contrastRatio(scheme.onPrimary, scheme.primary)).isAtLeast(4.5f)
            assertWithMessage("$name on primary container")
                .that(contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer))
                .isAtLeast(4.5f)
            assertWithMessage("$name on tertiary container")
                .that(contrastRatio(scheme.onTertiaryContainer, scheme.tertiaryContainer))
                .isAtLeast(4.5f)
            assertWithMessage("$name error").that(contrastRatio(scheme.error, surface)).isAtLeast(4.5f)
        }
    }

    @Test
    fun `light backdrop sits on the light scheme's surface`() {
        covers.forEach { (name, palette) ->
            val backdrop = playerBackdropColors(palette.base, palette.accent, light = true)
            assertWithMessage(name).that(backdrop.ground).isEqualTo(paletteLightColorScheme(palette, appError).surface)
        }
    }

    @Test
    fun `dark backdrop is the raw swatches over black`() {
        val palette = covers.getValue("saturated orange")
        val backdrop = playerBackdropColors(palette.base, palette.accent, light = false)
        assertThat(backdrop).isEqualTo(PlayerBackdropColors(Color.Black, palette.base, palette.accent))
    }

    @Test
    fun `player turns light only with the switch on, a light theme and a style that has a light version`() {
        val light = Color(0xFFFFFBFA)
        val dark = Color(0xFF141212)
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.GRADIENT, followLightTheme = true, appSurface = light)).isTrue()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.BLUR, followLightTheme = true, appSurface = light)).isTrue()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.BLUR_GRADIENT, followLightTheme = true, appSurface = light)).isTrue()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.GRADIENT, followLightTheme = false, appSurface = light)).isFalse()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.GRADIENT, followLightTheme = true, appSurface = dark)).isFalse()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.IMMERSIVE, followLightTheme = true, appSurface = light)).isFalse()
        assertThat(musicPlayerTurnsLight(MusicPlayerBackgroundStyle.DEFAULT, followLightTheme = true, appSurface = light)).isFalse()
    }
}
