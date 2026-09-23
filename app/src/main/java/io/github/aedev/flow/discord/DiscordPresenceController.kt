package io.github.aedev.flow.discord

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injectable access to Discord Rich Presence. Each flavor ships its own [DiscordPresenceRuntime] —
 * the real one in github, an always-unavailable stub in foss — and this adapter lets settings take
 * it as a dependency instead of reaching the object directly.
 */
@Singleton
class DiscordPresenceController
    @Inject
    constructor() {
        val settingsState: StateFlow<DiscordSettingsState> = DiscordPresenceRuntime.settingsState

        suspend fun setEnabled(enabled: Boolean) = DiscordPresenceRuntime.setEnabled(enabled)

        suspend fun connectAccount(): DiscordLinkResult = DiscordPresenceRuntime.connectAccount()

        suspend fun retry(): DiscordLinkResult = DiscordPresenceRuntime.retry()

        suspend fun unlink(): Boolean = DiscordPresenceRuntime.unlink()
    }
