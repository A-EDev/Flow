package io.github.aedev.flow.discord

enum class DiscordSettingsSummary {
    OFF,
    NOT_CONNECTED,
    READY,
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
    val linkedAccount = accountName?.takeIf(String::isNotBlank)
    val summary = when {
        !available -> DiscordSettingsSummary.UNAVAILABLE
        connectionState == DiscordConnectionState.ERROR -> DiscordSettingsSummary.ERROR
        !enabled -> DiscordSettingsSummary.OFF
        connectionState == DiscordConnectionState.CONNECTED -> DiscordSettingsSummary.CONNECTED
        linkedAccount != null -> DiscordSettingsSummary.READY
        else -> DiscordSettingsSummary.NOT_CONNECTED
    }

    return DiscordSettingsState(
        isAvailable = available,
        isEnabled = enabled,
        canEnable = available,
        connectionState = connectionState,
        summary = summary,
        accountName = linkedAccount,
        errorMessage = errorMessage?.takeIf(String::isNotBlank),
    )
}
