package io.github.aedev.flow.ui.screens.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.search.SearchFilterBar
import io.github.aedev.flow.ui.components.search.SearchFilterSheet
import io.github.aedev.flow.ui.components.search.SearchResultActions
import io.github.aedev.flow.ui.components.search.SearchResults
import io.github.aedev.flow.ui.components.search.SearchResultsShimmer
import io.github.aedev.flow.ui.components.search.SearchShortsGrid
import io.github.aedev.flow.ui.components.search.SearchSuggestionsPanel
import io.github.aedev.flow.ui.components.search.SearchTopBar
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.utils.videoIdFromUrl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onShortsQueue: (ShortsQueueSource) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val state = rememberSearchState(viewModel)

    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val searchBarState = rememberSearchBarState()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val collapseBar: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }
    val submit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            collapseBar()
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

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) gridState.scrollToItem(0)
    }

    // Opening the tab with nothing searched puts the caret in the field, as it always has; coming
    // back from a result must not, which is why this keys on the query rather than on first
    // composition.
    LaunchedEffect(Unit) {
        if (uiState.query.isBlank()) searchBarState.animateToExpanded()
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchTopBar(
            state = searchBarState,
            textFieldState = state.textFieldState,
            onSearch = submit,
            onBack = collapseBar,
            onVoiceSearch = { launchVoiceSearch(context, voiceSearchLauncher::launch) },
        ) {
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
        }

        if (uiState.query.isBlank()) {
            FlowEmptyState(
                title = stringResource(R.string.search_empty_prompt),
                icon = Icons.Rounded.Search,
            )
            return@Column
        }

        SearchFilterBar(
            filter = uiState.filters,
            shortsEnabled = state.shortsContentEnabled,
            isGridMode = state.isGridMode,
            onContentTypeSelected = { viewModel.updateFilters(uiState.filters.copy(contentType = it)) },
            onToggleGridMode = state::toggleGridMode,
            onOpenFilters = { showFilters = true },
            modifier = Modifier.padding(vertical = FilterBarVerticalPadding),
        )

        val refreshState = pagingItems.loadState.refresh
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val feedLayout = rememberFeedGridLayout(maxWidth, state.feedColumns)
            val actions =
                SearchResultActions(
                    onVideoClick = onVideoClick,
                    onShortsClick = { shelf, tapped -> onShortsQueue(viewModel.shortsShelfSource(shelf, tapped)) },
                    onChannelClick = onChannelClick,
                    onPlaylistClick = onPlaylistClick,
                    dismissKeyboard = collapseBar,
                )

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

    if (showFilters) {
        SearchFilterSheet(
            filter = uiState.filters,
            shortsEnabled = state.shortsContentEnabled,
            onApply = {
                showFilters = false
                viewModel.updateFilters(it)
            },
            onDismiss = { showFilters = false },
        )
    }

    LaunchedEffect(searchBarState) {
        snapshotFlow { searchBarState.targetValue }
            .distinctUntilChanged()
            .collect { if (it == SearchBarValue.Collapsed) state.clearSuggestions() }
    }
}

private fun launchVoiceSearch(
    context: android.content.Context,
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
