package io.github.aedev.flow.discord

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpDiscordPresenceTransport(
    private val unavailableMessage: String = "Discord Rich Presence is unavailable in this build."
) : DiscordPresenceTransport {