package io.github.aedev.flow.discord

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

object DiscordPresenceRuntime {
    private var scope: CoroutineScope? = null
    private var preferences: DiscordPreferences? = null
    private var tokenStore: DiscordTokenStore? = null
    private var transport: DiscordPresenceTransport? = null
    private var attachedActivity = WeakReference<Activity>(null)

    private val unavailableState = DiscordSettingsState(
        isAvailable = false,
        isEnabled = false,
        canEnable = false,
        connectionState = DiscordConnectionState.UNAVAILABLE,
        summary = DiscordSettingsSummary.UNAVAILABLE,
        accountName = null,
        errorMessage = "Discord Rich Presence has not initialized.",
    )
    private val _settingsState = MutableStateFlow(unavailableState)
    val settingsState: StateFlow<DiscordSettingsState> = _settingsState

    @Synchronized
    fun initialize(context: Context, okHttpClient: OkHttpClient) {
        if (scope != null) return
        val applicationContext = context.applicationContext
        val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val runtimePreferences = DiscordPreferences(applicationContext)
        val runtimeTokenStore = DiscordTokenStore(applicationContext)
        val runtimeTransport = DiscordPlatformTransportFactory().create(
            context = applicationContext,
            okHttpClient = okHttpClient,
            tokenStore = runtimeTokenStore,
        )

        scope = runtimeScope
        preferences = runtimePreferences
        tokenStore = runtimeTokenStore
        transport = runtimeTransport

        val state = combine(
            runtimePreferences.enabled,
            runtimePreferences.linkedAccountLabel,
            runtimeTransport.connectionState,
            runtimeTransport.linkedAccountName,
            runtimeTransport.lastError,
        ) { enabled, savedAccount, connection, liveAccount, error ->
            deriveDiscordSettingsState(
                preferenceEnabled = enabled,
                transportAvailable = runtimeTransport.isAvailable,
                connectionState = connection,
                accountName = liveAccount ?: savedAccount,
                errorMessage = error,
            )
        }.stateIn(runtimeScope, SharingStarted.Eagerly, unavailableState)

        runtimeScope.launch { state.collect { _settingsState.value = it } }
        runtimeScope.launch {
            runtimeTransport.linkedAccountName.collect { accountName ->
                if (!accountName.isNullOrBlank()) {
                    runtimePreferences.setLinkedAccountLabel(accountName)
                }
            }
        }
        runtimeScope.launch {
            runtimePreferences.enabled.collect { enabled ->
                if (!enabled) {
                    runtimeTransport.clear()
                } else {
                    runtimeTokenStore.load()?.let { runtimeTransport.connect(it) }
                }
            }
        }
        runtimeScope.launch {
            DiscordPresenceCoordinator(
                enabled = runtimePreferences.enabled,
                playback = DiscordPlaybackSource().playback,
                transport = runtimeTransport,
                nowElapsedMs = SystemClock::elapsedRealtime,
            ).run()
        }
    }

    fun attachActivity(activity: Activity?) {
        attachedActivity = WeakReference(activity)
        transport?.attachActivity(activity)
    }

    fun detachActivity(activity: Activity) {
        if (attachedActivity.get() === activity) {
            attachedActivity.clear()
            transport?.attachActivity(null)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        val currentTransport = transport ?: return
        val currentPreferences = preferences ?: return
        if (!currentTransport.isAvailable) {
            currentPreferences.setEnabled(false)
            return
        }

        currentPreferences.setEnabled(enabled)
        if (!enabled) {
            currentTransport.clear()
        }
    }

    suspend fun connectAccount(): DiscordLinkResult {
        val currentTransport = transport
            ?: return DiscordLinkResult.Failure("Discord Rich Presence has not initialized.")
        val result = currentTransport.link()
        if (result == DiscordLinkResult.Success) {
            currentTransport.linkedAccountName.value?.let { accountName ->
                preferences?.setLinkedAccountLabel(accountName)
            }
        }
        return result
    }

    suspend fun retry(): DiscordLinkResult {
        val currentTransport = transport
            ?: return DiscordLinkResult.Failure("Discord Rich Presence has not initialized.")
        val tokens = tokenStore?.load()
            ?: return DiscordLinkResult.Failure("Connect your Discord account first.")
        return currentTransport.connect(tokens)
    }

    suspend fun unlink(): Boolean {
        preferences?.setEnabled(false)
        val result = transport?.unlink() ?: false
        preferences?.setLinkedAccountLabel(null)
        return result
    }

    fun shutdown() {
        val currentTransport = transport
        currentTransport?.close()
        scope?.cancel()
        scope = null
        preferences = null
        tokenStore = null
        transport = null
        attachedActivity.clear()
        _settingsState.value = unavailableState
    }
}
