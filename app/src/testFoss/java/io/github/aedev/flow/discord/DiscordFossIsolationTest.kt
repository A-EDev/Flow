package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordFossIsolationTest {
    @Test
    fun `foss classpath excludes functional Discord implementation`() {
        val forbiddenClasses = listOf(
            "io.github.aedev.flow.discord.DiscordTokenStore",
            "io.github.aedev.flow.discord.DiscordAuthTokens",
            "io.github.aedev.flow.discord.DiscordPlaybackSource",
            "io.github.aedev.flow.discord.DiscordPresenceCoordinator",
            "io.github.aedev.flow.discord.KizzyDiscordPresenceTransport",
            "io.github.aedev.flow.discord.KizzyGatewayProtocol",
        )

        forbiddenClasses.forEach { className ->
            assertThat(runCatching { Class.forName(className) }.isFailure).isTrue()
        }
    }

    @Test
    fun `foss runtime reports Discord unavailable`() {
        assertThat(DiscordPresenceRuntime.settingsState.value.isAvailable).isFalse()
        assertThat(DiscordPresenceRuntime.settingsState.value.isEnabled).isFalse()
        assertThat(DiscordPresenceRuntime.settingsState.value.summary)
            .isEqualTo(DiscordSettingsSummary.UNAVAILABLE)
    }
}
