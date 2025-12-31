package com.flow.youtube.ui.screens.music

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.flow.youtube.innertube.YouTube.SearchFilter
import com.flow.youtube.innertube.models.*
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, FlowPreview::class)
@Composable
fun MusicSearchScreen(
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: MusicSearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
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

    Scaffold(
        topBar = {
            MusicSearchBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    viewModel.performSearch()
                    keyboardController?.hide()
                },
                onBackClick = onBackClick,
                onClearClick = viewModel::clearSearch,
                onVoiceSearchClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search music")
                    }
                    voiceSearchLauncher.launch(intent)
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!uiState.isSearching) {
                // Show suggestions
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.recommendedItems) { item ->
                        RecommendedItemRow(
                            item = item,
                            onClick = {
                                when (item) {
                                    is SongItem -> onTrackClick(convertSongToMusicTrack(item), listOf(convertSongToMusicTrack(item)), "Recommended")
                                    is ArtistItem -> onArtistClick(item.id)
                                    is AlbumItem -> onAlbumClick(item.id)
                                    is PlaylistItem -> onPlaylistClick(item.id)
                                }
                            }
                        )
                    }
                    items(uiState.suggestions) { suggestion ->
                        SearchSuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                viewModel.performSearch(suggestion)
                                keyboardController?.hide()
                            }
                        )
                    }
                }
            } else {
                // Show results
                Column(modifier = Modifier.fillMaxSize()) {
                    SearchFilterChips(
                        activeFilter = uiState.activeFilter,
                        onFilterClick = viewModel::applyFilter
                    )

                    if (uiState.isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            if (uiState.activeFilter == null && uiState.searchSummary != null) {
                                // Summary view (Top Result + Sections)
                                uiState.searchSummary?.summaries?.forEach { summary ->
                                    item {
                                        Text(
                                            text = summary.title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    
                                    if (summary.title == "Top result" || summary.title == "Top Result") {
                                        item {
                                            TopResultCard(
                                                item = summary.items.first(),
                                                onClick = {
                                                    val item = summary.items.first()
                                                    when (item) {
                                                        is SongItem -> onTrackClick(convertSongToMusicTrack(item), summary.items.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }, "Search: $query")
                                                        is ArtistItem -> onArtistClick(item.id)
                                                        is AlbumItem -> onAlbumClick(item.id)
                                                        is PlaylistItem -> onPlaylistClick(item.id)
                                                    }
                                                },
                                                onShuffleClick = {
                                                    val item = summary.items.first()
                                                    if (item is ArtistItem) {
                                                        viewModel.getArtistTracks(item.id) { tracks ->
                                                            val musicTracks = tracks.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }
                                                            if (musicTracks.isNotEmpty()) {
                                                                onTrackClick(musicTracks.shuffled().first(), musicTracks.shuffled(), "Artist: ${item.title}")
                                                            }
                                                        }
                                                    }
                                                },
                                                onRadioClick = {
                                                    val item = summary.items.first()
                                                    if (item is ArtistItem) {
                                                        // Start radio based on artist
                                                        viewModel.getArtistTracks(item.id) { tracks ->
                                                            val musicTracks = tracks.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }
                                                            if (musicTracks.isNotEmpty()) {
                                                                onTrackClick(musicTracks.first(), musicTracks, "Artist: ${item.title}")
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                        // Skip the first item as it's in the TopResultCard
                                        items(summary.items.drop(1)) { item ->
                                            YTItemRow(
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> onTrackClick(convertSongToMusicTrack(item), summary.items.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }, "Search: $query")
                                                        is ArtistItem -> onArtistClick(item.id)
                                                        is AlbumItem -> onAlbumClick(item.id)
                                                        is PlaylistItem -> onPlaylistClick(item.id)
                                                    }
                                                }
                                            )
                                        }
                                    } else {
                                        items(summary.items) { item ->
                                            YTItemRow(
                                                item = item,
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> onTrackClick(convertSongToMusicTrack(item), summary.items.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }, "Search: $query")
                                                        is ArtistItem -> onArtistClick(item.id)
                                                        is AlbumItem -> onAlbumClick(item.id)
                                                        is PlaylistItem -> onPlaylistClick(item.id)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Filtered results
                                items(uiState.filteredResults) { item ->
                                    YTItemRow(
                                        item = item,
                                        onClick = {
                                            when (item) {
                                                is SongItem -> onTrackClick(convertSongToMusicTrack(item), uiState.filteredResults.filterIsInstance<SongItem>().map { convertSongToMusicTrack(it) }, "Search: $query")
                                                is ArtistItem -> onArtistClick(item.id)
                                                is AlbumItem -> onAlbumClick(item.id)
                                                is PlaylistItem -> onPlaylistClick(item.id)
                                            }
                                        }
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

@Composable
fun MusicSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBackClick: () -> Unit,
    onClearClick: () -> Unit,
    onVoiceSearchClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text("Search music", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp)
                    }
                    innerTextField()
                }
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = onClearClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onVoiceSearchClick) {
            Icon(
                Icons.Default.Mic,
                contentDescription = "Voice Search",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun SearchFilterChips(
    activeFilter: SearchFilter?,
    onFilterClick: (SearchFilter?) -> Unit
) {
    val filters = listOf(
        "Albums" to SearchFilter.FILTER_ALBUM,
        "Videos" to SearchFilter.FILTER_VIDEO,
        "Songs" to SearchFilter.FILTER_SONG,
        "Community playlists" to SearchFilter.FILTER_COMMUNITY_PLAYLIST
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filters) { (label, filter) ->
            Surface(
                modifier = Modifier.clickable { onFilterClick(if (activeFilter == filter) null else filter) },
                shape = RoundedCornerShape(8.dp),
                color = if (activeFilter == filter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (activeFilter == filter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
fun SearchSuggestionRow(
    suggestion: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = suggestion,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ArrowOutward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun RecommendedItemRow(
    item: YTItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when (item) {
                is SongItem -> "Song • ${item.artists.joinToString { it.name }}"
                is ArtistItem -> "Artist"
                is AlbumItem -> "Album • ${item.artists?.joinToString { it.name } ?: ""}"
                is PlaylistItem -> "Playlist • ${item.author?.name ?: ""}"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* Options */ }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun YTItemRow(
    item: YTItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = when (item) {
                is SongItem -> {
                    val plays = item.viewCountText?.let { " • $it plays" } ?: ""
                    "Song • ${item.artists.joinToString { it.name }}$plays"
                }
                is ArtistItem -> "Artist"
                is AlbumItem -> "Album • ${item.artists?.joinToString { it.name } ?: ""} • ${item.year ?: ""}"
                is PlaylistItem -> "Playlist • ${item.author?.name ?: ""}"
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* Options */ }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun TopResultCard(
    item: YTItem,
    onClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRadioClick: () -> Unit
) {
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val subtitle = when (item) {
                        is ArtistItem -> "Artist • 442M monthly audience"
                        is SongItem -> "Song • ${item.artists.joinToString { it.name }}"
                        is AlbumItem -> "Album • ${item.artists?.joinToString { it.name } ?: ""}"
                        is PlaylistItem -> "Playlist • ${item.author?.name ?: ""}"
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (item is ArtistItem) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onShuffleClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shuffle", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRadioClick,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(28.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Radio", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Helper to convert SongItem to MusicTrack
private fun convertSongToMusicTrack(item: SongItem): MusicTrack {
    return MusicTrack(
        videoId = item.id,
        title = item.title,
        artist = item.artists.joinToString { it.name },
        thumbnailUrl = item.thumbnail,
        duration = item.duration ?: 0,
        views = 0, // View count text is a string in SongItem
        sourceUrl = "https://www.youtube.com/watch?v=${item.id}",
        album = item.album?.name ?: "Unknown Album",
        channelId = item.artists.firstOrNull()?.id ?: ""
    )
}
