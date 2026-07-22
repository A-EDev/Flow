package io.github.aedev.flow.discord

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

internal enum class DiscordRuntimeAction {
    UNLINK,
    KEEP_CONNECTION,
    DISCONNECT,
}

internal fun discordRuntimeAction(
    enabled: Boolean,
    appForeground: Boolean,
    accountLinkInProgress: Boolean,
    backgroundPlaybackActive: Boolean,
): DiscordRuntimeAction = when {
    !enabled -> DiscordRuntimeAction.UNLINK
    accountLinkInProgress || appForeground || backgroundPlaybackActive ->
        DiscordRuntimeAction.KEEP_CONNECTION
    else -> DiscordRuntimeAction.DISCONNECT
}

internal fun Flow<Boolean>.delayDiscordPlaybackInactive(
    disconnectDelayMs: Long,
): Flow<Boolean> = channelFlow {
    distinctUntilChanged().collectLatest { active ->
        if (!active) delay(disconnectDelayMs)
        send(active)
    }
}.distinctUntilChanged()
