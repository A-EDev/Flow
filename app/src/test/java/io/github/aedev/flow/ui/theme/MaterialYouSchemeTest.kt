package io.github.aedev.flow.ui.theme

import android.app.Application
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MaterialYouSchemeTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun resolve(variant: ThemeVariant) =
        resolveFlowColorScheme(
            context = context,
            isSystemDark = variant != ThemeVariant.LIGHT,
            themeMode = ThemeMode.MATERIAL_YOU,
            themeVariant = variant,
            customTheme = null,
            systemLightThemeMode = ThemeMode.DARK,
            systemDarkThemeMode = ThemeMode.DARK,
            systemDarkThemeVariant = ThemeVariant.DARK,
        )

    @Test
    fun `light and dark use the system dynamic scheme unchanged (#798)`() {
        assertEquals(dynamicLightColorScheme(context).toString(), resolve(ThemeVariant.LIGHT).toString())
        assertEquals(dynamicDarkColorScheme(context).toString(), resolve(ThemeVariant.DARK).toString())
    }

    @Test
    fun `amoled keeps the dynamic accents and turns only the surfaces black`() {
        val dynamic = dynamicDarkColorScheme(context)
        val amoled = resolve(ThemeVariant.AMOLED)
        assertEquals(dynamic.primary, amoled.primary)
        assertEquals(dynamic.secondaryContainer, amoled.secondaryContainer)
        assertEquals(dynamic.tertiary, amoled.tertiary)
        assertEquals(Color.Black, amoled.background)
        assertEquals(Color(0xFF080808), amoled.surface)
    }
}
