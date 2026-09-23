package io.github.aedev.flow.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.NotificationRepository
import io.github.aedev.flow.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationViewModel
    @Inject
    constructor(
        private val repository: NotificationRepository,
    ) : ViewModel() {
        val notifications: StateFlow<List<NotificationEntity>> =
            repository.allNotifications
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val unreadCount: StateFlow<Int> =
            repository.unreadCount
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

        private val _newIds = MutableStateFlow<Set<Int>>(emptySet())

        /** What was unread when the inbox opened; it stays marked new for the rest of the visit. */
        val newIds: StateFlow<Set<Int>> = _newIds.asStateFlow()

        private var inboxOpened = false
        private var lastRemoved: NotificationEntity? = null

        /**
         * Takes the unread snapshot, then clears the badge. Only the inbox route calls this: the
         * activity-scoped instance behind the badge must never mark anything read.
         */
        fun openInbox() {
            if (inboxOpened) return
            inboxOpened = true
            viewModelScope.launch {
                _newIds.value =
                    repository.allNotifications
                        .first()
                        .filterNot { it.isRead }
                        .mapTo(HashSet()) { it.id }
                repository.markAllAsRead()
            }
        }

        fun deleteNotification(notification: NotificationEntity) {
            lastRemoved = notification
            viewModelScope.launch { repository.deleteNotification(notification) }
        }

        fun undoDelete() {
            val notification = lastRemoved ?: return
            lastRemoved = null
            viewModelScope.launch { repository.insertNotification(notification.copy(isRead = true)) }
        }

        fun clearAll() {
            lastRemoved = null
            viewModelScope.launch { repository.clearAll() }
        }
    }
