package io.github.aedev.flow.discord

import android.app.Activity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPresenceCoordinatorTest {
    private val mapper = DiscordPresenceMapper(
        appName = "Flow",
        playingFallback = "Playing in Flow",
        creatorLabel = { creator -> "by $creator" },
    )

    @Test
    fun `disabled setting clears presence`() = runTest {
        val enabled = MutableStateFlow(false)
        val playback = MutableStateFlow<PlaybackSnapshot?>(null)
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            mapper = mapper,
            nowEpochSeconds = { 1_000L },
            nowElapsedMs = { 1_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()

        assertThat(transport.clears).isEqualTo(1)
        assertThat(transport.disconnects).isEqualTo(0)
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
            mapper = mapper,
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
            mapper = mapper,
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

    @Test
    fun `failed transport update is retried`() = runTest {
        val snapshot = PlaybackSnapshot(
            kind = PlaybackKind.VIDEO,
            mediaId = "video-1",
            title = "Video",
            subtitle = "Creator",
            artworkUrl = "",
            durationMs = 60_000L,
            positionMs = 1_000L,
            isPlaying = true,
            isLive = false,
        )
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(snapshot)
        val transport = RecordingTransport().apply { failNextUpdate = true }
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            mapper = mapper,
            nowEpochSeconds = { 100L },
            nowElapsedMs = { 10_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()
        playback.value = snapshot.copy(positionMs = 2_000L)
        runCurrent()

        assertThat(transport.updateAttempts).isEqualTo(2)
        assertThat(transport.updates).hasSize(1)
        job.cancelAndJoin()
    }

    @Test
    fun `playback lifecycle stop clears previously published presence`() = runTest {
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(
            PlaybackSnapshot(
                kind = PlaybackKind.VIDEO,
                mediaId = "video-1",
                title = "Video",
                subtitle = "Creator",
                artworkUrl = "",
                durationMs = 60_000L,
                positionMs = 1_000L,
                isPlaying = true,
                isLive = false,
            ),
        )
        val transport = RecordingTransport()
        val coordinator = DiscordPresenceCoordinator(
            enabled = enabled,
            playback = playback,
            transport = transport,
            mapper = mapper,
            nowEpochSeconds = { 100L },
            nowElapsedMs = { 10_000L },
        )

        val job = launch { coordinator.run() }
        runCurrent()
        playback.value = null
        runCurrent()

        assertThat(transport.updates).hasSize(1)
        assertThat(transport.clears).isEqualTo(1)
        assertThat(transport.disconnects).isEqualTo(0)
        job.cancelAndJoin()
    }

    @Test
    fun `transient playback gap keeps gateway connected for next media`() = runTest {
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(snapshot("video-1"))
        val transport = RecordingTransport()
        val coordinator = coordinator(enabled, playback, transport)

        val job = launch { coordinator.run() }
        runCurrent()
        playback.value = null
        runCurrent()
        playback.value = snapshot("video-2")
        runCurrent()

        assertThat(transport.updates.map(DiscordPresencePayload::mediaId))
            .containsExactly("video-1", "video-2")
            .inOrder()
        assertThat(transport.clears).isEqualTo(1)
        assertThat(transport.disconnects).isEqualTo(0)
        job.cancelAndJoin()
    }

    @Test
    fun `rapid media change does not cancel in flight transport update`() = runTest {
        val enabled = MutableStateFlow(true)
        val playback = MutableStateFlow<PlaybackSnapshot?>(snapshot("video-1"))
        val updateGate = CompletableDeferred<Unit>()
        val transport = RecordingTransport().apply {
            blockNextUpdate = updateGate
        }
        val coordinator = coordinator(enabled, playback, transport)

        val job = launch { coordinator.run() }
        runCurrent()
        playback.value = snapshot("video-2")
        runCurrent()
        updateGate.complete(Unit)
        runCurrent()

        assertThat(transport.cancelledUpdates).isEqualTo(0)
        assertThat(transport.updates.map(DiscordPresencePayload::mediaId))
            .containsExactly("video-1", "video-2")
            .inOrder()
        job.cancelAndJoin()
    }

    private fun coordinator(
        enabled: MutableStateFlow<Boolean>,
        playback: MutableStateFlow<PlaybackSnapshot?>,
        transport: DiscordPresenceTransport,
    ) = DiscordPresenceCoordinator(
        enabled = enabled,
        playback = playback,
        transport = transport,
        mapper = mapper,
        nowEpochSeconds = { 100L },
        nowElapsedMs = { 10_000L },
    )

    private fun snapshot(mediaId: String) = PlaybackSnapshot(
        kind = PlaybackKind.VIDEO,
        mediaId = mediaId,
        title = mediaId,
        subtitle = "Creator",
        artworkUrl = "",
        durationMs = 60_000L,
        positionMs = 1_000L,
        isPlaying = true,
        isLive = false,
    )

    private class RecordingTransport : DiscordPresenceTransport {
        override val isAvailable: Boolean = true
        override val connectionState = MutableStateFlow(DiscordConnectionState.CONNECTED)
        override val linkedAccountName = MutableStateFlow<String?>(null)
        override val lastError = MutableStateFlow<String?>(null)
        val updates = mutableListOf<DiscordPresencePayload>()
        var updateAttempts = 0
        var failNextUpdate = false
        var disconnects: Int = 0
        var clears: Int = 0
        var cancelledUpdates: Int = 0
        var blockNextUpdate: CompletableDeferred<Unit>? = null

        override fun attachActivity(activity: Activity?) = Unit

        override suspend fun link(): DiscordLinkResult = DiscordLinkResult.Success

        override suspend fun connect(tokens: DiscordAuthTokens): DiscordLinkResult = DiscordLinkResult.Success

        override suspend fun update(payload: DiscordPresencePayload): Boolean {
            updateAttempts += 1
            blockNextUpdate?.let { blocker ->
                blockNextUpdate = null
                try {
                    blocker.await()
                } catch (error: CancellationException) {
                    cancelledUpdates += 1
                    throw error
                }
            }
            if (failNextUpdate) {
                failNextUpdate = false
                return false
            }
            updates += payload
            return true
        }

        override suspend fun clear(): Boolean {
            clears += 1
            return true
        }

        override suspend fun disconnect(): Boolean {
            disconnects += 1
            return true
        }

        override suspend fun unlink(): Boolean = true

        override fun close() = Unit
    }
}
