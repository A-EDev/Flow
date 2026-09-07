package io.github.aedev.flow.ui.components.shared

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.dao.SubscriptionGroupDao
import io.github.aedev.flow.data.model.toUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private const val LABELS_SUBSCRIPTION_TIMEOUT_MS = 5_000L

@HiltViewModel
class ChannelGroupLabelsViewModel
    @Inject
    constructor(
        playerPreferences: PlayerPreferences,
        subscriptionGroupDao: SubscriptionGroupDao,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val labels: StateFlow<ChannelGroupLabels> =
            playerPreferences.showChannelGroupBadges
                .distinctUntilChanged()
                .flatMapLatest { enabled ->
                    // With the preference off nothing observes the group table at all.
                    if (!enabled) {
                        flowOf(ChannelGroupLabels.Empty)
                    } else {
                        subscriptionGroupDao.getAllGroups().map { entities ->
                            val byChannelId = mutableMapOf<String, String>()
                            entities
                                .map { it.toUiModel() }
                                .sortedBy { it.sortOrder }
                                .forEach { group ->
                                    // First group in display order wins, so a channel in several
                                    // groups shows one stable label rather than an arbitrary one.
                                    group.channelIds.forEach { id -> byChannelId.putIfAbsent(id, group.name) }
                                }
                            ChannelGroupLabels(byChannelId)
                        }
                    }
                }.stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(LABELS_SUBSCRIPTION_TIMEOUT_MS),
                    ChannelGroupLabels.Empty,
                )
    }

/**
 * Publishes group labels to every avatar below. Wrap the app content once; nothing else needs to
 * know where the labels come from.
 */
@Composable
fun ProvideChannelGroupLabels(content: @Composable () -> Unit) {
    val activity = LocalContext.current as? ComponentActivity
    val viewModel: ChannelGroupLabelsViewModel =
        if (activity != null) hiltViewModel(activity) else hiltViewModel()
    val labels by viewModel.labels.collectAsStateWithLifecycle()

    CompositionLocalProvider(LocalChannelGroupLabels provides labels, content = content)
}
