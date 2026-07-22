package io.github.aedev.flow.discord

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscordPreferencesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `enabled defaults to false`() = runTest {
        val preferences = createPreferences(backgroundScope, "default.preferences_pb")

        assertThat(preferences.enabled.first()).isFalse()
    }

    @Test
    fun `enabled and account label persist across instances`() = runTest {
        val file = File(temporaryFolder.root, "persisted.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file },
        )
        val first = DiscordPreferences(dataStore)
        first.setEnabled(true)
        first.setLinkedAccountLabel("pasta")
        val second = DiscordPreferences(dataStore)

        assertThat(second.enabled.first()).isTrue()
        assertThat(second.linkedAccountLabel.first()).isEqualTo("pasta")
    }

    private fun createPreferences(scope: CoroutineScope, fileName: String): DiscordPreferences =
        createPreferences(scope, File(temporaryFolder.root, fileName))

    private fun createPreferences(scope: CoroutineScope, file: File): DiscordPreferences =
        DiscordPreferences(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            ),
        )
}
