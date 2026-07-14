package io.github.aedev.flow.discord

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPresenceCoordinatorTest {

    @Test
    fun `disabled setting clears presence`() = runTest {
        val enabled = MutableStateFlow(false)
        val playback = MutableStateFlow<PlaybackSnapshot?>(null)
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowEpochSeconds = { 1_000L },
            nowElapsedMs = { 1_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()

        assertThat(transport.clears).isEqualTo(1)
        assertThat(transport.updates).isEmpty()
        job.cancelAndJoin()
    }

    @Test
    fun `enabled playback publishes mapped presence`() = runTest {
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(
            PlaybackSnapshot(
                kind = PlaybackKind.VIDEO,
                mediaId = "video-1",
                title = "A useful video",
                subtitle = "Flow Creator",
                artworkUrl = "https://example.com/art.jpg",
                durationMs = 120_000L,
                positionMs = 30_000L,
                isPlaying = true,
                isLive = false,
            ),
        )
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowEpochSeconds = { 100_000L },
            nowElapsedMs = { 1_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()

        assertThat(transport.updates).hasSize(1)
        assertThat(transport.updates.single().details).isEqualTo("A useful video")
        assertThat(transport.updates.single().state).isEqualTo("by Flow Creator")
        job.cancelAndJoin()
    }

    @Test
    fun `repeated snapshots are deduplicated`() = runTest {
        var elapsed = 1_000L
        val snapshot = PlaybackSnapshot(
            kind = PlaybackKind.MUSIC,
            mediaId = "song-1",
            title = "A useful song",
            subtitle = "Flow Artist",
            artworkUrl = "",
            durationMs = 180_000L,
            positionMs = 20_000L,
            isPlaying = true,
            isLive = false,
        )
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(snapshot)
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowEpochSeconds = { 100_000L },
            nowElapsedMs = { elapsed },
        )

        val job = launch { coordinator.run() }
        runCurrent()
        elapsed += 1_000L
        playback.value = snapshot.copy(positionMs = 21_000L)
        runCurrent()

        assertThat(transport.updates).hasSize(1)
        job.cancelAndJoin()
    }

    private class RecordingTransport : DiscordPresenceTransport {
        override val connectionState = MutableStateFlow(DiscordConnectionState.CONNECTED)
        override val linkedAccountName = MutableStateFlow<String?>(null)
        override val lastError = MutableStateFlow<String?>(null)
        val updates = mutableListOf<DiscordPresencePayload>()
        var clears: Int = 0

        override fun attachActivity(activity: Activity?) = Unit

        override suspend fun link(): DiscordLinkResult = DiscordLinkResult.Success

        override suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult = DiscordLinkResult.Success

        override suspend fun update(payload: DiscordPresencePayload): Boolean {
            updates += payload
            return true
        }

        override suspend fun clear(): Boolean {
            clears += 1
            return true
        }

        override suspend fun unlink(): Boolean = true

        override fun close() = Unit
    }
}
