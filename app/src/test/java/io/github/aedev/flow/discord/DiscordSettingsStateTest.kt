package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordSettingsStateTest {
    @Test
    fun `unavailable transport cannot appear enabled`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = true,
            transportAvailable = false,
            connectionState = DiscordConnectionState.UNAVAILABLE,
            accountName = null,
            errorMessage = "SDK missing",
        )

        assertThat(state.isEnabled).isFalse()
        assertThat(state.canEnable).isFalse()
        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.UNAVAILABLE)
    }

    @Test
    fun `available disabled setting reports off`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = false,
            transportAvailable = true,
            connectionState = DiscordConnectionState.DISCONNECTED,
            accountName = null,
            errorMessage = null,
        )

        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.OFF)
    }

    @Test
    fun `enabled unlinked setting reports not connected`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = true,
            transportAvailable = true,
            connectionState = DiscordConnectionState.DISCONNECTED,
            accountName = null,
            errorMessage = null,
        )

        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.NOT_CONNECTED)
    }

    @Test
    fun `connected setting exposes account`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = true,
            transportAvailable = true,
            connectionState = DiscordConnectionState.CONNECTED,
            accountName = "pasta",
            errorMessage = null,
        )

        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.CONNECTED)
        assertThat(state.accountName).isEqualTo("pasta")
    }

    @Test
    fun `transport error takes precedence while enabled`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = true,
            transportAvailable = true,
            connectionState = DiscordConnectionState.ERROR,
            accountName = null,
            errorMessage = "Connection timed out",
        )

        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.ERROR)
        assertThat(state.errorMessage).isEqualTo("Connection timed out")
    }

    @Test
    fun `connection error remains visible while preference is disabled`() {
        val state = deriveDiscordSettingsState(
            preferenceEnabled = false,
            transportAvailable = true,
            connectionState = DiscordConnectionState.ERROR,
            accountName = null,
            errorMessage = "Sign-in failed",
        )

        assertThat(state.isEnabled).isFalse()
        assertThat(state.summary).isEqualTo(DiscordSettingsSummary.ERROR)
        assertThat(state.errorMessage).isEqualTo("Sign-in failed")
    }
}
