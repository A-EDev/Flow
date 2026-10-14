package io.github.aedev.flow.ui.screens.notifications

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.NotificationRepository
import io.github.aedev.flow.data.local.entity.NotificationEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val unread = NotificationEntity(id = 1, videoId = "a", title = "a", channelName = "c", thumbnailUrl = null, isRead = false)
    private val read = NotificationEntity(id = 2, videoId = "b", title = "b", channelName = "c", thumbnailUrl = null, isRead = true)
    private val repository: NotificationRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.allNotifications } returns flowOf(listOf(unread, read))
        every { repository.unreadCount } returns flowOf(1)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening the inbox keeps what was unread as new and clears the badge once`() {
        val viewModel = NotificationViewModel(repository)

        viewModel.openInbox()
        viewModel.openInbox()

        assertThat(viewModel.newIds.value).containsExactly(1)
        coVerify(exactly = 1) { repository.markAllAsRead() }
    }

    @Test
    fun `the badge instance never marks anything read`() {
        NotificationViewModel(repository)

        coVerify(exactly = 0) { repository.markAllAsRead() }
    }

    @Test
    fun `undo puts the removed notification back as read`() {
        val viewModel = NotificationViewModel(repository)

        viewModel.deleteNotification(unread)
        viewModel.undoDelete()
        viewModel.undoDelete()

        coVerify(exactly = 1) { repository.deleteNotification(unread) }
        coVerify(exactly = 1) { repository.insertNotification(unread.copy(isRead = true)) }
    }
}
