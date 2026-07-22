package io.github.aedev.flow.discord

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.discordDataStore: DataStore<Preferences> by safePreferencesDataStore(
    name = "discord_presence",
)

class DiscordPreferences(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.discordDataStore)

    val enabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ENABLED] ?: false
    }

    val linkedAccountLabel: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LINKED_ACCOUNT_LABEL]?.takeIf(String::isNotBlank)
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ENABLED] = enabled }
    }

    suspend fun setLinkedAccountLabel(label: String?) {
        dataStore.edit { preferences ->
            val normalized = label?.trim()?.takeIf(String::isNotBlank)
            if (normalized == null) {
                preferences.remove(LINKED_ACCOUNT_LABEL)
            } else {
                preferences[LINKED_ACCOUNT_LABEL] = normalized
            }
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val LINKED_ACCOUNT_LABEL = stringPreferencesKey("linked_account_label")
    }
}
