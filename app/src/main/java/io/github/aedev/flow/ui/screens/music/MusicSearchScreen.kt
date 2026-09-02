package io.github.aedev.flow.ui.screens.music

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import io.github.aedev.flow.innertube.models.*
import io.github.aedev.flow.ui.components.AddToPlaylistDialog
import io.github.aedev.flow.ui.components.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.card.TopResultCard
import io.github.aedev.flow.ui.components.music.item.MusicCollectionRow
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.search.MusicSearchBar
import io.github.aedev.flow.ui.components.music.search.SearchFilterChips
import io.github.aedev.flow.ui.components.music.search.SearchSuggestionRow
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, FlowPreview::class, ExperimentalFoundationApi::class)
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
            kotlinx.coroutines.delay(100)
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

    fun showCollectionActions(item: YTItem) {
        item.toCollectionActionItem()?.let { selectedCollection = it }
    }

    fun menuActionFor(item: YTItem): (() -> Unit)? =
        when (item) {
            is SongItem -> ({ showTrackActions(convertSongToMusicTrack(item)) })
            is AlbumItem, is PlaylistItem -> ({ showCollectionActions(item) })
            else -> null
        }

    fun isDownloaded(item: YTItem): Boolean = (item as? SongItem)?.let { uiState.downloadedTrackIds.contains(it.id) } ?: false

    if (showBottomSheet && selectedTrack != null) {
        val context = LocalContext.current
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = {
                if (selectedTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(selectedTrack!!.channelId)
                }
            },
            onViewAlbum = { /* TODO: Implement view album */ },
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
                val data = result.data
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = results?.get(0)
                if (!spokenText.isNullOrBlank()) {
                    viewModel.onQueryChange(spokenText)
                    viewModel.performSearch(spokenText)
                }
            }
        }

    fun playSearchTrack(
        track: MusicTrack,
        queue: List<MusicTrack>,
        source: String?,
    ) {
        dismissSearchInput()
        onTrackClick(track, queue, source)
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
                // Show suggestions
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.recommendedItems, key = { it.stableLazyKey("recommended") }) { item ->
                        MusicCollectionRow(
                            item = item,
                            onClick = {
                                when (item) {
                                    is SongItem -> {
                                        val track = convertSongToMusicTrack(item)
                                        playSearchTrack(track, listOf(track), "Recommended")
                                    }

                                    is ArtistItem -> {
                                        dismissSearchInput()
                                        onArtistClick(item.id)
                                    }

                                    is AlbumItem -> {
                                        dismissSearchInput()
                                        onAlbumClick(item.id)
                                    }

                                    is PlaylistItem -> {
                                        dismissSearchInput()
                                        onPlaylistClick(item.id)
                                    }
                                }
                            },
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
                // Show results
                Column(modifier = Modifier.fillMaxSize()) {
                    SearchFilterChips(
                        activeFilter = uiState.activeFilter,
                        onFilterClick = viewModel::applyFilter,
                    )

                    val topResultTarget = stringResource(R.string.section_top_result)
                    val searchSourceTemplate = stringResource(R.string.search_source_template)
                    val artistSourceTemplate = stringResource(R.string.artist_source_template)

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp),
                        ) {
                            if (uiState.activeFilter == null && uiState.searchSummary != null) {
                                // Summary view (Top Result + Sections)
                                uiState.searchSummary?.summaries?.forEach { summary ->
                                    item {
                                        Text(
                                            text = summary.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.onBackground,
                                        )
                                    }

                                    if (summary.title == topResultTarget) {
                                        item {
                                            TopResultCard(
                                                item = summary.items.first(),
                                                onClick = {
                                                    val item = summary.items.first()
                                                    when (item) {
                                                        is SongItem -> {
                                                            playSearchTrack(
                                                                convertSongToMusicTrack(
                                                                    item,
                                                                ),
                                                                summary.items.filterIsInstance<SongItem>().map {
                                                                    convertSongToMusicTrack(it)
                                                                },
                                                                searchSourceTemplate.format(query),
                                                            )
                                                        }

                                                        is ArtistItem -> {
                                                            dismissSearchInput()
                                                            onArtistClick(item.id)
                                                        }

                                                        is AlbumItem -> {
                                                            dismissSearchInput()
                                                            onAlbumClick(item.id)
                                                        }

                                                        is PlaylistItem -> {
                                                            dismissSearchInput()
                                                            onPlaylistClick(item.id)
                                                        }
                                                    }
                                                },
                                                onShuffleClick = {
                                                    val item = summary.items.first()
                                                    if (item is ArtistItem) {
                                                        viewModel.getArtistTracks(item.id) { tracks ->
                                                            val musicTracks =
                                                                tracks.filterIsInstance<SongItem>().map {
                                                                    convertSongToMusicTrack(
                                                                        it,
                                                                    )
                                                                }
                                                            if (musicTracks.isNotEmpty()) {
                                                                val shuffled = musicTracks.shuffled()
                                                                playSearchTrack(
                                                                    shuffled.first(),
                                                                    shuffled,
                                                                    artistSourceTemplate.format(item.title),
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onRadioClick = {
                                                    val item = summary.items.first()
                                                    if (item is ArtistItem) {
                                                        // Start radio based on artist
                                                        viewModel.getArtistTracks(item.id) { tracks ->
                                                            val musicTracks =
                                                                tracks.filterIsInstance<SongItem>().map {
                                                                    convertSongToMusicTrack(
                                                                        it,
                                                                    )
                                                                }
                                                            if (musicTracks.isNotEmpty()) {
                                                                playSearchTrack(
                                                                    musicTracks.first(),
                                                                    musicTracks,
                                                                    artistSourceTemplate.format(item.title),
                                                                )
                                                            }
                                                        }
                                                    }
                                                },
                                                onLongClick = menuActionFor(summary.items.first()),
                                                onMenuClick = menuActionFor(summary.items.first()),
                                            )
                                        }
                                        // Skip the first item as it's in the TopResultCard
                                        items(
                                            items = summary.items.drop(1),
                                            key = { it.stableLazyKey("summary_${summary.title}") },
                                        ) { item ->
                                            MusicCollectionRow(
                                                showPlayCount = true,
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> {
                                                            playSearchTrack(
                                                                convertSongToMusicTrack(
                                                                    item,
                                                                ),
                                                                summary.items.filterIsInstance<SongItem>().map {
                                                                    convertSongToMusicTrack(it)
                                                                },
                                                                searchSourceTemplate.format(query),
                                                            )
                                                        }

                                                        is ArtistItem -> {
                                                            dismissSearchInput()
                                                            onArtistClick(item.id)
                                                        }

                                                        is AlbumItem -> {
                                                            dismissSearchInput()
                                                            onAlbumClick(item.id)
                                                        }

                                                        is PlaylistItem -> {
                                                            dismissSearchInput()
                                                            onPlaylistClick(item.id)
                                                        }
                                                    }
                                                },
                                                onMenuClick = menuActionFor(item),
                                                onLongClick = menuActionFor(item),
                                                isDownloaded = isDownloaded(item),
                                            )
                                        }
                                    } else {
                                        items(
                                            items = summary.items,
                                            key = { it.stableLazyKey("summary_${summary.title}") },
                                        ) { item ->
                                            MusicCollectionRow(
                                                showPlayCount = true,
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> {
                                                            playSearchTrack(
                                                                convertSongToMusicTrack(
                                                                    item,
                                                                ),
                                                                summary.items.filterIsInstance<SongItem>().map {
                                                                    convertSongToMusicTrack(it)
                                                                },
                                                                searchSourceTemplate.format(query),
                                                            )
                                                        }

                                                        is ArtistItem -> {
                                                            dismissSearchInput()
                                                            onArtistClick(item.id)
                                                        }

                                                        is AlbumItem -> {
                                                            dismissSearchInput()
                                                            onAlbumClick(item.id)
                                                        }

                                                        is PlaylistItem -> {
                                                            dismissSearchInput()
                                                            onPlaylistClick(item.id)
                                                        }
                                                    }
                                                },
                                                onMenuClick = menuActionFor(item),
                                                onLongClick = menuActionFor(item),
                                                isDownloaded = isDownloaded(item),
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Filtered results
                                items(uiState.filteredResults, key = { it.stableLazyKey("filtered") }) { item ->
                                    MusicCollectionRow(
                                        showPlayCount = true,
                                        item = item,
                                        onClick = {
                                            when (item) {
                                                is SongItem -> {
                                                    playSearchTrack(
                                                        convertSongToMusicTrack(
                                                            item,
                                                        ),
                                                        uiState.filteredResults.filterIsInstance<SongItem>().map {
                                                            convertSongToMusicTrack(it)
                                                        },
                                                        searchSourceTemplate.format(query),
                                                    )
                                                }

                                                is ArtistItem -> {
                                                    dismissSearchInput()
                                                    onArtistClick(item.id)
                                                }

                                                is AlbumItem -> {
                                                    dismissSearchInput()
                                                    onAlbumClick(item.id)
                                                }

                                                is PlaylistItem -> {
                                                    dismissSearchInput()
                                                    onPlaylistClick(item.id)
                                                }
                                            }
                                        },
                                        onMenuClick = menuActionFor(item),
                                        onLongClick = menuActionFor(item),
                                        isDownloaded = isDownloaded(item),
                                    )
                                }
                            }

                            // Continuation Logic
                            if (uiState.continuation != null) {
                                item {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMore()
                                    }
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (uiState.isMoreLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
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

private fun YTItem.toCollectionActionItem(): MusicCollectionActionItem? =
    when (this) {
        is AlbumItem -> {
            MusicCollectionActionItem(
                id = id,
                title = title,
                subtitle = artists?.joinToString { it.name }.orEmpty(),
                thumbnailUrl = thumbnail,
                description = year?.toString().orEmpty(),
                isAlbum = true,
            )
        }

        is PlaylistItem -> {
            MusicCollectionActionItem(
                id = id,
                title = title,
                subtitle = author?.name.orEmpty(),
                thumbnailUrl = thumbnail,
                description = author?.name.orEmpty(),
                isAlbum = false,
            )
        }

        else -> {
            null
        }
    }
