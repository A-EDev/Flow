package io.github.aedev.flow.discord

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

object DiscordPresenceRuntime {
    private val unavailableState = DiscordSettingsState(
        isAvailable = false,
        isEnabled = false,
        canEnable = false,
        connectionState = DiscordConnectionState.UNAVAILABLE,
        summary = DiscordSettingsSummary.UNAVAILABLE,
        accountName = null,
        errorMessage = null,
    )
    private val mutableSettingsState = MutableStateFlow(unavailableState)
    val settingsState: StateFlow<DiscordSettingsState> = mutableSettingsState

    fun initialize(context: Context, okHttpClient: OkHttpClient) = Unit
    fun attachActivity(activity: Activity?) = Unit
    fun detachActivity(activity: Activity) = Unit
    fun setAppForeground(foreground: Boolean) = Unit
    suspend fun setEnabled(enabled: Boolean) = Unit
    suspend fun connectAccount(): DiscordLinkResult = DiscordLinkResult.Failure("")
    suspend fun retry(): DiscordLinkResult = DiscordLinkResult.Failure("")
    suspend fun unlink(): Boolean = false
    fun shutdown() = Unit
}
