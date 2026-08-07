package io.github.aedev.flow.ui.screens.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionRefreshPlan
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    private val subscriptionRepository: SubscriptionRepository = mockk(relaxed = true)
    private val viewHistory: ViewHistory = mockk(relaxed = true)
    private val subscriptionFeedRepository: SubscriptionFeedRepository = mockk(relaxed = true)
    private val database: AppDatabase = mockk(relaxed = true)
    private val playerPreferences: PlayerPreferences = mockk(relaxed = true)
    private val subscriptionGroupDao: SubscriptionGroupDao = mockk(relaxed = true)

    private lateinit var viewModel: SubscriptionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { subscriptionGroupDao.getAllGroups() } returns flowOf(emptyList())
        coEvery { playerPreferences.shortsShelfEnabled } returns flowOf(false)
        coEvery { playerPreferences.subscriptionShowVideos } returns flowOf(true)
        coEvery { playerPreferences.subscriptionShowShorts } returns flowOf(true)
        coEvery { playerPreferences.subscriptionShowLive } returns flowOf(true)
        coEvery { playerPreferences.subscriptionShortsExcludedChannels } returns flowOf(emptySet())
        coEvery { playerPreferences.subsFullWidthView } returns flowOf(false)
        coEvery { playerPreferences.subsSortMode } returns flowOf("DEFAULT")
        coEvery { playerPreferences.selectedSubscriptionGroup } returns flowOf(null)
        coEvery { playerPreferences.subscriptionLastRefreshTime } returns flowOf(0L)
        coEvery { playerPreferences.subscriptionLastRefreshedCount } returns flowOf(0)
        coEvery { playerPreferences.subscriptionShowCheckedVideoCount } returns flowOf(true)
        coEvery { viewHistory.getVideoHistoryFlow() } returns flowOf(emptyList())
        coEvery { playerPreferences.hideWatchedVideosFromSubscriptions } returns flowOf(false)
        coEvery { playerPreferences.watchedThreshold } returns flowOf(mockk(relaxed = true))
        coEvery { database.downloadDao().getVideoDownloads() } returns flowOf(emptyList())
        coEvery { playerPreferences.unplayableVideoIds } returns flowOf(emptySet())
        coEvery { playerPreferences.hideUnplayableVideosFromSubscriptions } returns flowOf(false)
        coEvery { subscriptionRepository.getAllSubscriptions() } returns flowOf(emptyList())
        coEvery { subscriptionFeedRepository.observeFeed() } returns flowOf(emptyList())
        coEvery { subscriptionFeedRepository.planRefresh(any()) } returns SubscriptionRefreshPlan.NOTHING_TO_DO

        viewModel =
            SubscriptionsViewModel(
                subscriptionRepository = subscriptionRepository,
                subscriptionFeedRepository = subscriptionFeedRepository,
                viewHistory = viewHistory,
                database = database,
                playerPreferences = playerPreferences,
                subscriptionGroupDao = subscriptionGroupDao,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    /**
     * Regression guard: the TV shell hoists this ViewModel at app launch, so merely constructing it
     * must not read preferences or kick off the subscription feed fetch. That work belongs to
     * [SubscriptionsViewModel.ensureStarted], which the screens call when the feed becomes visible.
     */
    @Test
    fun `construction does not start collectors or touch the feed`() =
        runTest {
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { subscriptionRepository.getAllSubscriptions() }
            coVerify(exactly = 0) { subscriptionGroupDao.getAllGroups() }
            coVerify(exactly = 0) { subscriptionFeedRepository.observeFeed() }
        }

    @Test
    fun `ensureStarted begins collecting subscriptions`() =
        runTest {
            viewModel.ensureStarted()

            coVerify(timeout = VERIFY_TIMEOUT_MS) { subscriptionRepository.getAllSubscriptions() }
            coVerify(timeout = VERIFY_TIMEOUT_MS) { subscriptionGroupDao.getAllGroups() }
        }

    @Test
    fun `ensureStarted is idempotent`() =
        runTest {
            viewModel.ensureStarted()
            coVerify(timeout = VERIFY_TIMEOUT_MS) { subscriptionGroupDao.getAllGroups() }

            viewModel.ensureStarted()
            viewModel.ensureStarted()

            coVerify(exactly = 1) { subscriptionGroupDao.getAllGroups() }
        }

    @Test
    fun `initial ui state has default properties`() =
        runTest {
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.selectedGroupName).isNull()
            assertThat(state.sortMode).isEqualTo(SubscriptionSortMode.DEFAULT)
        }

    @Test
    fun `selectGroup updates selectedGroupName in state`() =
        runTest {
            viewModel.selectGroup("Tech")

            assertThat(viewModel.uiState.value.selectedGroupName).isEqualTo("Tech")
        }

    @Test
    fun `selectChannel updates selectedChannelId in state`() =
        runTest {
            viewModel.selectChannel("channel_123")

            assertThat(viewModel.uiState.value.selectedChannelId).isEqualTo("channel_123")
        }

    @Test
    fun `unsubscribe calls subscription repository`() =
        runTest {
            val channelId = "channel_abc"
            coEvery { subscriptionRepository.unsubscribe(channelId) } returns Unit

            viewModel.unsubscribe(channelId)

            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { subscriptionRepository.unsubscribe(channelId) }
        }

    @Test
    fun `updateNotificationState calls subscription repository`() =
        runTest {
            val channelId = "channel_xyz"
            coEvery { subscriptionRepository.updateNotificationState(channelId, true) } returns Unit

            viewModel.updateNotificationState(channelId, true)

            coVerify(timeout = VERIFY_TIMEOUT_MS, exactly = 1) { subscriptionRepository.updateNotificationState(channelId, true) }
        }

    private companion object {
        /**
         * The ViewModel launches on [io.github.aedev.flow.utils.PerformanceDispatcher] rather than
         * on an injected dispatcher, so the test scheduler cannot join those coroutines. Verify
         * with a timeout instead of racing them.
         */
        const val VERIFY_TIMEOUT_MS = 2_000L
    }
}
