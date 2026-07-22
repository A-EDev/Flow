package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscordPresenceMapperTest {
    private val mapper = DiscordPresenceMapper(
        appName = "Flow",
        playingFallback = "Playing in Flow",
        creatorLabel = { creator -> "by $creator" },
    )

    @Test
    fun musicMapsToListeningWithArtistAndCountdown() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.MUSIC,
                mediaId = "song-1",
                title = "Night Drive",
                subtitle = "Example Artist",
                artworkUrl = "https://i.ytimg.com/vi/song-1/maxresdefault.jpg",
                positionMs = 30_000L,
                durationMs = 180_000L,
                isPlaying = true,
                isLive = false,
            ),
            nowEpochSeconds = 1_000L,
        )

        assertThat(result).isEqualTo(
            DiscordPresencePayload(
                type = DiscordActivityType.LISTENING,
                mediaId = "song-1",
                details = "Night Drive",
                state = "Example Artist",
                largeImage = "https://i.ytimg.com/vi/song-1/maxresdefault.jpg",
                largeImageText = "Night Drive",
                startTimestampSeconds = 970L,
                endTimestampSeconds = 1_150L,
            ),
        )
    }

    @Test
    fun videoMapsToWatchingWithChannel() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.VIDEO,
                mediaId = "video-1",
                title = "Building Flow",
                subtitle = "A-E Dev",
                artworkUrl = "https://i.ytimg.com/vi/video-1/maxresdefault.jpg",
                positionMs = 10_000L,
                durationMs = 70_000L,
                isPlaying = true,
                isLive = false,
            ),
            nowEpochSeconds = 5_000L,
        )

        assertThat(result?.type).isEqualTo(DiscordActivityType.WATCHING)
        assertThat(result?.details).isEqualTo("Building Flow")
        assertThat(result?.state).isEqualTo("by A-E Dev")
        assertThat(result?.startTimestampSeconds).isEqualTo(4_990L)
        assertThat(result?.endTimestampSeconds).isEqualTo(5_060L)
    }

    @Test
    fun liveVideoOmitsTimestampsAndRejectsInsecureArtwork() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.LIVE,
                mediaId = "live-1",
                title = "Live Stream",
                subtitle = "A-E Dev",
                artworkUrl = "http://insecure.example/image.jpg",
                positionMs = 60_000L,
                durationMs = 0L,
                isPlaying = true,
                isLive = true,
            ),
            nowEpochSeconds = 9_000L,
        )

        assertThat(result?.startTimestampSeconds).isNull()
        assertThat(result?.endTimestampSeconds).isNull()
        assertThat(result?.largeImage).isEqualTo("flow_logo")
    }

    @Test
    fun pausedSnapshotDoesNotPublish() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.SHORT,
                mediaId = "short-1",
                title = "Short",
                subtitle = "Creator",
                artworkUrl = "",
                positionMs = 0L,
                durationMs = 20_000L,
                isPlaying = false,
                isLive = false,
            ),
            nowEpochSeconds = 10L,
        )

        assertThat(result).isNull()
    }

    @Test
    fun shortMapsToWatchingWithCreatorAndFiniteTiming() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.SHORT,
                mediaId = "short-1",
                title = "A Short",
                subtitle = "Creator",
                artworkUrl = "https://i.ytimg.com/vi/short-1/oar2.jpg",
                positionMs = 5_000L,
                durationMs = 20_000L,
                isPlaying = true,
                isLive = false,
            ),
            nowEpochSeconds = 100L,
        )

        assertThat(result?.type).isEqualTo(DiscordActivityType.WATCHING)
        assertThat(result?.state).isEqualTo("by Creator")
        assertThat(result?.endTimestampSeconds).isEqualTo(115L)
    }

    @Test
    fun fieldsAreTrimmedAndLimitedToDiscordMaximum() {
        val result = mapper.map(
            snapshot = PlaybackSnapshot(
                kind = PlaybackKind.MUSIC,
                mediaId = "song-2",
                title = "x".repeat(200),
                subtitle = "  Artist  ",
                artworkUrl = "",
                positionMs = 0L,
                durationMs = 1_000L,
                isPlaying = true,
                isLive = false,
            ),
            nowEpochSeconds = 10L,
        )

        assertThat(result?.details).hasLength(128)
        assertThat(result?.state).isEqualTo("Artist")
    }
}
