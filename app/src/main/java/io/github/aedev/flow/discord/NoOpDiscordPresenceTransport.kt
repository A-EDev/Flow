package io.github.aedev.flow.discord

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpDiscordPresenceTransport(
    private val unavailableMessage: String = "Discord Rich Presence is unavailable in this build.",
) : DiscordPresenceTransport {
    override val isAvailable: Boolean = false

    private val _connectionState = MutableStateFlow(DiscordConnectionState.UNAVAILABLE)
    override val connectionState: StateFlow<DiscordConnectionState> = _connectionState.asStateFlow()

    private val _linkedAccountName = MutableStateFlow<String?>(null)
    override val linkedAccountName: StateFlow<String?> = _linkedAccountName.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(unavailableMessage)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    override fun attachActivity(activity: Activity?) = Unit

    override suspend fun link(): DiscordLinkResult =
        DiscordLinkResult.Failure(unavailableMessage)

    override suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult =
        DiscordLinkResult.Failure(unavailableMessage)

    override suspend fun update(payload: DiscordPresencePayload): Boolean = false

    override suspend fun clear(): Boolean = false

    override suspend fun unlink(): Boolean = false

    override fun close() = Unit
}
