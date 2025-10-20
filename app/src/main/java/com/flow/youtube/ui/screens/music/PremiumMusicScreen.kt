package com.flow.youtube.ui.screens.music

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PremiumMusicScreen(
    onBackClick: () -> Unit,
    onSongClick: (MusicTrack) -> Unit,
    onLibraryClick: () -> Unit = {},
    viewModel: MusicViewModel = viewModel(),
    playerViewModel: MusicPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by playerViewModel.uiState.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedTrackForOptions by remember { mutableStateOf<MusicTrack?>(null) }
    val tabs = listOf("Home", "Charts", "Genres", "Artists")
    val scope = rememberCoroutineScope()

    // Dialogs
    if (playerState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { playerViewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                playerViewModel.createPlaylist(name, desc, playerState.currentTrack)
            }
        )
    }
    
    if (playerState.showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playerState.playlists,
            onDismiss = { playerViewModel.showAddToPlaylistDialog(false) },
            onSelectPlaylist = { playlistId ->
                playerViewModel.addToPlaylist(playlistId)
            },
            onCreateNew = {
                playerViewModel.showAddToPlaylistDialog(false)
                playerViewModel.showCreatePlaylistDialog(true)
            }
        )
    }
    
    selectedTrackForOptions?.let { track ->
        TrackOptionsBottomSheet(
            track = track,
            isFavorite = false, // TODO: Check if favorite
            onDismiss = { selectedTrackForOptions = null },
            onFavoriteToggle = {
                playerViewModel.addToPlaylist("favorites") // TODO: Implement proper favorite toggle
            },
            onAddToPlaylist = {
                playerViewModel.showAddToPlaylistDialog(true)
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSearchActive.isNotBlank()) {
                SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { 
                        searchQuery = it
                        if (it.isNotBlank()) {
                            viewModel.searchMusic(it)
                        }
                    },
                    onClose = {
                        isSearchActive = ""
                        searchQuery = ""
                        viewModel.retry()
                    }
                )
            } else {
                MusicTopBar(
                    onBackClick = onBackClick,
                    onSearchClick = { isSearchActive = "active" },
                    onLibraryClick = onLibraryClick
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading -> {
                    LoadingView()
                }
                
                uiState.error != null -> {
                    ErrorView(
                        error = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    if (isSearchActive.isNotBlank() && searchQuery.isNotBlank()) {
                        SearchResultsView(
                            songs = uiState.allSongs,
                            isSearching = uiState.isSearching,
                            onSongClick = onSongClick,
                            onMoreClick = { track -> selectedTrackForOptions = track }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Modern Tab Bar
                            PremiumTabRow(
                                selectedIndex = selectedTab,
                                tabs = tabs,
                                onTabSelected = { selectedTab = it }
                            )

                            // Content with Animation
                            AnimatedContent(
                                targetState = selectedTab,
                                label = "tab_content",
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(300)) togetherWith
                                            fadeOut(animationSpec = tween(300))
                                }
                            ) { tabIndex ->
                                when (tabIndex) {
                                    0 -> HomeTab(
                                        uiState = uiState,
                                        onSongClick = onSongClick,
                                        onGenreClick = { genre -> 
                                            selectedTab = 2
                                            viewModel.loadGenreTracks(genre)
                                        },
                                        playerViewModel = playerViewModel
                                    )
                                    1 -> ChartsTab(
                                        songs = uiState.trendingSongs,
                                        onSongClick = onSongClick,
                                        playerViewModel = playerViewModel
                                    )
                                    2 -> GenresTab(
                                        genres = uiState.genres,
                                        genreTracks = uiState.genreTracks,
                                        onGenreClick = { viewModel.loadGenreTracks(it) },
                                        selectedGenre = uiState.selectedGenre,
                                        playerViewModel = playerViewModel
                                    )
                                    3 -> ArtistsTab(
                                        artists = extractArtists(uiState.trendingSongs),
                                        onArtistClick = { artist ->
                                            searchQuery = artist
                                            viewModel.searchMusic(artist)
                                            isSearchActive = "active"
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
@OptIn(ExperimentalMaterial3Api::class)
private fun MusicTopBar(
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onLibraryClick: () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Flow Music",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Premium Experience",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(onClick = onLibraryClick) {
                Icon(Icons.Outlined.LibraryMusic, "Library", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Outlined.Search, "Search", tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search for songs, artists...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.ArrowBack, "Close")
            }
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, "Clear")
                }
            }
        }
    )
}

@Composable
private fun PremiumTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        edgePadding = 0.dp,
        divider = {},
        indicator = { tabPositions ->
            Box(
                Modifier
                    .tabIndicatorOffset(tabPositions[selectedIndex])
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(horizontal = 24.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "Loading your music...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Error,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeTab(
    uiState: MusicUiState,
    onSongClick: (MusicTrack) -> Unit,
    onGenreClick: (String) -> Unit,
    playerViewModel: MusicPlayerViewModel
) {
    val scope = rememberCoroutineScope()
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero Banner with Featured Tracks
        item {
            HeroBanner(
                tracks = uiState.trendingSongs.take(5),
                onTrackClick = onSongClick
            )
        }

        // Top 10 Charts
        item {
            SectionHeader(
                title = "Top 10 This Week",
                subtitle = "Hottest tracks right now",
                icon = Icons.Filled.LocalFireDepartment
            )
        }
        
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(uiState.trendingSongs.take(10)) { track ->
                    var isFavorite by remember { mutableStateOf(false) }
                    var isDownloaded by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(track.videoId) {
                        isFavorite = playerViewModel.isTrackFavorite(track.videoId)
                        isDownloaded = playerViewModel.isTrackDownloaded(track.videoId)
                    }
                    
                    SleekMusicCard(
                        track = track,
                        onClick = { onSongClick(track) },
                        onFavoriteClick = {
                            playerViewModel.toggleLike()
                            scope.launch {
                                isFavorite = playerViewModel.isTrackFavorite(track.videoId)
                            }
                        },
                        onDownloadClick = {
                            playerViewModel.downloadTrack(track)
                            scope.launch {
                                kotlinx.coroutines.delay(1000)
                                isDownloaded = playerViewModel.isTrackDownloaded(track.videoId)
                            }
                        },
                        onAddToPlaylistClick = {
                            playerViewModel.showAddToPlaylistDialog(true)
                        },
                        isFavorite = isFavorite,
                        isDownloaded = isDownloaded,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        }

        // Genre Sections
        uiState.genreTracks.forEach { (genre, tracks) ->
            if (tracks.isNotEmpty()) {
                item(key = genre) {
                    Column(
                        modifier = Modifier.animateItemPlacement(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GenreSectionHeader(
                            genre = genre,
                            trackCount = tracks.size,
                            onSeeAll = { onGenreClick(genre) }
                        )
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(tracks.take(15)) { track ->
                                var isFavorite by remember { mutableStateOf(false) }
                                var isDownloaded by remember { mutableStateOf(false) }
                                
                                LaunchedEffect(track.videoId) {
                                    isFavorite = playerViewModel.isTrackFavorite(track.videoId)
                                    isDownloaded = playerViewModel.isTrackDownloaded(track.videoId)
                                }
                                
                                SleekMusicCard(
                                    track = track,
                                    onClick = { onSongClick(track) },
                                    onFavoriteClick = {
                                        playerViewModel.toggleLike()
                                        scope.launch {
                                            isFavorite = playerViewModel.isTrackFavorite(track.videoId)
                                        }
                                    },
                                    onDownloadClick = {
                                        playerViewModel.downloadTrack(track)
                                        scope.launch {
                                            kotlinx.coroutines.delay(1000)
                                            isDownloaded = playerViewModel.isTrackDownloaded(track.videoId)
                                        }
                                    },
                                    onAddToPlaylistClick = {
                                        playerViewModel.showAddToPlaylistDialog(true)
                                    },
                                    isFavorite = isFavorite,
                                    isDownloaded = isDownloaded,
                                    modifier = Modifier.width(180.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recently Trending
        item {
            SectionHeader(
                title = "More Trending Hits",
                subtitle = "${uiState.trendingSongs.size} songs",
                icon = Icons.Filled.TrendingUp
            )
        }
        
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 2000.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                userScrollEnabled = false
            ) {
                items(uiState.trendingSongs.drop(10).take(20)) { track ->
                    GridMusicCard(
                        track = track,
                        onClick = { onSongClick(track) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBanner(
    tracks: List<MusicTrack>,
    onTrackClick: (MusicTrack) -> Unit
) {
    if (tracks.isEmpty()) return
    
    val pagerState = rememberPagerState(pageCount = { tracks.size })
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % tracks.size)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val track = tracks[page]
            val scale = remember { Animatable(1f) }
            
            LaunchedEffect(pagerState.currentPage) {
                scale.animateTo(
                    if (page == pagerState.currentPage) 1f else 0.9f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                )
            }
            
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .scale(scale.value)
                    .clickable { onTrackClick(track) },
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp
            ) {
                Box {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.3f to Color.Black.copy(alpha = 0.3f),
                                    1f to Color.Black.copy(alpha = 0.9f)
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "FEATURED",
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            Row(
                                modifier = Modifier.padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = { onTrackClick(track) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Filled.PlayArrow, "Play")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Page Indicator
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(tracks.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (selected) 24.dp else 8.dp, 8.dp)
                        .background(
                            color = if (selected) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                Color.White.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .animateContentSize()
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GenreSectionHeader(
    genre: String,
    trackCount: Int,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = genre,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$trackCount tracks available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        FilledTonalButton(
            onClick = onSeeAll,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("See All", style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ModernTrackCard(
    track: MusicTrack,
    rank: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Rank Badge
                Surface(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "#$rank",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Play Button Overlay
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
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
private fun PremiumTrackCard(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            )
            
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
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
private fun GridTrackCard(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChartsTab(
    songs: List<MusicTrack>,
    onSongClick: (MusicTrack) -> Unit,
    playerViewModel: MusicPlayerViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(songs) { track ->
            val rank = songs.indexOf(track) + 1
            ChartTrackCard(
                track = track,
                rank = rank,
                onClick = { onSongClick(track) }
            )
        }
    }
}

@Composable
private fun ChartTrackCard(
    track: MusicTrack,
    rank: Int,
    onClick: () -> Unit
) {
    val rankColor = when {
        rank <= 3 -> MaterialTheme.colorScheme.primary
        rank <= 10 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank with Gradient
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = rankColor.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$rank",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = rankColor
                    )
                }
            }
            
            // Artwork
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null
                )
            }
            
            // Track Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Play Button
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun GenresTab(
    genres: List<String>,
    genreTracks: Map<String, List<MusicTrack>>,
    onGenreClick: (String) -> Unit,
    selectedGenre: String?,
    playerViewModel: MusicPlayerViewModel
) {
    if (selectedGenre != null && genreTracks.containsKey(selectedGenre)) {
        // Show tracks for selected genre
        val tracks = genreTracks[selectedGenre] ?: emptyList()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = selectedGenre,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${tracks.size} tracks",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(tracks) { track ->
                CompactTrackRow(
                    track = track,
                    onClick = { /* Handle click */ }
                )
            }
        }
    } else {
        // Show genre grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genres) { genre ->
                GenreTile(
                    genre = genre,
                    trackCount = genreTracks[genre]?.size ?: 0,
                    onClick = { onGenreClick(genre) }
                )
            }
        }
    }
}

@Composable
private fun GenreTile(
    genre: String,
    trackCount: Int,
    onClick: () -> Unit
) {
    val gradientColors = remember(genre) {
        val hash = genre.hashCode().absoluteValue
        val color1 = Color(0xFF000000 or ((hash % 0xFFFFFF).toLong()))
        val color2 = Color(0xFF000000 or ((hash.inv() % 0xFFFFFF).toLong()))
        listOf(color1, color2)
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradientColors))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(32.dp)
                )
                
                Column {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$trackCount songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<String>,
    onArtistClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists) { artist ->
            ArtistCard(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Text(
                text = artist,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchResultsView(
    songs: List<MusicTrack>,
    isSearching: Boolean,
    onSongClick: (MusicTrack) -> Unit,
    onMoreClick: (MusicTrack) -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isSearching -> {
                LoadingView()
            }
            songs.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No results found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(songs) { track ->
                        CompactSleekCard(
                            track = track,
                            onClick = { onSongClick(track) },
                            onMoreClick = { onMoreClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactTrackRow(
    track: MusicTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private fun extractArtists(tracks: List<MusicTrack>): List<String> {
    return tracks
        .map { it.artist }
        .distinct()
        .take(20)
}
