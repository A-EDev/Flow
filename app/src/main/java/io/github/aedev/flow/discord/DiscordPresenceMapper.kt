package io.github.aedev.flow.discord

class DiscordPresenceMapper {
    fun map(
        snapshot: PlaybackSnapshot,
        nowEpochSeconds: Long,
    ): DiscordPresencePayload? {
        if (!snapshot.isPlaying || snapshot.mediaId.isBlank()) return null

        val details = normalize(snapshot.title, fallback = "Playing in Flow")
        val subtitle = normalize(snapshot.subtitle, fallback = "Flow")
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L).let { position ->
            if (durationMs > 0L) position.coerceAtMost(durationMs) else position
        }
        val hasTimestamps = !snapshot.isLive && durationMs > 0L

        return DiscordPresencePayload(
            type = when (snapshot.kind) {
                PlaybackKind.MUSIC -> DiscordActivityType.LISTENING
                PlaybackKind.VIDEO,
                PlaybackKind.SHORT,
                PlaybackKind.LIVE,
                -> DiscordActivityType.WATCHING
            },
            mediaId = snapshot.mediaId,
            details = details,
            state = if (snapshot.kind == PlaybackKind.MUSIC) {
                subtitle
            } else {
                normalize("by $subtitle", fallback = "Flow")
            },
            largeImage = snapshot.artworkUrl
                .trim()
                .takeIf { it.startsWith("https://") }
                ?: FALLBACK_IMAGE_KEY,
            largeImageText = details,
            startTimestampSeconds = if (hasTimestamps) {
                nowEpochSeconds - positionMs / 1_000L
            } else {
                null
            },
            endTimestampSeconds = if (hasTimestamps) {
                nowEpochSeconds + (durationMs - positionMs) / 1_000L
            } else {
                null
            },
        )
    }

    private fun normalize(value: String, fallback: String): String =
        value
            .trim()
            .ifBlank { fallback }
            .take(MAX_TEXT_LENGTH)
            .padEnd(MIN_TEXT_LENGTH, ' ')

    private companion object {
        const val MIN_TEXT_LENGTH = 2
        const val MAX_TEXT_LENGTH = 128
        const val FALLBACK_IMAGE_KEY = "flow_logo"
    }
}
