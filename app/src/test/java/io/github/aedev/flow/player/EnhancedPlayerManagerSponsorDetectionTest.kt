package io.github.aedev.flow.player

import android.os.Looper
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.data.repository.SponsorBlockRepository
import io.github.aedev.flow.data.sponsordetection.ON_DEVICE_UNAVAILABLE_MESSAGE
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionCoordinator
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionLoadResult
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionStatus
import io.github.aedev.flow.data.sponsordetection.SponsorDetectionUiState
import io.github.aedev.flow.data.sponsordetection.SponsorModelReadiness
import io.github.aedev.flow.data.sponsordetection.SponsorPlaybackSettings
import io.github.aedev.flow.data.sponsordetection.SponsorPredictedSpan
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

@ConscryptMode(ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EnhancedPlayerManagerSponsorDetectionTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkConstructor(SponsorBlockRepository::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `cached VOD segments still trigger on-device evaluation without refetch`() =
        runTest {
            assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
            val cachedSegments = listOf(SponsorBlockSegment("sponsor", listOf(12f, 18f), "cached"))
            val coordinator = mockk<SponsorDetectionCoordinator>()
            val handler = mockk<SponsorBlockHandler>(relaxed = true)
            every { handler.getSegments() } returns cachedSegments
            coEvery {
                coordinator.evaluate("video", any(), cachedSegments)
            } returns SponsorDetectionLoadResult(cachedSegments, cachedSegments)
            mockkConstructor(SponsorBlockRepository::class)
            every {
                anyConstructed<SponsorBlockRepository>().getCachedSegments("video")
            } returns cachedSegments

            val manager = newUninitializedManager()
            setField(manager, "sponsorDetectionCoordinator", coordinator)
            setField(manager, "sponsorBlockHandler", handler)
            setField(manager, "sponsorOnlineEnabled", true)
            setField(manager, "sponsorOnDeviceEnabled", true)

            try {
                manager.setStreams(
                    videoId = "video",
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                )

                coVerify(exactly = 1) { coordinator.evaluate("video", any(), cachedSegments) }
                verify(exactly = 1) { handler.loadSegmentsFromList("video", cachedSegments) }
                verify(exactly = 0) { handler.loadSegments("video") }
                assertThat(manager.getPlayer()).isNull()
            } finally {
                (getField(manager, "scope") as kotlinx.coroutines.CoroutineScope).cancel()
            }
        }

    @Test
    fun `online disabled no-cache VOD still runs model and applies current video segments`() =
        runTest {
            assertThat(Looper.myLooper()).isSameInstanceAs(Looper.getMainLooper())
            val modelSegments = listOf(SponsorBlockSegment("flow-ml:span", listOf(12f, 18f), "sponsor"))
            val coordinator = mockk<SponsorDetectionCoordinator>()
            val handler = mockk<SponsorBlockHandler>(relaxed = true)
            coEvery {
                coordinator.evaluate("video", any(), emptyList())
            } returns SponsorDetectionLoadResult(modelSegments, emptyList())
            mockkConstructor(SponsorBlockRepository::class)
            every { anyConstructed<SponsorBlockRepository>().getCachedSegments("video") } returns null

            val manager = newUninitializedManager()
            setField(manager, "sponsorDetectionCoordinator", coordinator)
            setField(manager, "sponsorBlockHandler", handler)
            setField(manager, "sponsorOnlineEnabled", false)
            setField(manager, "sponsorOnDeviceEnabled", true)

            try {
                manager.setStreams(
                    videoId = "video",
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                )

                coVerify(exactly = 1) { coordinator.evaluate("video", any(), emptyList()) }
                verify(exactly = 0) { handler.loadSegments("video") }
                verify(exactly = 1) { handler.loadSegmentsFromList("video", modelSegments) }
            } finally {
                (getField(manager, "scope") as kotlinx.coroutines.CoroutineScope).cancel()
            }
        }

    @Test
    fun `final empty model result removes provisional spans`() =
        runTest {
            val coordinator = mockk<SponsorDetectionCoordinator>()
            val handler = mockk<SponsorBlockHandler>(relaxed = true)
            every { handler.getSegments() } returns listOf(SponsorBlockSegment("flow-ml:provisional", listOf(4f, 5f), "sponsor"))
            coEvery { coordinator.evaluate("video", any(), emptyList()) } returns SponsorDetectionLoadResult(emptyList(), emptyList())
            val manager = newUninitializedManager()
            setField(manager, "sponsorDetectionCoordinator", coordinator)
            setField(manager, "sponsorBlockHandler", handler)
            setField(manager, "sponsorOnDeviceEnabled", true)
            try {
                manager.setStreams(
                    videoId = "video",
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                )
                verify(exactly = 1) { handler.loadSegmentsFromList("video", emptyList()) }
            } finally {
                (getField(manager, "scope") as kotlinx.coroutines.CoroutineScope).cancel()
            }
        }

    @Test
    fun `installed model retries only a skipped current video`() =
        runTest {
            val modelSegments = listOf(SponsorBlockSegment("flow-ml:span", listOf(4f, 5f), "sponsor"))
            val coordinator = mockk<SponsorDetectionCoordinator>()
            val handler = mockk<SponsorBlockHandler>(relaxed = true)
            every {
                coordinator.state
            } returns
                MutableStateFlow(
                    SponsorDetectionUiState(
                        videoId = "video",
                        status = SponsorDetectionStatus.SKIPPED,
                        apiSegments = emptyList(),
                        errorMessage = ON_DEVICE_UNAVAILABLE_MESSAGE,
                    ),
                )
            coEvery {
                coordinator.evaluate("video", any(), emptyList())
            } returns SponsorDetectionLoadResult(modelSegments, emptyList())

            val manager = newUninitializedManager()
            setField(manager, "sponsorDetectionCoordinator", coordinator)
            setField(manager, "sponsorBlockHandler", handler)
            setField(manager, "currentVideoId", "video")
            setField(manager, "sponsorOnlineEnabled", false)
            setField(manager, "sponsorOnDeviceEnabled", true)
            setField(manager, "sponsorModelInstalled", false)

            try {
                applySponsorSettings(manager, SponsorPlaybackSettings(false, SponsorModelReadiness(true, true)))

                coVerify(exactly = 1) { coordinator.evaluate("video", any(), emptyList()) }
                verify(exactly = 1) { handler.loadSegmentsFromList("video", modelSegments) }
            } finally {
                (getField(manager, "scope") as kotlinx.coroutines.CoroutineScope).cancel()
            }
        }

    @Test
    fun `model readiness arriving during a skipped evaluation triggers one retry after it settles`() =
        runTest {
            val firstStarted = CompletableDeferred<Unit>()
            val finishFirst = CompletableDeferred<Unit>()
            val state = MutableStateFlow(SponsorDetectionUiState())
            val coordinator = mockk<SponsorDetectionCoordinator>()
            val handler = mockk<SponsorBlockHandler>(relaxed = true)
            val modelSegments = listOf(SponsorBlockSegment("flow-ml:span", listOf(4f, 5f), "sponsor"))
            var evaluations = 0
            every { coordinator.state } returns state
            coEvery {
                coordinator.evaluate("video", any(), emptyList())
            } coAnswers {
                evaluations++
                if (evaluations == 1) {
                    firstStarted.complete(Unit)
                    finishFirst.await()
                    SponsorDetectionLoadResult(emptyList(), emptyList())
                } else {
                    SponsorDetectionLoadResult(modelSegments, emptyList())
                }
            }
            mockkConstructor(SponsorBlockRepository::class)
            every { anyConstructed<SponsorBlockRepository>().getCachedSegments("video") } returns null

            val manager = newUninitializedManager()
            setField(manager, "sponsorDetectionCoordinator", coordinator)
            setField(manager, "sponsorBlockHandler", handler)
            setField(manager, "sponsorOnlineEnabled", false)
            setField(manager, "sponsorOnDeviceEnabled", true)
            setField(manager, "sponsorModelInstalled", false)

            try {
                manager.setStreams(
                    videoId = "video",
                    videoStream = null,
                    audioStream = null,
                    videoStreams = emptyList(),
                    audioStreams = emptyList(),
                    subtitles = emptyList(),
                )
                firstStarted.await()
                state.value = SponsorDetectionUiState(videoId = "video", status = SponsorDetectionStatus.LOADING)
                applySponsorSettings(manager, SponsorPlaybackSettings(false, SponsorModelReadiness(true, true)))
                state.value =
                    SponsorDetectionUiState(
                        videoId = "video",
                        status = SponsorDetectionStatus.SKIPPED,
                        errorMessage = ON_DEVICE_UNAVAILABLE_MESSAGE,
                    )
                invokeReadinessRetry(manager, state.value)

                finishFirst.complete(Unit)
                runCurrent()

                assertThat(evaluations).isEqualTo(2)
                verify(exactly = 1) { handler.loadSegmentsFromList("video", modelSegments) }
            } finally {
                (getField(manager, "scope") as kotlinx.coroutines.CoroutineScope).cancel()
            }
        }

    private fun newUninitializedManager(): EnhancedPlayerManager {
        val constructor = EnhancedPlayerManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
    }

    private fun setField(
        target: Any,
        name: String,
        value: Any,
    ) {
        target.javaClass.getDeclaredField(name).apply {
            isAccessible = true
            set(target, value)
        }
    }

    private fun getField(
        target: Any,
        name: String,
    ): Any =
        target.javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(target)
        }

    private fun applySponsorSettings(
        manager: EnhancedPlayerManager,
        settings: SponsorPlaybackSettings,
    ) {
        manager.javaClass
            .getDeclaredMethod("applySponsorPlaybackSettings", SponsorPlaybackSettings::class.java)
            .apply { isAccessible = true }
            .invoke(manager, settings)
    }

    private fun invokeReadinessRetry(
        manager: EnhancedPlayerManager,
        state: SponsorDetectionUiState,
    ) {
        manager.javaClass
            .getDeclaredMethod("retrySkippedSponsorEvaluation", SponsorDetectionUiState::class.java)
            .apply { isAccessible = true }
            .invoke(manager, state)
    }
}
