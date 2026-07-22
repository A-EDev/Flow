package io.github.aedev.flow.discord

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import io.github.aedev.flow.R
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import okhttp3.OkHttpClient

object DiscordPresenceRuntime {
    private var scope: CoroutineScope? = null
    private var preferences: DiscordPreferences? = null
    private var tokenStore: DiscordTokenStore? = null
    private var transport: DiscordPresenceTransport? = null
    private var applicationContext: Context? = null
    private var attachedActivity = WeakReference<Activity>(null)
    private val isAppForeground = MutableStateFlow(false)
    private val isAccountLinkInProgress = MutableStateFlow(false)
    private val accountLinkMutex = Mutex()

    private val unavailableState = DiscordSettingsState(
        isAvailable = false,
        isEnabled = false,
        canEnable = false,
        connectionState = DiscordConnectionState.UNAVAILABLE,
        summary = DiscordSettingsSummary.UNAVAILABLE,
        accountName = null,
        errorMessage = null,
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
        this.applicationContext = applicationContext
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
            runtimePreferences.enabled
                .distinctUntilChanged()
                .collectLatest { enabled ->
                    if (!enabled) {
                        unlinkDiscordConnection(
                            transport = runtimeTransport,
                            disablePreference = {},
                            clearAccountLabel = {
                                runtimePreferences.setLinkedAccountLabel(null)
                            },
                        )
                        return@collectLatest
                    }

                    runEnabledPresence(
                        context = applicationContext,
                        transport = runtimeTransport,
                    )
                }
        }
    }

    private suspend fun runEnabledPresence(
        context: Context,
        transport: DiscordPresenceTransport,
    ) = coroutineScope {
        val playback = DiscordPlaybackSource().playback.shareIn(
            scope = this,
            started = SharingStarted.Eagerly,
            replay = 1,
        )
        val backgroundPlaybackActive = playback
            .map { snapshot -> snapshot != null }
            .delayDiscordPlaybackInactive(BACKGROUND_INACTIVE_DISCONNECT_DELAY_MS)
            .stateIn(
                scope = this,
                started = SharingStarted.Eagerly,
                initialValue = true,
            )

        launch {
            DiscordPresenceCoordinator(
                enabled = flowOf(true),
                playback = playback,
                transport = transport,
                mapper = DiscordPresenceMapper(
                    appName = context.getString(R.string.app_name),
                    playingFallback = context.getString(
                        R.string.discord_presence_playing_fallback,
                    ),
                    creatorLabel = { creator ->
                        context.getString(R.string.discord_presence_by_creator, creator)
                    },
                ),
                nowElapsedMs = SystemClock::elapsedRealtime,
            ).run()
        }

        combine(
            isAppForeground,
            isAccountLinkInProgress,
            backgroundPlaybackActive,
        ) { foreground, linking, playbackActive ->
            discordRuntimeAction(
                enabled = true,
                appForeground = foreground,
                accountLinkInProgress = linking,
                backgroundPlaybackActive = playbackActive,
            )
        }
            .distinctUntilChanged()
            .collect { action ->
                if (action == DiscordRuntimeAction.DISCONNECT) {
                    transport.disconnect()
                }
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

    fun setAppForeground(foreground: Boolean) {
        isAppForeground.value = foreground
    }

    suspend fun setEnabled(enabled: Boolean) {
        val currentTransport = transport ?: return
        val currentPreferences = preferences ?: return
        if (!currentTransport.isAvailable) {
            currentPreferences.setEnabled(false)
            return
        }

        if (enabled) {
            currentPreferences.setEnabled(true)
        } else {
            unlinkDiscordConnection(
                transport = currentTransport,
                disablePreference = { currentPreferences.setEnabled(false) },
                clearAccountLabel = { currentPreferences.setLinkedAccountLabel(null) },
            )
        }
    }

    suspend fun connectAccount(): DiscordLinkResult {
        if (!accountLinkMutex.tryLock()) {
            return DiscordLinkResult.Failure(
                applicationContext?.getString(R.string.discord_error_connection_in_progress).orEmpty(),
            )
        }

        isAccountLinkInProgress.value = true
        return try {
            connectAccountLocked()
        } finally {
            isAccountLinkInProgress.value = false
            accountLinkMutex.unlock()
        }
    }

    private suspend fun connectAccountLocked(): DiscordLinkResult {
        val currentTransport = transport
            ?: return DiscordLinkResult.Failure(
                applicationContext?.getString(R.string.discord_error_not_initialized).orEmpty(),
            )
        if (preferences?.enabled?.first() != true) {
            return DiscordLinkResult.Failure(
                applicationContext?.getString(R.string.discord_error_enable_before_connect).orEmpty(),
            )
        }
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
            ?: return DiscordLinkResult.Failure(
                applicationContext?.getString(R.string.discord_error_not_initialized).orEmpty(),
            )
        return retryDiscordConnection(currentTransport) { tokenStore?.load() }
    }

    suspend fun unlink(): Boolean {
        val currentTransport = transport ?: return false
        return unlinkDiscordConnection(
            transport = currentTransport,
            disablePreference = { preferences?.setEnabled(false) },
            clearAccountLabel = { preferences?.setLinkedAccountLabel(null) },
        )
    }

    fun shutdown() {
        val currentTransport = transport
        currentTransport?.close()
        scope?.cancel()
        scope = null
        preferences = null
        tokenStore = null
        transport = null
        applicationContext = null
        attachedActivity.clear()
        isAppForeground.value = false
        isAccountLinkInProgress.value = false
        _settingsState.value = unavailableState
    }

    private const val BACKGROUND_INACTIVE_DISCONNECT_DELAY_MS = 60_000L
}
