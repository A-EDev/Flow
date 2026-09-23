package io.github.aedev.flow.widget.core

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import org.junit.Test

class WidgetThemeSignatureTest {
    private val theme = CustomTheme.from("custom-widget", "Widget")

    private fun signatureOf(theme: CustomTheme?) =
        WidgetThemeSignature(
            themeMode = ThemeMode.CUSTOM,
            themeVariant = ThemeVariant.DARK,
            customTheme = theme,
            systemLightThemeMode = ThemeMode.LIGHT,
            systemDarkThemeMode = ThemeMode.DARK,
            systemDarkThemeVariant = ThemeVariant.DARK,
        )

    @Test
    fun `the same theme signs the same way twice`() {
        assertThat(signatureOf(theme).persistedForm()).isEqualTo(signatureOf(theme.copy()).persistedForm())
    }

    @Test
    fun `a changed colour changes the signature`() {
        val edited = theme.withColors(ThemeVariant.AMOLED, theme.amoled.copy(surface = Color(0xFF123456)))
        assertThat(signatureOf(edited).persistedForm()).isNotEqualTo(signatureOf(theme).persistedForm())
    }

    @Test
    fun `no custom theme still signs`() {
        assertThat(signatureOf(null).persistedForm()).isNotEmpty()
    }
}
