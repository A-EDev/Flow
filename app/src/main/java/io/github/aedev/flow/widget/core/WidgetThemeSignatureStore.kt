package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetThemeStore by preferencesDataStore(name = "widget_theme")

private val LAST_SIGNATURE = stringPreferencesKey("last_signature")

internal suspend fun Context.lastWidgetThemeSignature(): String? = widgetThemeStore.data.map { it[LAST_SIGNATURE] }.first()

internal suspend fun Context.writeWidgetThemeSignature(signature: String) {
    widgetThemeStore.edit { prefs -> prefs[LAST_SIGNATURE] = signature }
}
