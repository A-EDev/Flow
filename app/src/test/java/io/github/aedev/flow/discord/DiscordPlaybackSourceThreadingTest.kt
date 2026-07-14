package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DiscordPlaybackSourceThreadingTest {

    @Test
    fun `playback snapshots are built on the main player thread when presence collects on IO`() = runBlocking {
        val playerThread = AtomicReference<Thread>()
        val playerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "player-main").also(playerThread::set)
        }
        val presenceExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "presence-io")
        }
        val playerDispatcher = playerExecutor.asCoroutineDispatcher()
        val presenceDispatcher = presenceExecutor.asCoroutineDispatcher()
        val snapshotThread = AtomicReference<Thread>()
        val expectedSnapshot = PlaybackSnapshot(
            kind = PlaybackKind.VIDEO,
            mediaId = "video-id",
            title = "Video title",
            subtitle = "Channel",
            artworkUrl = "",
            positionMs = 0L,
            durationMs = 60_000L,
            isPlaying = true,
            isLive = false,
        )

        try {
            val snapshots = discordPlaybackSnapshotFlow(
                signals = flowOf(Unit),
                snapshotDispatcher = playerDispatcher,
                readSnapshot = {
                    snapshotThread.set(Thread.currentThread())
                    expectedSnapshot
                },
            )

            val actualSnapshot = withContext(presenceDispatcher) {
                withTimeout(5_000L) {
                    snapshots.first()
                }
            }

            assertThat(actualSnapshot).isEqualTo(expectedSnapshot)
            assertThat(snapshotThread.get()).isSameInstanceAs(playerThread.get())
        } finally {
            playerDispatcher.close()
            presenceDispatcher.close()
            playerExecutor.shutdownNow()
            presenceExecutor.shutdownNow()
        }
    }
}
