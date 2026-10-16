package io.github.aedev.flow.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.utils.AppLanguageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/** A restored language used to reach only the preference YouTube read, never the UI (#1277). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AppLanguageRestoreTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a restored backup applies its app language to the UI`() =
        runBlocking {
            AppLanguageManager.saveLanguageTag(app, "ru")
            val preferences = PlayerPreferences(app)

            preferences.restoreData(SettingsBackup(strings = mapOf("app_language" to "en")))

            assertThat(preferences.appLanguage.first()).isEqualTo("en")
            assertThat(AppLanguageManager.loadSelectedLanguageTag(app)).isEqualTo("en")
            assertThat(AppLanguageManager.appliedLanguage.value).isEqualTo("en")
        }

    @Test
    fun `a backup without a language leaves the UI language alone`() =
        runBlocking {
            AppLanguageManager.saveLanguageTag(app, "ru")

            PlayerPreferences(app).restoreData(SettingsBackup(strings = mapOf("trending_region" to "RU")))

            assertThat(AppLanguageManager.appliedLanguage.value).isEqualTo("ru")
        }
}
