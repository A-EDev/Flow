package io.github.aedev.flow.ui.screens.onboarding

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.backup.BackupCoordinator
import io.github.aedev.flow.data.backup.BackupOperation
import io.github.aedev.flow.data.backup.ImportKind
import io.github.aedev.flow.data.backup.ImportSource
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.paging.ChannelSearch
import io.github.aedev.flow.data.recommendation.OnboardingCompleter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val subscriptions: SubscriptionRepository = mockk(relaxed = true)
    private val backup: BackupCoordinator = mockk(relaxed = true)
    private val operation = MutableStateFlow<BackupOperation>(BackupOperation.Idle)
    private val channelSearch: ChannelSearch = mockk()
    private val completer: OnboardingCompleter = mockk()
    private val preferences: PlayerPreferences = mockk(relaxed = true)
    private val channel = Channel(id = "UCabc", name = "Circuit Bench", thumbnailUrl = "https://t", subscriberCount = 10)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { backup.operation } returns operation
        coEvery { channelSearch.search(any()) } returns listOf(channel)
        coEvery { completer.complete(any()) } returns Unit
        every { preferences.notifNewVideosEnabled } returns flowOf(true)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) =
        OnboardingViewModel(saved, subscriptions, backup, channelSearch, completer, preferences)

    @Test
    fun `a recreated screen resumes on the same step with the same choices`() =
        runTest(dispatcher) {
            val saved = SavedStateHandle()
            viewModel(saved).apply {
                toggleTopic("Jazz")
                toggleSubscription(channel)
                next()
            }

            val restored = viewModel(saved).state.value

            assertThat(restored.step).isEqualTo(OnboardingStep.entries[1])
            assertThat(restored.topics).containsExactly("Jazz")
            assertThat(restored.subscribed.map { it.id }).containsExactly("UCabc")
        }

    @Test
    fun `back on the first step is left to the system`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            assertThat(viewModel.back()).isFalse()
            viewModel.next()
            assertThat(viewModel.back()).isTrue()
            assertThat(viewModel.state.value.step).isEqualTo(OnboardingStep.entries.first())
        }

    @Test
    fun `typing searches once the query settles`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.search("J")
            viewModel.search("Jazz")
            advanceTimeBy(399)
            runCurrent()
            coVerify(exactly = 0) { channelSearch.search(any()) }
            advanceUntilIdle()

            coVerify(exactly = 1) { channelSearch.search("Jazz") }
            assertThat(viewModel.state.value.results).containsExactly(channel)
        }

    @Test
    fun `subscribing stores the channel id the search returned`() =
        runTest(dispatcher) {
            viewModel().toggleSubscription(channel)
            advanceUntilIdle()

            coVerify { subscriptions.subscribe(match { it.channelId == "UCabc" && it.channelName == "Circuit Bench" }) }
        }

    @Test
    fun `finishing seeds the engine once however often it is tapped`() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Unit>()
            coEvery { completer.complete(any()) } coAnswers { gate.await() }
            val viewModel = viewModel()
            viewModel.toggleTopic("Jazz")

            viewModel.complete()
            viewModel.complete()
            runCurrent()
            gate.complete(Unit)
            advanceUntilIdle()

            coVerify(exactly = 1) { completer.complete(setOf("Jazz")) }
            assertThat(viewModel.state.value.completed).isTrue()
        }

    @Test
    fun `a finished import marks the app it came from, a failed one does not`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            runCurrent()

            viewModel.startImport(ImportKind.NEWPIPE_HISTORY, mockk())
            operation.value = BackupOperation.Failed("no")
            runCurrent()
            assertThat(viewModel.state.value.importedSources).isEmpty()

            viewModel.startImport(ImportKind.NEWPIPE_SUBSCRIPTIONS, mockk())
            operation.value = BackupOperation.Succeeded("ok")
            runCurrent()
            assertThat(viewModel.state.value.importedSources).containsExactly(ImportSource.NEWPIPE)
        }
}
