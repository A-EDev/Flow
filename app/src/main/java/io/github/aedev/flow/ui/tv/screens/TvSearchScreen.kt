package io.github.aedev.flow.ui.tv.screens

import android.app.Activity
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.availableWith
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.ui.components.search.SearchSuggestionsPanel
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaKindSelector
import io.github.aedev.flow.ui.components.shared.quickactions.sharedQuickActionsViewModel
import io.github.aedev.flow.ui.screens.music.MusicSearchBody
import io.github.aedev.flow.ui.screens.music.MusicSearchViewModel
import io.github.aedev.flow.ui.screens.search.SearchResultsBody
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.screens.search.launchVoiceSearch
import io.github.aedev.flow.ui.screens.search.rememberSearchState
import io.github.aedev.flow.ui.screens.search.rememberSearchSubmitter
import io.github.aedev.flow.ui.tv.components.TvScreenFrame
import io.github.aedev.flow.ui.tv.input.TvKeyboard
import kotlinx.coroutines.flow.drop

private val KeyboardPaneWidth = 336.dp
private val KindSelectorMaxWidth = 360.dp

/**
 * TV search: the query, the Videos and Music switch and the D-pad keyboard on the start side; the
 * phone's suggestions and results beside them. Both kinds read the one query, so switching kind keeps
 * what was typed.
 */
@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onPlayTrack: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenMusicPlaylist: (String) -> Unit,
    onOpenMood: (MoodAndGenres.Item) -> Unit,
    onOpenMoods: () -> Unit,
    modifier: Modifier = Modifier,
    musicViewModel: MusicSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val softwareKeyboard = LocalSoftwareKeyboardController.current
    val uiState by viewModel.uiState.collectAsState()
    val state = rememberSearchState(viewModel)
    val quickActions = sharedQuickActionsViewModel()
    val submitVideo = rememberSearchSubmitter(viewModel, state, quickActions)
    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    var kind by rememberSaveable { mutableStateOf(MediaKind.Videos) }
    var systemKeyboard by remember { mutableStateOf(false) }
    val fieldFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }
    val text = state.textFieldState

    fun submit() {
        val query = text.text.toString()
        if (kind == MediaKind.Music) musicViewModel.performSearch(query) else submitVideo(query, uiState.filters)
    }

    LaunchedEffect(state) {
        snapshotFlow { text.text.toString() }
            .drop(1)
            .collect(musicViewModel::onQueryChange)
    }
    LaunchedEffect(systemKeyboard) {
        if (systemKeyboard) {
            fieldFocusRequester.requestFocus()
            softwareKeyboard?.show()
        }
    }

    val voiceLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.takeIf(String::isNotBlank)
                ?.let { spoken ->
                    text.setTextAndPlaceCursorAtEnd(spoken)
                    submit()
                }
        }

    TvScreenFrame(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(KeyboardPaneWidth)
                        .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowSearchField(
                    state = text,
                    placeholder = stringResource(R.string.search_videos_channels_placeholder),
                    readOnly = !systemKeyboard,
                    focusRequester = fieldFocusRequester,
                    onSearch = {
                        systemKeyboard = false
                        submit()
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (systemKeyboard && !it.hasFocus) {
                                    systemKeyboard = false
                                    softwareKeyboard?.hide()
                                    keyboardFocusRequester.requestFocus()
                                }
                            },
                )
                TvKeyboard(
                    onInput = { char -> text.edit { append(char) } },
                    onDelete = { text.edit { if (length > 0) delete(length - 1, length) } },
                    onClear = { text.clearText() },
                    onSearch = ::submit,
                    onSystemKeyboard = { systemKeyboard = true },
                    onVoice = { launchVoiceSearch(context, voiceLauncher::launch) },
                    modifier = Modifier.focusRequester(keyboardFocusRequester),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                MediaKindSelector(
                    options = MediaKind.entries,
                    selected = kind,
                    onSelected = { kind = it },
                    label = { stringResource(it.labelRes) },
                    icon = { it.icon },
                    modifier = Modifier.widthIn(max = KindSelectorMaxWidth),
                )
                if (kind == MediaKind.Music) {
                    MusicSearchBody(
                        viewModel = musicViewModel,
                        onTrackClick = onPlayTrack,
                        onAlbumClick = onOpenAlbum,
                        onArtistClick = onOpenArtist,
                        onPlaylistClick = onOpenMusicPlaylist,
                        onMoodClick = onOpenMood,
                        onMoodsSeeAll = onOpenMoods,
                        dismissSearchInput = {},
                        onSuggestionPicked = {},
                    )
                } else if (uiState.query.isBlank() || state.isTyping) {
                    SearchSuggestionsPanel(
                        query = state.query,
                        history = state.matchingHistory,
                        suggestions = state.suggestions,
                        onSubmit = { suggestion ->
                            text.setTextAndPlaceCursorAtEnd(suggestion)
                            submitVideo(suggestion, uiState.filters)
                        },
                        onHistorySelect = { item ->
                            text.setTextAndPlaceCursorAtEnd(item.query)
                            submitVideo(item.query, item.filters?.availableWith(false) ?: uiState.filters)
                        },
                        onFill = text::setTextAndPlaceCursorAtEnd,
                        onDeleteHistoryItem = state::deleteHistoryItem,
                        onClearHistory = state::clearHistory,
                    )
                } else {
                    SearchResultsBody(
                        viewModel = viewModel,
                        filters = uiState.filters,
                        shortsEnabled = false,
                        // The results share the screen with the keyboard: rows fit that pane, a grid would not.
                        isGridMode = true,
                        feedColumns = HomeFeedColumns.AUTO,
                        pagingItems = pagingItems,
                        gridState = gridState,
                        quickActions = quickActions,
                        onVideoClick = onVideoClick,
                        onChannelClick = onChannelClick,
                        onPlaylistClick = onPlaylistClick,
                        onShortsQueue = {},
                        dismissKeyboard = state::stopTyping,
                    )
                }
            }
        }
    }
}
