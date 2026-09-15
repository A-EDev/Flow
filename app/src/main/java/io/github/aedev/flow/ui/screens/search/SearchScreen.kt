package io.github.aedev.flow.ui.screens.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.FEED_MAX_AUTO_COLUMNS
import io.github.aedev.flow.ui.components.QuickActionsViewModel
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.search.SearchFilterBar
import io.github.aedev.flow.ui.components.search.SearchFilterDialog
import io.github.aedev.flow.ui.components.search.SearchResultActions
import io.github.aedev.flow.ui.components.search.SearchResults
import io.github.aedev.flow.ui.components.search.SearchResultsShimmer
import io.github.aedev.flow.ui.components.search.SearchShortsGrid
import io.github.aedev.flow.ui.components.search.SearchSuggestionsPanel
import io.github.aedev.flow.ui.components.search.SearchTopBar
import io.github.aedev.flow.ui.components.search.SearchTopBarActions
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.utils.videoIdFromUrl

/**
 * The route: a bar, then either what the user might be looking for or what they found.
 *
 * Typing is a mode of this screen rather than a surface stacked on it, so back always leaves —
 * it never has a collapse step to fall into first.
 */
@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onShortsQueue: (ShortsQueueSource) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val state = rememberSearchState(viewModel)

    val quickActions: QuickActionsViewModel = hiltViewModel()
    val subscribedIds by quickActions.subscribedChannelIds.collectAsStateWithLifecycle()
    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val submit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            val videoId = videoIdFromUrl(text)
            if (videoId != null) {
                onVideoClick(sharedVideo(videoId, context.getString(R.string.shared_video)))
            } else {
                state.onSubmit(text)
                viewModel.search(text, uiState.filters)
            }
        }
    }

    val voiceSearchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf(String::isNotBlank)
                ?.let { spoken ->
                    state.textFieldState.setTextAndPlaceCursorAtEnd(spoken)
                    submit(spoken)
                }
        }

    val showResults = uiState.query.isNotBlank() && !state.isTyping
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(showResults) {
        if (showResults) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        } else {
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) gridState.scrollToItem(0)
    }

    LaunchedEffect(pagingItems.itemSnapshotList.items) {
        pagingItems.itemSnapshotList.items
            .filterIsInstance<SearchResultItem.ChannelResult>()
            .forEach { quickActions.loadSubscriptionState(it.channel.id) }
    }

    // Back leaves the screen, but a query typed over a finished search returns to that search first.
    BackHandler(enabled = state.isTyping && uiState.query.isNotBlank()) { state.stopTyping() }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchTopBar(
                textFieldState = state.textFieldState,
                onSearch = submit,
                onBack = onBack,
                onVoiceSearch = { launchVoiceSearch(context, voiceSearchLauncher::launch) },
                focusRequester = focusRequester,
                onFieldFocused = state::startTyping,
                actions =
                    if (showResults) {
                        {
                            SearchTopBarActions(
                                activeFilterCount = uiState.filters.activeCount,
                                isGridMode = state.isGridMode,
                                onOpenFilters = { showFilters = true },
                                onToggleGridMode = state::toggleGridMode,
                            )
                        }
                    } else {
                        null
                    },
            )

            if (!showResults) {
                SearchSuggestionsPanel(
                    query = state.query,
                    history = state.matchingHistory,
                    suggestions = state.suggestions,
                    onSubmit = { text ->
                        state.textFieldState.setTextAndPlaceCursorAtEnd(text)
                        submit(text)
                    },
                    onFill = state.textFieldState::setTextAndPlaceCursorAtEnd,
                    onDeleteHistoryItem = state::deleteHistoryItem,
                    onClearHistory = state::clearHistory,
                )
                return@Column
            }

            SearchFilterBar(
                selected = uiState.filters.contentType,
                shortsEnabled = state.shortsContentEnabled,
                onContentTypeSelected = { viewModel.updateFilters(uiState.filters.copy(contentType = it)) },
                modifier = Modifier.padding(vertical = FilterBarVerticalPadding),
            )

            val refreshState = pagingItems.loadState.refresh
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val feedLayout = rememberFeedGridLayout(maxWidth, state.feedColumns, FEED_MAX_AUTO_COLUMNS)
                val actions =
                    remember(feedLayout, subscribedIds) {
                        SearchResultActions(
                            onVideoClick = onVideoClick,
                            onShortsClick = { shelf, tapped ->
                                onShortsQueue(viewModel.shortsShelfSource(shelf, tapped))
                            },
                            onChannelClick = onChannelClick,
                            onPlaylistClick = onPlaylistClick,
                            dismissKeyboard = state::stopTyping,
                            isSubscribed = { it in subscribedIds },
                            onSubscribeToggle = { channel ->
                                quickActions.toggleSubscription(channel.id, channel.name, channel.thumbnailUrl)
                            },
                        )
                    }

                when {
                    refreshState is LoadState.Loading -> {
                        SearchResultsShimmer(state.isGridMode, feedLayout)
                    }

                    refreshState is LoadState.Error && pagingItems.itemCount == 0 -> {
                        FlowErrorState(
                            error = refreshState.error.localizedMessage ?: stringResource(R.string.search_failed),
                            onRetry = pagingItems::retry,
                        )
                    }

                    pagingItems.itemCount == 0 -> {
                        FlowEmptyState(
                            title = stringResource(R.string.no_results_found),
                            icon = Icons.Rounded.Search,
                        )
                    }

                    uiState.filters.contentType == ContentType.SHORTS -> {
                        SearchShortsGrid(pagingItems, gridState, actions)
                    }

                    else -> {
                        SearchResults(pagingItems, gridState, feedLayout, state.isGridMode, actions)
                    }
                }
            }
        }
    }

    if (showFilters) {
        SearchFilterDialog(
            filter = uiState.filters,
            shortsEnabled = state.shortsContentEnabled,
            onApply = {
                showFilters = false
                viewModel.updateFilters(it)
            },
            onDismiss = { showFilters = false },
        )
    }
}

private fun launchVoiceSearch(
    context: Context,
    launch: (Intent) -> Unit,
) {
    val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.search_voice_prompt))
        }
    try {
        launch(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun sharedVideo(
    videoId: String,
    title: String,
) = Video(
    id = videoId,
    title = title,
    channelName = title,
    channelId = "",
    thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
    duration = 0,
    viewCount = 0L,
    uploadDate = "",
)

private val FilterBarVerticalPadding = 4.dp
