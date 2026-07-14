package io.github.aedev.flow.discord

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
        val playback = MutableStateFlow<FlowPlaybackSnapshot?>(null)
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowMs = { 1_000L },
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
        val playback = MutableStateFlow<FlowPlaybackSnapshot?>(
            FlowPlaybackSnapshot(
                mediaId = "video-1",
                mediaKind = FlowMediaKind.VIDEO,
                title = "A useful video",
                creator = "Flow Creator",
                artworkUrl = "https://example.com/art.jpg",
                durationMs = 120_000L,
                positionMs = 30_000L,
                isPlaying = true,
            ),
        )
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowMs = { 100_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()

        assertThat(transport.updates).hasSize(1)
        assertThat(transport.updates.single().details).isEqualTo("A useful video")
        assertThat(transport.updates.single().state).isEqualTo("Flow Creator")
        job.cancelAndJoin()
    }

    @Test
    fun `repeated snapshots are deduplicated`() = runTest {
        var now = 100_000L
        val snapshot = FlowPlaybackSnapshot(
            mediaId = "song-1",
            mediaKind = FlowMediaKind.MUSIC,
            title = "A useful song",
            creator = "Flow Artist",
            durationMs = 180_000L,
            positionMs = 20_000L,
            isPlaying = true,
        )
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<FlowPlaybackSnapshot?>(snapshot)
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            nowMs = { now },
        )

        val job = launch { coordinator.run() }
        runCurrent()
        now += 1_000L
        playback.value = snapshot.copy(positionMs = 21_000L)
        runCurrent()

        assertThat(transport.updates).hasSize(1)
        job.cancelAndJoin()
    }

    private class RecordingTransport : DiscordPresenceTransport {
        override val isAvailable: Boolean = true
        val updates = mutableListOf<DiscordActivity>()
        var clears: Int = 0

        override suspend fun connect(): DiscordConnectionState = DiscordConnectionState.CONNECTED

        override suspend fun update(activity: DiscordActivity) {
            updates += activity
        }

        override suspend fun clear() {
            clears += 1
        }

        override suspend fun disconnect() = Unit
    }
}
