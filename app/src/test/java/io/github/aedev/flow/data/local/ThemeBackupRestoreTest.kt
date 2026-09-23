package io.github.aedev.flow.data.local

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.theme.CustomTheme
import io.github.aedev.flow.ui.theme.ThemeMode
import io.github.aedev.flow.ui.theme.ThemeVariant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Old backups carry palette names that no longer exist and the single custom palette older versions
 * kept. Restoring one must land on today's themes rather than failing or losing the user's colours.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ThemeBackupRestoreTest {
    private val manager = LocalDataManager(ApplicationProvider.getApplicationContext())

    @Test
    fun `an old backup restores onto today's themes and the export round trips`() =
        runBlocking {
            manager.restoreData(
                SettingsBackup(
                    strings =
                        mapOf(
                            "theme_mode" to "ROSE_LIGHT",
                            "system_light_theme_mode" to "SKY_LIGHT",
                            "system_dark_theme_mode" to "MIDNIGHT_BLACK",
                            "custom_theme_palettes_v2" to """{"dark":{"values":{"PRIMARY":4278255360}}}""",
                        ),
                ),
            )

            assertThat(manager.themeMode.first()).isEqualTo(ThemeMode.STRAWBERRY_DAIQUIRI)
            assertThat(manager.themeVariant.first()).isEqualTo(ThemeVariant.LIGHT)
            assertThat(manager.systemLightThemeMode.first()).isEqualTo(ThemeMode.NORDIC_HORIZON)
            assertThat(manager.systemDarkThemeMode.first()).isEqualTo(ThemeMode.TIDE)
            assertThat(manager.systemDarkThemeVariant.first()).isEqualTo(ThemeVariant.AMOLED)

            val restored = manager.customThemes.first().single()
            assertThat(restored.dark.primary).isEqualTo(Color(0xFF00FF00))

            val export = manager.getExportData()
            assertThat(export.strings["theme_mode"]).isEqualTo(ThemeMode.STRAWBERRY_DAIQUIRI.name)
            assertThat(CustomThemeCodec.decode(export.strings["custom_themes"])).hasSize(1)

            val imported = CustomTheme.from("custom-imported", "Imported")
            manager.restoreData(SettingsBackup(strings = mapOf("custom_themes" to CustomThemeCodec.encodeList(listOf(imported)))))
            assertThat(manager.customThemes.first().map { it.id }).containsExactly(restored.id, imported.id)

            manager.restoreData(SettingsBackup(strings = mapOf("theme_mode" to "NOT_A_THEME")))
            assertThat(manager.themeMode.first()).isEqualTo(ThemeMode.STRAWBERRY_DAIQUIRI)
        }
}
