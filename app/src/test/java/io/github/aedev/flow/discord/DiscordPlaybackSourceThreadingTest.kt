package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPlaybackSourceThreadingTest {

    @Test
    fun `playback snapshots are built on the main player thread when presence collects on IO`() = runBlocking {
        val playerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "player-main")
        }
        val presenceExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "presence-io")
        }
        val playerDispatcher = playerExecutor.asCoroutineDispatcher()
        val presenceDispatcher = presenceExecutor.asCoroutineDispatcher()
        val snapshotThread = AtomicReference<String>()
        val selector = mockk<DiscordPlaybackSelector>()
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
        every { selector.select(any(), any(), any()) } answers {
            snapshotThread.set(Thread.currentThread().name)
            expectedSnapshot
        }

        Dispatchers.setMain(playerDispatcher)
        try {
            val source = DiscordPlaybackSource(selector = selector)

            withContext(presenceDispatcher) {
                withTimeout(5_000L) {
                    source.playback.first()
                }
            }

            assertThat(snapshotThread.get()).isEqualTo("player-main")
        } finally {
            Dispatchers.resetMain()
            playerDispatcher.close()
            presenceDispatcher.close()
            playerExecutor.shutdownNow()
            presenceExecutor.shutdownNow()
        }
    }
}
