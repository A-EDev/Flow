package io.github.aedev.flow.discord

enum class PlaybackKind {
    VIDEO,
    SHORT,
    LIVE,
    MUSIC,
}

enum class DiscordActivityType {
    WATCHING,
    LISTENING,
}

data class PlaybackSnapshot(
    val kind: PlaybackKind,
    val mediaId: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val isLive: Boolean,
)

data class DiscordPresencePayload(
    val type: DiscordActivityType,
    val mediaId: String,
    val details: String,
    val state: String,
    val largeImage: String,
    val largeImageText: String,
    val startTimestampSeconds: Long?,
    val endTimestampSeconds: Long?,
)

data class SentPresence(
    val payload: DiscordPresencePayload,
    val sentAtElapsedMs: Long,
)

sealed interface DiscordPresenceDecision {
    data class Send(val payload: DiscordPresencePayload) : DiscordPresenceDecision
    data object Skip : DiscordPresenceDecision
}

enum class DiscordConnectionState {
    UNAVAILABLE,
    DISCONNECTED,
    LINKING,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class DiscordPresenceStatus(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val connectionState: DiscordConnectionState = DiscordConnectionState.UNAVAILABLE,
    val linkedAccountLabel: String? = null,
    val errorMessage: String? = null,
)

data class DiscordAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
)

sealed interface DiscordLinkResult {
    data object Success : DiscordLinkResult
    data class Failure(val message: String) : DiscordLinkResult
}

sealed interface DiscordTransportResult {
    data object Success : DiscordTransportResult
    data object Unavailable : DiscordTransportResult
    data class Failure(val message: String) : DiscordTransportResult
}
