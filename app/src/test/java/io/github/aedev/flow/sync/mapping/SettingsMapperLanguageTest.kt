package io.github.aedev.flow.sync.mapping

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SettingsBackup
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class SettingsMapperLanguageTest {
    @Test
    fun `the app language is sent to other devices`() {
        val exported = SettingsMapper.exportToCanonical(SettingsBackup(strings = mapOf("app_language" to "ru")), "1:0:a")

        assertThat(exported.single { it.key == "app_language" }.value).isEqualTo(JsonPrimitive("ru"))
    }

    @Test
    fun `another device's app language is never applied (#1277)`() {
        val backup =
            SettingsMapper.applyToBackup(
                listOf(
                    CanonicalSetting("app_language", JsonPrimitive("en"), "1:0:a"),
                    CanonicalSetting("trending_region", JsonPrimitive("RU"), "1:0:a"),
                ),
            )

        assertThat(backup.strings).doesNotContainKey("app_language")
        assertThat(backup.strings["trending_region"]).isEqualTo("RU")
    }
}
