package io.github.aedev.flow.discord

internal suspend fun retryDiscordConnection(
    transport: DiscordPresenceTransport,
    loadTokens: () -> DiscordAuthTokens?,
): DiscordLinkResult = loadTokens()?.let { tokens ->
    transport.connect(tokens)
} ?: transport.link()

internal suspend fun unlinkDiscordConnection(
    transport: DiscordPresenceTransport,
    disablePreference: suspend () -> Unit,
    clearAccountLabel: suspend () -> Unit,
): Boolean {
    disablePreference()
    val result = transport.unlink()
    clearAccountLabel()
    return result
}
