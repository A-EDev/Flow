package io.github.aedev.flow.ui.screens.music

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.ui.components.music.card.TopResultCard
import io.github.aedev.flow.ui.components.music.common.MusicFeedProgress
import io.github.aedev.flow.ui.components.music.common.MusicLoadingIndicator
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicCollectionRow
import io.github.aedev.flow.ui.components.music.search.MusicSearchBar
import io.github.aedev.flow.ui.components.music.search.SearchFilterChips
import io.github.aedev.flow.ui.components.music.search.SearchSuggestionRow
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.toCollectionActionItem
import kotlinx.coroutines.delay

private const val FOCUS_DELAY_MS = 100L
private const val RECOMMENDED_SOURCE = "Recommended"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSearchScreen(
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    initialQuery: String? = null,
    viewModel: MusicSearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // When opened with a preset query (e.g. from music recognition), run the search instead of auto-focusing.
    LaunchedEffect(Unit) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            viewModel.performSearch(initialQuery)
            keyboardController?.hide()
        } else {
            delay(FOCUS_DELAY_MS)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedCollection by remember { mutableStateOf<MusicCollectionActionItem?>(null) }

    fun dismissSearchInput() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun showTrackActions(track: MusicTrack) {
        selectedTrack = track
        showBottomSheet = true
    }

    fun menuActionFor(item: YTItem): (() -> Unit)? =
        when (item) {
            is SongItem -> ({ showTrackActions(convertSongToMusicTrack(item)) })
            is AlbumItem, is PlaylistItem -> ({ item.toCollectionActionItem()?.let { selectedCollection = it } })
            else -> null
        }

    fun isDownloaded(item: YTItem): Boolean = (item as? SongItem)?.let { uiState.downloadedTrackIds.contains(it.id) } ?: false

    fun openItem(
        item: YTItem,
        queue: List<YTItem>,
        source: String,
    ) {
        dismissSearchInput()
        when (item) {
            is SongItem -> {
                onTrackClick(
                    convertSongToMusicTrack(item),
                    queue.filterIsInstance<SongItem>().map(::convertSongToMusicTrack),
                    source,
                )
            }

            is ArtistItem -> {
                onArtistClick(item.id)
            }

            is AlbumItem -> {
                onAlbumClick(item.id)
            }

            is PlaylistItem -> {
                onPlaylistClick(item.id)
            }
        }
    }

    fun playArtistTracks(
        artist: ArtistItem,
        shuffle: Boolean,
        source: String,
    ) {
        viewModel.getArtistTracks(artist.id) { tracks ->
            val musicTracks = tracks.filterIsInstance<SongItem>().map(::convertSongToMusicTrack)
            if (musicTracks.isNotEmpty()) {
                val queue = if (shuffle) musicTracks.shuffled() else musicTracks
                dismissSearchInput()
                onTrackClick(queue.first(), queue, source)
            }
        }
    }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = {
                if (selectedTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(selectedTrack!!.channelId)
                }
            },
            onViewAlbum = {},
            onShare = {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(
                                R.string.share_message_template,
                                selectedTrack!!.title,
                                selectedTrack!!.artist,
                                selectedTrack!!.videoId,
                            ),
                        )
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = {
                dismissSearchInput()
                if (collection.isAlbum) {
                    onAlbumClick(collection.id)
                } else {
                    onPlaylistClick(collection.id)
                }
            },
        )
    }

    val voiceSearchLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    viewModel.onQueryChange(spokenText)
                    viewModel.performSearch(spokenText)
                }
            }
        }

    Scaffold(
        topBar = {
            MusicSearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    viewModel.performSearch()
                    focusManager.clearFocus(force = true)
                },
                onBackClick = onBackClick,
                onClearClick = viewModel::clearSearch,
                onVoiceSearchClick = {
                    val intent =
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
                        }
                    voiceSearchLauncher.launch(intent)
                },
                focusRequester = focusRequester,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            if (!uiState.isSearching) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.recommendedItems, key = { it.stableLazyKey("recommended") }) { item ->
                        MusicCollectionRow(
                            item = item,
                            onClick = { openItem(item, listOf(item), RECOMMENDED_SOURCE) },
                            onMenuClick = menuActionFor(item),
                            onLongClick = menuActionFor(item),
                            isDownloaded = isDownloaded(item),
                        )
                    }
                    items(uiState.suggestions, key = { it }) { suggestion ->
                        SearchSuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                viewModel.performSearch(suggestion)
                                keyboardController?.hide()
                            },
                        )
                    }
                }
            } else {
                SearchFilterChips(
                    activeFilter = uiState.activeFilter,
                    onFilterClick = viewModel::applyFilter,
                )

                val topResultTarget = stringResource(R.string.section_top_result)
                val searchSource = stringResource(R.string.search_source_template).format(query)
                val artistSourceTemplate = stringResource(R.string.artist_source_template)

                if (uiState.isLoading) {
                    MusicLoadingIndicator()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                    ) {
                        val summaries = uiState.searchSummary?.summaries
                        if (uiState.activeFilter == null && summaries != null) {
                            summaries.forEachIndexed { index, summary ->
                                item(key = "summary_header_$index") {
                                    MusicSectionHeader(title = summary.title)
                                }

                                val isTopResult = summary.title == topResultTarget
                                if (isTopResult) {
                                    val topItem = summary.items.first()
                                    item(key = "top_result") {
                                        TopResultCard(
                                            item = topItem,
                                            onClick = { openItem(topItem, summary.items, searchSource) },
                                            onShuffleClick = {
                                                if (topItem is ArtistItem) {
                                                    playArtistTracks(topItem, shuffle = true, artistSourceTemplate.format(topItem.title))
                                                }
                                            },
                                            onRadioClick = {
                                                if (topItem is ArtistItem) {
                                                    playArtistTracks(topItem, shuffle = false, artistSourceTemplate.format(topItem.title))
                                                }
                                            },
                                            onLongClick = menuActionFor(topItem),
                                            onMenuClick = menuActionFor(topItem),
                                        )
                                    }
                                }

                                items(
                                    items = if (isTopResult) summary.items.drop(1) else summary.items,
                                    key = { it.stableLazyKey("summary_${summary.title}") },
                                ) { item ->
                                    MusicCollectionRow(
                                        showPlayCount = true,
                                        item = item,
                                        onClick = { openItem(item, summary.items, searchSource) },
                                        onMenuClick = menuActionFor(item),
                                        onLongClick = menuActionFor(item),
                                        isDownloaded = isDownloaded(item),
                                    )
                                }
                            }
                        } else {
                            items(uiState.filteredResults, key = { it.stableLazyKey("filtered") }) { item ->
                                MusicCollectionRow(
                                    showPlayCount = true,
                                    item = item,
                                    onClick = { openItem(item, uiState.filteredResults, searchSource) },
                                    onMenuClick = menuActionFor(item),
                                    onLongClick = menuActionFor(item),
                                    isDownloaded = isDownloaded(item),
                                )
                            }
                        }

                        if (uiState.continuation != null) {
                            item(key = "continuation") {
                                LaunchedEffect(Unit) {
                                    viewModel.loadMore()
                                }
                                if (uiState.isMoreLoading) {
                                    MusicFeedProgress()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper to convert SongItem to MusicTrack (shared with the TV search screen)
internal fun convertSongToMusicTrack(item: SongItem): MusicTrack =
    MusicTrack(
        videoId = item.id,
        title = item.title,
        artist = item.artists.joinToString { it.name },
        thumbnailUrl = item.thumbnail,
        duration = item.duration ?: 0,
        views = 0, // View count text is a string in SongItem
        sourceUrl = "https://www.youtube.com/watch?v=${item.id}",
        album = item.album?.name ?: "Unknown Album",
        channelId = item.artists.firstOrNull()?.id ?: "",
        isExplicit = item.explicit,
        isVideoSong = item.isVideoSong,
    )
