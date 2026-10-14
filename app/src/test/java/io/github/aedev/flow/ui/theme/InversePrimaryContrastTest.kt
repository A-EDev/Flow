package io.github.aedev.flow.ui.theme

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Runs on Robolectric: toning goes through android.graphics.Color, which a plain JVM test stubs to zero. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class InversePrimaryContrastTest {
    @Test
    fun `snackbar actions read on the snackbar in every palette and style`() {
        ThemeCatalog.palettes.filterNot { it.mode == ThemeMode.MATERIAL_YOU }.forEach { entry ->
            ThemeVariant.entries.forEach { variant ->
                val scheme = FlowPalettes.forMode(entry.mode).colorsFor(variant).toColorScheme(variant)
                assertWithMessage("${entry.mode} $variant")
                    .that(contrastRatio(scheme.inversePrimary, scheme.inverseSurface))
                    .isAtLeast(4.5f)
            }
        }
    }
}
