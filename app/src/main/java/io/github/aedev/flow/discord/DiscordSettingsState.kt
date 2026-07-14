package io.github.aedev.flow.discord

enum class DiscordSettingsSummary {
    OFF,
    NOT_CONNECTED,
    CONNECTED,
    UNAVAILABLE,
    ERROR,
}

data class DiscordSettingsState(
    val isAvailable: Boolean,
    val isEnabled: Boolean,
    val canEnable: Boolean,
    val connectionState: DiscordConnectionState,
    val summary: DiscordSettingsSummary,
    val accountName: String?,
    val errorMessage: String?,
)

fun deriveDiscordSettingsState(
    preferenceEnabled: Boolean,
    transportAvailable: Boolean,
    connectionState: DiscordConnectionState,
    accountName: String?,
    errorMessage: String?,
): DiscordSettingsState {
    val available = transportAvailable && connectionState != DiscordConnectionState.UNAVAILABLE
    val enabled = preferenceEnabled && available
    val summary = when {
        !available -> DiscordSettingsSummary.UNAVAILABLE
        enabled && connectionState == DiscordConnectionState.ERROR -> DiscordSettingsSummary.ERROR
        !enabled -> DiscordSettingsSummary.OFF
        connectionState == DiscordConnectionState.CONNECTED -> DiscordSettingsSummary.CONNECTED
        else -> DiscordSettingsSummary.NOT_CONNECTED
    }

    return DiscordSettingsState(
        isAvailable = available,
        isEnabled = enabled,
        canEnable = available,
        connectionState = connectionState,
        summary = summary,
        accountName = accountName?.takeIf(String::isNotBlank),
        errorMessage = errorMessage?.takeIf(String::isNotBlank),
    )
}
