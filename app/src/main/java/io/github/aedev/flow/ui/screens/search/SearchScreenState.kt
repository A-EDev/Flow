package io.github.aedev.flow.ui.screens.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SearchHistoryItem
import io.github.aedev.flow.data.local.SearchHistoryRepository
import io.github.aedev.flow.innertube.pages.search.SearchSuggestion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Everything the search route needs that is not a result: the field, the history it matches
 * against, the live suggestions, and the two display preferences.
 */
@Stable
class SearchScreenState(
    val textFieldState: TextFieldState,
    private val scope: CoroutineScope,
    private val history: SearchHistoryRepository,
    private val preferences: PlayerPreferences,
    private val fetchSuggestions: suspend (String) -> List<SearchSuggestion>,
) {
    var suggestions by mutableStateOf<List<SearchSuggestion>>(emptyList())
        private set
    var allHistory by mutableStateOf<List<SearchHistoryItem>>(emptyList())
        internal set
    var isGridMode by mutableStateOf(false)
        internal set
    var shortsContentEnabled by mutableStateOf(true)
        internal set
    var feedColumns by mutableStateOf(HomeFeedColumns.AUTO)
        internal set
    var suggestionsEnabled by mutableStateOf(true)
        internal set

    val query: String
        get() = textFieldState.text.toString()

    /** History rows that match what has been typed, prefix matches first, as YouTube orders them. */
    val matchingHistory: List<SearchHistoryItem>
        get() {
            val typed = query.trim()
            if (typed.isEmpty()) return allHistory.take(HISTORY_LIMIT)
            val lowered = typed.lowercase()
            val matches = allHistory.filter { it.query.contains(typed, ignoreCase = true) }
            val (prefix, rest) = matches.partition { it.query.lowercase().startsWith(lowered) }
            return (prefix + rest).take(HISTORY_LIMIT)
        }

    fun onSubmit(text: String) {
        scope.launch { history.saveSearchQuery(text) }
        suggestions = emptyList()
    }

    fun deleteHistoryItem(item: SearchHistoryItem) {
        scope.launch { history.deleteSearchItem(item.id) }
    }

    fun clearHistory() {
        scope.launch { history.clearSearchHistory() }
    }

    fun clearSuggestions() {
        suggestions = emptyList()
    }

    fun toggleGridMode() {
        scope.launch { preferences.setSearchIsGridMode(!isGridMode) }
    }

    internal suspend fun refreshSuggestions(typed: String) {
        suggestions =
            if (suggestionsEnabled && typed.trim().length >= MIN_SUGGESTION_LENGTH) {
                fetchSuggestions(typed.trim())
            } else {
                emptyList()
            }
    }

    private companion object {
        const val HISTORY_LIMIT = 8
        const val MIN_SUGGESTION_LENGTH = 2
    }
}

@OptIn(FlowPreview::class)
@Composable
fun rememberSearchState(viewModel: SearchViewModel): SearchScreenState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val historyRepository = remember(context) { SearchHistoryRepository(context) }
    val preferences = remember(context) { PlayerPreferences(context) }
    val textFieldState = remember { TextFieldState() }

    val state =
        remember(historyRepository, preferences) {
            SearchScreenState(
                textFieldState = textFieldState,
                scope = scope,
                history = historyRepository,
                preferences = preferences,
                fetchSuggestions = viewModel::getSearchSuggestions,
            )
        }

    state.allHistory = historyRepository.getSearchHistoryFlow().collectAsStateWithLifecycle(emptyList()).value
    state.suggestionsEnabled = historyRepository.isSearchSuggestionsEnabledFlow().collectAsStateWithLifecycle(true).value
    state.isGridMode = preferences.searchIsGridMode.collectAsStateWithLifecycle(false).value
    state.shortsContentEnabled = preferences.shortsContentEnabled.collectAsStateWithLifecycle(true).value
    state.feedColumns = preferences.homeFeedColumns.collectAsStateWithLifecycle(HomeFeedColumns.AUTO).value

    LaunchedEffect(state) {
        snapshotFlow { state.textFieldState.text.toString() }
            .distinctUntilChanged()
            .debounce(SUGGESTION_DEBOUNCE_MS)
            .collect { state.refreshSuggestions(it) }
    }

    return state
}

private const val SUGGESTION_DEBOUNCE_MS = 280L
