package io.github.aedev.flow.ui.screens.recap

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SearchHistoryRepository
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import io.github.aedev.flow.data.stats.RecapImageResolver
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.VideoMonthRecord
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.data.stats.VideoStatsSnapshot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {
    private val august = YearMonth.of(2026, 8)
    private val september = YearMonth.of(2026, 9)
    private val gate = CompletableDeferred<Unit>()
    private val videoStats: VideoStatsRecorder = mockk()
    private val musicBrain: MusicBrainEngine = mockk()
    private val searchHistory: SearchHistoryRepository = mockk()
    private val images: RecapImageResolver = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val month = VideoMonthRecord(views = 3, watchedMs = 60_000L)
        coEvery { videoStats.snapshot() } coAnswers {
            gate.await()
            VideoStatsSnapshot(months = mapOf("2026-08" to month, "2026-09" to month))
        }
        coEvery { musicBrain.listeningStats() } returns MusicStatsStorage.SerializableStats()
        every { searchHistory.isAutoDeleteHistoryEnabledFlow() } returns flowOf(false)
        coEvery { images.localImages() } returns emptyMap()
        coEvery { images.fetchMissing(any()) } returns emptyMap()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RecapViewModel(videoStats, musicBrain, mockk(relaxed = true), searchHistory, images)

    @Test
    fun `a period asked for while the ledgers load wins over the newest month`() =
        runBlocking {
            val viewModel = viewModel()
            viewModel.openAt(RecapPeriod.Month(august), RecapSource.ALL)
            gate.complete(Unit)

            val state = withTimeout(TIMEOUT_MS) { viewModel.state.first { !it.loading } }
            assertThat(state.period).isEqualTo(RecapPeriod.Month(august))
            assertThat(state.summary?.period).isEqualTo(RecapPeriod.Month(august))
        }

    @Test
    fun `without a request the newest month opens`() =
        runBlocking {
            gate.complete(Unit)
            val state = withTimeout(TIMEOUT_MS) { viewModel().state.first { !it.loading } }
            assertThat(state.period).isEqualTo(RecapPeriod.Month(september))
        }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
