package io.github.aedev.flow.ui.screens.channel

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.shorts.ShortsContentFilter
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
class ChannelViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val subscriptionRepository: SubscriptionRepository = mockk(relaxed = true)
    private val subscriptionGroupDao: SubscriptionGroupDao = mockk(relaxed = true)

    private lateinit var viewModel: ChannelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { subscriptionRepository.getSubscription(any()) } returns flowOf(null)
        coEvery { subscriptionGroupDao.getAllGroups() } returns flowOf(emptyList())
        viewModel =
            ChannelViewModel(
                appContext = context,
                subscriptionRepository = subscriptionRepository,
                shortsContentFilter = ShortsContentFilter(flowOf(true)),
                subscriptionGroupDao = subscriptionGroupDao,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial ui state has default values`() =
        runTest {
            val state = viewModel.uiState.value
            assertThat(state.channelId).isNull()
            assertThat(state.isLoading).isFalse()
            assertThat(state.isSubscribed).isFalse()
            assertThat(state.selectedTab).isEqualTo(0)
        }

    @Test
    fun `selectTab updates selectedTab in uiState`() =
        runTest {
            viewModel.selectTab(2)
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectedTab).isEqualTo(2)
        }

    @Test
    fun `saveScrollPosition retains index and offset`() =
        runTest {
            viewModel.saveScrollPosition(index = 7, offset = 120)

            assertThat(viewModel.listScrollIndex).isEqualTo(7)
            assertThat(viewModel.listScrollOffset).isEqualTo(120)
        }

    @Test
    fun `unsubscribe is a no-op until a channel is loaded`() =
        runTest {
            viewModel.unsubscribe()
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { subscriptionRepository.unsubscribe(any()) }
        }

    @Test
    fun `setNotificationState is a no-op until a channel is loaded`() =
        runTest {
            viewModel.setNotificationState(true)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { subscriptionRepository.updateNotificationState(any(), any()) }
        }

    @Test
    fun `setChannelInGroup adds the channel to the stored member list`() =
        runTest {
            coEvery { subscriptionGroupDao.getAllGroupsOnce() } returns
                listOf(SubscriptionGroupEntity(name = "Music", channelIds = "UC1", sortOrder = 0))

            viewModel.setChannelInGroup("Music", "UC2", inGroup = true)

            coVerify(timeout = GROUP_WRITE_TIMEOUT_MS) {
                subscriptionGroupDao.updateGroup(
                    SubscriptionGroupEntity(name = "Music", channelIds = "UC1,UC2", sortOrder = 0),
                )
            }
        }

    @Test
    fun `setChannelInGroup removes the channel when it is already a member`() =
        runTest {
            coEvery { subscriptionGroupDao.getAllGroupsOnce() } returns
                listOf(SubscriptionGroupEntity(name = "Music", channelIds = "UC1,UC2", sortOrder = 0))

            viewModel.setChannelInGroup("Music", "UC2", inGroup = false)

            coVerify(timeout = GROUP_WRITE_TIMEOUT_MS) {
                subscriptionGroupDao.updateGroup(
                    SubscriptionGroupEntity(name = "Music", channelIds = "UC1", sortOrder = 0),
                )
            }
        }

    @Test
    fun `setChannelInGroup ignores a group that no longer exists`() =
        runTest {
            coEvery { subscriptionGroupDao.getAllGroupsOnce() } returns emptyList()

            viewModel.setChannelInGroup("Gone", "UC2", inGroup = true)

            coVerify(timeout = GROUP_WRITE_TIMEOUT_MS) { subscriptionGroupDao.getAllGroupsOnce() }
            coVerify(exactly = 0) { subscriptionGroupDao.updateGroup(any()) }
        }

    @Test
    fun `createGroupWithChannel refuses a name that already exists`() =
        runTest {
            coEvery { subscriptionGroupDao.exists("Music") } returns true

            viewModel.createGroupWithChannel("Music", "UC2")

            coVerify(timeout = GROUP_WRITE_TIMEOUT_MS) { subscriptionGroupDao.exists("Music") }
            coVerify(exactly = 0) { subscriptionGroupDao.insertGroup(any()) }
        }

    @Test
    fun `createGroupWithChannel stores the trimmed name with the channel as its first member`() =
        runTest {
            coEvery { subscriptionGroupDao.exists(any()) } returns false
            coEvery { subscriptionGroupDao.getAllGroupsOnce() } returns emptyList()

            viewModel.createGroupWithChannel("  Podcasts  ", "UC9")

            coVerify(timeout = GROUP_WRITE_TIMEOUT_MS) {
                subscriptionGroupDao.insertGroup(
                    SubscriptionGroupEntity(name = "Podcasts", channelIds = "UC9", sortOrder = 0),
                )
            }
        }

    private companion object {
        /** The view model writes on PerformanceDispatcher.diskIO, which the test scheduler cannot advance. */
        const val GROUP_WRITE_TIMEOUT_MS = 2_000L
    }
}
