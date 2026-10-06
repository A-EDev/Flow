package io.github.aedev.flow.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import io.github.aedev.flow.player.media.MediaLoader
import io.github.aedev.flow.player.sabr.integration.SabrOrchestrator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class EnhancedPlayerManagerSabrSeekTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var manager: EnhancedPlayerManager
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val loader = mockk<MediaLoader>(relaxed = true)
    private val session = mockk<SabrOrchestrator>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manager =
            EnhancedPlayerManager::class.java.getDeclaredConstructor().run {
                isAccessible = true
                newInstance()
            }
        setField("player", player)
        setField("mediaLoader", loader)
        setField("currentVideoId", "video")
        every { player.duration } returns 180_000L
        every { player.isCurrentMediaItemLive } returns false
        every { loader.getActiveSabrOrchestrator() } returns session
    }

    @After
    fun tearDown() {
        (field("scope") as CoroutineScope).cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid seeks rebuild only once`() =
        runTest(dispatcher) {
            manager.seekTo(10_000L)
            manager.seekTo(20_000L)
            manager.seekTo(30_000L)
            runCurrent()
            verify(exactly = 1) { player.stop() }
            verify(exactly = 1) { loader.releaseSabr() }
        }

    @Test
    fun `explicit direct seek uses exact parameters then restores ordinary policy`() =
        runTest(dispatcher) {
            every { loader.getActiveSabrOrchestrator() } returns null
            every { player.seekParameters } returns SeekParameters.CLOSEST_SYNC

            manager.seekTo(65_000L, exact = true)

            verifyOrder {
                player.setSeekParameters(SeekParameters.EXACT)
                player.seekTo(65_000L)
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
        }

    @Test
    fun `ordinary direct seek keeps closest sync policy`() =
        runTest(dispatcher) {
            every { loader.getActiveSabrOrchestrator() } returns null
            every { player.seekParameters } returns SeekParameters.CLOSEST_SYNC

            manager.seekTo(65_000L)

            verify(exactly = 1) { player.seekTo(65_000L) }
            verify(exactly = 0) { player.setSeekParameters(any()) }
        }

    @Test
    fun `explicit SABR seek applies exact parameters during the queued rebuild`() =
        runTest(dispatcher) {
            every { player.seekParameters } returns SeekParameters.CLOSEST_SYNC

            manager.seekTo(65_000L, exact = true)
            runCurrent()

            verifyOrder {
                player.setSeekParameters(SeekParameters.EXACT)
                player.stop()
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
        }

    @Test
    fun `closing the video cancels a queued rebuild`() =
        runTest(dispatcher) {
            manager.seekTo(30_000L)
            manager.clearCurrentVideo()
            runCurrent()
            verify(exactly = 1) { player.stop() }
            verify(exactly = 1) { loader.releaseSabr() }
        }

    @Test
    fun `a newer end boundary seek cancels a queued rebuild`() =
        runTest(dispatcher) {
            manager.seekTo(30_000L)
            manager.seekTo(180_000L)
            runCurrent()
            verify(exactly = 0) { player.stop() }
            verify(exactly = 1) { player.seekTo(180_000L) }
        }

    @Test
    fun `a queued seek cannot stop a newer video`() =
        runTest(dispatcher) {
            manager.seekTo(30_000L)
            setField("currentVideoId", "next-video")
            runCurrent()
            verify(exactly = 0) { player.stop() }
            verify(exactly = 0) { loader.releaseSabr() }
        }

    @Test
    fun `a queued seek cannot stop a replacement session for the same video`() =
        runTest(dispatcher) {
            manager.seekTo(30_000L)
            every { loader.getActiveSabrOrchestrator() } returns mockk<SabrOrchestrator>()
            runCurrent()
            verify(exactly = 0) { player.stop() }
        }

    private fun setField(
        name: String,
        value: Any,
    ) {
        manager.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(manager, value)
        }
    }

    private fun field(name: String): Any =
        manager.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(manager)
        }
}
