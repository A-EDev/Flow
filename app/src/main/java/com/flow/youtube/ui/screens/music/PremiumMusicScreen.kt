package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.flow.youtube.data.search.SearchSuggestionsService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumMusicScreen(
    onBackClick: () -> Unit,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onLibraryClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedChip by remember { mutableStateOf<String?>(null) }
    
    // Live search suggestions
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    
    // Debounced suggestions fetching
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        if (searchQuery.length >= 2 && isSearching) {
            searchJob = scope.launch {
                delay(300) // Debounce
                suggestions = SearchSuggestionsService.getMusicSuggestions(searchQuery)
                showSuggestions = suggestions.isNotEmpty()
            }
        } else {
            suggestions = emptyList()
            showSuggestions = false
        }
    }
    
    // Use theme colors instead of forced dark theme
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MusicTopBar(
                searchQuery = searchQuery,
                isSearching = isSearching,
                onSearchQueryChange = { 
                    searchQuery = it
                    showSuggestions = it.length >= 2
                },
                onSearchClick = onSearchClick,
                onSearchSubmit = {
                    if (searchQuery.isNotBlank()) {
                        showSuggestions = false
                        viewModel.searchMusic(searchQuery)
                    }
                },
                onClearSearch = {
                    searchQuery = ""
                    isSearching = false
                    showSuggestions = false
                    viewModel.searchMusic("") // Reset to trending
                },
                onProfileClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.trendingSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Loading music...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 160.dp) // More space for mini player + nav
            ) {
                // Live Search Suggestions
                if (showSuggestions && suggestions.isNotEmpty() && isSearching) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            suggestions.forEach { suggestion ->
                                SuggestionItem(
                                    suggestion = suggestion,
                                    query = searchQuery,
                                    onClick = {
                                        searchQuery = suggestion
                                        showSuggestions = false
                                        viewModel.searchMusic(suggestion)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Filter Chips
                    item {
                        FilterChipsRow(
                            selectedChip = selectedChip,
                            onChipSelected = { chip ->
                                selectedChip = if (selectedChip == chip) null else chip
                                if (chip != null && selectedChip == chip) {
                                    viewModel.loadGenreTracks(chip)
                                } else {
                                    viewModel.searchMusic("") // Reset to trending
                                }
                            }
                        )
                    }

                    // Search Results or Listen Again
                    if (uiState.allSongs.isNotEmpty() && uiState.allSongs != uiState.trendingSongs) {
                        // Artist Results
                        if (uiState.searchResultsArtists.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Artists")
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.searchResultsArtists) { artist ->
                                        ArtistResultItem(artist = artist, onClick = { onArtistClick(artist.channelId) })
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        // Search Results
                        item {
                            SectionHeader(
                                title = if (selectedChip != null) selectedChip!! else "Songs",
                                subtitle = "${uiState.allSongs.size} tracks"
                            )
                        }
                        
                        items(uiState.allSongs.chunked(2)) { rowTracks ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowTracks.forEach { track ->
                                    SearchResultItem(
                                        track = track,
                                        onClick = { onSongClick(track, uiState.allSongs, if (selectedChip != null) selectedChip else "Search Results") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Fill empty space if odd number
                                if (rowTracks.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // For You (New Recommendation Section)
                        if (uiState.forYouTracks.isNotEmpty()) {
                            item {
                                SectionHeader(title = "For You", subtitle = "Recommended based on your listening")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.forYouTracks) { track ->
                                        ListenAgainItem(track = track, onClick = { onSongClick(track, uiState.forYouTracks, "For You") }, onArtistClick = onArtistClick)
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        // Listen Again
                        if (uiState.trendingSongs.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Listen again")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.trendingSongs.take(15)) { track ->
                                        ListenAgainItem(track = track, onClick = { onSongClick(track, uiState.trendingSongs, "Listen Again") }, onArtistClick = onArtistClick)
                                    }
                                }
                            }
                        }

                        // Quick Picks
                        if (uiState.trendingSongs.size > 5) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                SectionHeader(title = "Quick picks", subtitle = "START RADIO")
                                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                                    uiState.trendingSongs.drop(5).take(6).forEach { track ->
                                        QuickPickItem(track = track, onClick = { onSongClick(track, uiState.trendingSongs, "Quick Picks") }, onArtistClick = onArtistClick)
                                    }
                                }
                            }
                        }
                        
                        // Genre Sections
                        uiState.genreTracks.forEach { (genre, tracks) ->
                            if (tracks.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    SectionHeader(title = genre)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(tracks.take(10)) { track ->
                                            NewReleaseItem(track = track, onClick = { onSongClick(track, tracks, genre) }, onArtistClick = onArtistClick)
                                        }
                                    }
                                }
                            }
                        }

                        // New Releases
                        if (uiState.newReleases.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(24.dp))
                                SectionHeader(title = "New releases")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.newReleases) { track ->
                                        NewReleaseItem(track = track, onClick = { onSongClick(track, uiState.newReleases, "New Releases") }, onArtistClick = onArtistClick)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Fallback if empty
                    if (uiState.trendingSongs.isEmpty() && !uiState.isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.MusicOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "No music found",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { viewModel.retry() }) {
                                        Text("Retry")
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

@Composable
private fun SuggestionItem(
    suggestion: String,
    query: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            
            // Highlighted suggestion text
            Text(
                text = buildAnnotatedString {
                    val lowerSuggestion = suggestion.lowercase()
                    val lowerQuery = query.lowercase()
                    val startIndex = lowerSuggestion.indexOf(lowerQuery)
                    
                    if (startIndex >= 0) {
                        append(suggestion.substring(0, startIndex))
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append(suggestion.substring(startIndex, startIndex + query.length))
                        }
                        append(suggestion.substring(startIndex + query.length))
                    } else {
                        append(suggestion)
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Icon(
                imageVector = Icons.Filled.NorthWest,
                contentDescription = "Use suggestion",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicTopBar(
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchSubmit: () -> Unit,
    onClearSearch: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        if (isSearching) {
            // Search mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search music...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Clear, "Clear")
                            }
                        }
                    }
                )
                
                IconButton(onClick = onSearchSubmit) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            // Normal mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Music",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Outlined.AccountCircle,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipsRow(
    selectedChip: String?,
    onChipSelected: (String?) -> Unit
) {
    val chips = listOf("Energize", "Relax", "Workout", "Commute", "Focus", "Pop", "Rock", "Hip Hop")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { chip ->
            val isSelected = selectedChip == chip
            FilterChip(
                selected = isSelected,
                onClick = { onChipSelected(chip) },
                label = { 
                    Text(
                        text = chip,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ListenAgainItem(track: MusicTrack, onClick: () -> Unit, onArtistClick: ((String) -> Unit)? = null) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(130.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                Modifier.clickable { onArtistClick(track.channelId) }
            } else {
                Modifier
            }
        )
    }
}

@Composable
private fun QuickPickItem(track: MusicTrack, onClick: () -> Unit, onArtistClick: ((String) -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                        Modifier.clickable { onArtistClick(track.channelId) }
                    } else {
                        Modifier
                    }
                )
            }
            IconButton(onClick = { /* More options */ }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MusicVideoItem(track: MusicTrack, onClick: () -> Unit, onArtistClick: ((String) -> Unit)? = null) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                Modifier.clickable { onArtistClick(track.channelId) }
            } else {
                Modifier
            }
        )
    }
}

@Composable
private fun NewReleaseItem(track: MusicTrack, onClick: () -> Unit, onArtistClick: ((String) -> Unit)? = null) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            modifier = Modifier.size(150.dp),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 2.dp
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (onArtistClick != null && track.channelId.isNotEmpty()) {
                Modifier.clickable { onArtistClick(track.channelId) }
            } else {
                Modifier
            }
        )
    }
}

@Composable
private fun SearchResultItem(
    track: MusicTrack, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistResultItem(
    artist: ArtistDetails,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
