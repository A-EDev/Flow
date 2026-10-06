package io.github.aedev.flow.data.local

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.ui.theme.AppFont
import io.github.aedev.flow.ui.theme.appFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.appFontDataStore: DataStore<Preferences> by safePreferencesDataStore(name = "app_font")

/** The app font choice. The custom font file itself lives in [CustomFontStore] and is not backed up. */
class AppFontPreferences
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val appContext = context.applicationContext
        val customFonts = CustomFontStore(appContext)

        val font: Flow<AppFont> = appContext.appFontDataStore.data.map { AppFont.fromStorage(it[FONT]) }

        val customFontName: Flow<String?> = appContext.appFontDataStore.data.map { it[CUSTOM_NAME] }

        /** What the app draws with, resolved off the main thread. A missing or broken custom file is the system font. */
        val fontFamily: Flow<FontFamily> =
            appContext.appFontDataStore.data
                .map { AppFont.fromStorage(it[FONT]) to it[CUSTOM_IMPORTED_AT] }
                .distinctUntilChanged()
                .map { (font, _) -> appFontFamily(font, customFonts.file(), customFonts::loadFamily) }
                .flowOn(Dispatchers.IO)

        suspend fun setFont(font: AppFont) {
            appContext.appFontDataStore.edit { it[FONT] = font.storageId }
        }

        suspend fun setCustomFont(displayName: String) {
            appContext.appFontDataStore.edit {
                it[FONT] = AppFont.CUSTOM.storageId
                it[CUSTOM_NAME] = displayName
                it[CUSTOM_IMPORTED_AT] = System.currentTimeMillis()
            }
        }

        suspend fun getSettingsBackup(): SettingsBackup = SettingsBackup(strings = mapOf(FONT.name to font.first().storageId))

        suspend fun restoreSettings(backup: SettingsBackup) {
            val restored = backup.strings[FONT.name] ?: return
            setFont(AppFont.fromStorage(restored))
        }

        private companion object {
            val FONT = stringPreferencesKey("app_font")
            val CUSTOM_NAME = stringPreferencesKey("app_font_custom_name")
            val CUSTOM_IMPORTED_AT = longPreferencesKey("app_font_custom_imported_at")
        }
    }
