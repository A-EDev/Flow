package io.github.aedev.flow.ui.screens.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.dao.CacheDao
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
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
    private val cacheDao: CacheDao = mockk(relaxed = true)
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
        coEvery { cacheDao.getSubscriptionFeed() } returns flowOf(emptyList())

        viewModel = SubscriptionsViewModel(
            subscriptionRepository = subscriptionRepository,
            viewHistory = viewHistory,
            cacheDao = cacheDao,
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

    @Test
    fun `initial ui state has default properties`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.selectedGroupName).isNull()
        assertThat(state.sortMode).isEqualTo(SubscriptionSortMode.DEFAULT)
    }

    @Test
    fun `selectGroup updates selectedGroupName in state`() = runTest {
        viewModel.selectGroup("Tech")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedGroupName).isEqualTo("Tech")
    }

    @Test
    fun `selectChannel updates selectedChannelId in state`() = runTest {
        viewModel.selectChannel("channel_123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChannelId).isEqualTo("channel_123")
    }

    @Test
    fun `unsubscribe calls subscription repository`() = runTest {
        val channelId = "channel_abc"
        coEvery { subscriptionRepository.unsubscribe(channelId) } returns Unit

        viewModel.unsubscribe(channelId)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { subscriptionRepository.unsubscribe(channelId) }
    }

    @Test
    fun `updateNotificationState calls subscription repository`() = runTest {
        val channelId = "channel_xyz"
        coEvery { subscriptionRepository.updateNotificationState(channelId, true) } returns Unit

        viewModel.updateNotificationState(channelId, true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { subscriptionRepository.updateNotificationState(channelId, true) }
    }
}
