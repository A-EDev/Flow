package io.github.aedev.flow.discord

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface DiscordPresenceTransport {
    val isAvailable: Boolean
    val connectionState: StateFlow<DiscordConnectionState>
    val linkedAccountName: StateFlow<String?>
    val lastError: StateFlow<String?>

    fun attachActivity(activity: Activity?)
    suspend fun link(): DiscordLinkResult
    suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult
    suspend fun update(payload: DiscordPresencePayload): Boolean
    suspend fun clear(): Boolean
    suspend fun unlink(): Boolean
    fun close()
}
