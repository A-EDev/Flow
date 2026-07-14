package io.github.aedev.flow.discord

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordPlaybackSourceThreadingTest {

    @Test
    fun `video player state is read on the main player thread when presence collects on IO`() = runBlocking {
        val playerExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "player-main")
        }
        val presenceExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "presence-io")
        }
        val playerDispatcher = playerExecutor.asCoroutineDispatcher()
        val presenceDispatcher = presenceExecutor.asCoroutineDispatcher()
        val readThread = AtomicReference<String>()
        val video = Video(
            id = "video-id",
            title = "Video title",
            channelName = "Channel",
            channelId = "channel-id",
            thumbnailUrl = "https://example.com/thumb.jpg",
            duration = 60,
            viewCount = 0L,
            uploadDate = "today",
        )
        val videoManager = mockk<EnhancedPlayerManager>()
        val shortsPool = mockk<ShortsPlayerPool>()

        every { videoManager.playerState } returns MutableStateFlow(
            EnhancedPlayerState(currentVideoId = video.id, isPlaying = true),
        )
        every { videoManager.getCurrentPosition() } answers {
            readThread.set(Thread.currentThread().name)
            5_000L
        }
        every { videoManager.getDuration() } returns 60_000L
        every { shortsPool.currentVideo } returns MutableStateFlow(null)
        every { shortsPool.currentVideoId } returns MutableStateFlow(null)

        Dispatchers.setMain(playerDispatcher)
        GlobalPlayerState.setCurrentVideo(video)
        try {
            val source = DiscordPlaybackSource(
                videoManager = videoManager,
                shortsPool = shortsPool,
            )

            withContext(presenceDispatcher) {
                withTimeout(5_000L) {
                    source.playback.first { it?.mediaId == video.id }
                }
            }

            assertThat(readThread.get()).isEqualTo("player-main")
        } finally {
            GlobalPlayerState.setCurrentVideo(null)
            Dispatchers.resetMain()
            playerDispatcher.close()
            presenceDispatcher.close()
            playerExecutor.shutdownNow()
            presenceExecutor.shutdownNow()
        }
    }
}
