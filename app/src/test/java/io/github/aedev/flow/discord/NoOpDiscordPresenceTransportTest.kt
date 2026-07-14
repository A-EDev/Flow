package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NoOpDiscordPresenceTransportTest {

    @Test
    fun `no-op transport reports unavailable and rejects updates`() = runTest {
        val transport = NoOpDiscordPresenceTransport("Not included")

        assertThat(transport.connectionState.value).isEqualTo(DiscordConnectionState.UNAVAILABLE)
        assertThat(transport.lastError.value).isEqualTo("Not included")
        assertThat(transport.update(samplePayload())).isFalse()
        assertThat(transport.clear()).isFalse()
    }

    @Test
    fun `no-op transport cannot link or connect`() = runTest {
        val transport = NoOpDiscordPresenceTransport()
        val tokens = DiscordAuthTokens(
            accessToken = "access",
            refreshToken = "refresh",
            expiresAtEpochSeconds = 2_000L,
        )

        assertThat(transport.link()).isEqualTo(DiscordLinkResult.Failure("Discord Rich Presence is unavailable in this build."))
        assertThat(transport.connect(tokens)).isEqualTo(DiscordLinkResult.Failure("Discord Rich Presence is unavailable in this build."))
        assertThat(transport.unlink()).isFalse()
    }

    private fun samplePayload() = DiscordPresencePayload(
        type = DiscordActivityType.WATCHING,
        mediaId = "video-1",
        details = "Video",
        state = "by Creator",
        largeImage = "flow_logo",
        largeImageText = "Video",
        startTimestampSeconds = null,
        endTimestampSeconds = null,
    )
}
