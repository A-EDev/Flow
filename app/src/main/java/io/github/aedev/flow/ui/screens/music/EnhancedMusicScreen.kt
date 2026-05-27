package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import io.github.aedev.flow.ui.TabScrollEventBus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.screens.music.components.*
import io.github.aedev.flow.ui.screens.music.tabs.*
import io.github.aedev.flow.ui.theme.Dimensions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

private fun MusicTrack.isAudioMusicCandidate(): Boolean {
    val usableDuration = duration == 0 || duration in 30..1200
    return itemType == MusicItemType.SONG && !isVideoSong && videoId.isNotBlank() && usableDuration
}

private fun List<MusicTrack>.audioMusicOnly(): List<MusicTrack> =
    filter { it.isAudioMusicCandidate() }.distinctBy { it.videoId }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EnhancedMusicScreen(
    onBackClick: () -> Unit,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onVideoClick: (MusicTrack) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onMoodsClick: (io.github.aedev.flow.innertube.pages.MoodAndGenres.Item?) -> Unit = {},
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val musicListState = rememberLazyListState()
    val quickPicksGridState = rememberLazyGridState()

    val sectionOrder = remember(uiState.sessionSeed) {
        val defaultOrder = HomeSectionType.values().toList()
        val anchored = listOf(
            HomeSectionType.QUICK_PICKS,
            HomeSectionType.FROM_COMMUNITY,
            HomeSectionType.DAILY_DISCOVER
        )
        val dynamicPool = defaultOrder - anchored
        anchored + dynamicPool.shuffled(java.util.Random(uiState.sessionSeed))
    }

    // Scroll to top and refresh when tapping the music tab while already on this screen
    LaunchedEffect(Unit) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "music" }
            .collectLatest {
                musicListState.animateScrollToItem(0)
                viewModel.refresh()
    }
}
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedCollection by remember { mutableStateOf<MusicCollectionActionItem?>(null) }

    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onViewArtist = { channelId ->
                if (channelId.isNotEmpty()) {
                    onArtistClick(channelId)
                }
            },
            onViewAlbum = { albumId ->
                if (albumId.isNotEmpty()) {
                    onAlbumClick(albumId)
                }
            },
            onShare = { 
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, selectedTrack!!.title, selectedTrack!!.artist, selectedTrack!!.videoId))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            }
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = { onAlbumClick(collection.id) }
        )
    }
    



    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.screen_title_music),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.search))
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isInitialLoading = uiState.isLoading && uiState.trendingSongs.isEmpty() && uiState.dynamicSections.isEmpty()

            when {
                isInitialLoading -> {
                    MusicScreenShimmerLoading()
                }
                
                uiState.error != null && uiState.trendingSongs.isEmpty() -> {
                    ErrorContent(
                        error = uiState.error ?: stringResource(R.string.error_occurred),
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    val popularArtists = remember(uiState.trendingSongs, uiState.newReleases) {
                        (uiState.trendingSongs + uiState.newReleases)
                            .distinctBy { it.artist }
                            .take(10)
                    }

                    val speedDialTracks = remember(uiState.history, uiState.forYouTracks, uiState.listenAgain) {
                        (uiState.history + uiState.forYouTracks + uiState.listenAgain)
                            .audioMusicOnly()
                            .take(26)
                    }
                    val quickPickTracks = remember(uiState.forYouTracks) {
                        uiState.forYouTracks.audioMusicOnly().take(20)
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = musicListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                bottom = 80.dp
                            )
                        ) {
                            // Listen Again
                            if (uiState.listenAgain.isNotEmpty()) {
                            item {
                                NavigationTitle(title = stringResource(R.string.section_listen_again))
                                val listenThumbnailHeight = currentGridThumbnailHeight()
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.listenAgain) { track ->
                                        GridItem(
                                            title = track.title,
                                            subtitle = track.artist,
                                            thumbnailUrl = track.thumbnailUrl,
                                            thumbnailHeight = listenThumbnailHeight,
                                            onClick = { onSongClick(track, uiState.listenAgain, "listen_again") }
                                        )
                                    }
                                }
                            }
                        }

                        // Home Chips
                        if (uiState.homeChips.isNotEmpty()) {
                            item {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.homeChips) { chip ->
                                        val isChipSelected = uiState.selectedHomeChip?.title == chip.title
                                        ContentFilterChip(
                                            title = chip.title,
                                            isSelected = isChipSelected,
                                            onClick = { 
                                                if (isChipSelected) {
                                                    viewModel.setHomeChip(null)
                                                } else {
                                                    viewModel.setHomeChip(chip)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.selectedFilter != null) {
                            if (uiState.isSearching) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            } else {
                                items(uiState.allSongs) { track ->
                                    MusicTrackRow(
                                        track = track,
                                        isPlaying = currentTrack?.videoId == track.videoId,
                                        isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                        onClick = { onSongClick(track, uiState.allSongs, uiState.selectedFilter) },
                                        onLongClick = {
                                            selectedTrack = track
                                            showBottomSheet = true
                                        },
                                        onMenuClick = {
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }
                        } else {
                            if (speedDialTracks.isNotEmpty()) {
                                item {
                                    SpeedDialSection(
                                        speedDialTracks = speedDialTracks,
                                        downloadedTrackIds = uiState.downloadedTrackIds,
                                        onSongClick = onSongClick,
                                        onTrackMenu = { track ->
                                            selectedTrack = track
                                            showBottomSheet = true
                                        }
                                    )
                                }
                            }

                            sectionOrder.forEach { sectionType ->
                                when (sectionType) {
                                    HomeSectionType.DAILY_DISCOVER -> {
                                        if (uiState.dailyDiscover.isNotEmpty()) {
                                            item {
                                                val discoverTracks = uiState.dailyDiscover.map { it.recommendation }.audioMusicOnly()
                                                SectionHeader(
                                                    title = stringResource(R.string.section_daily_discover),
                                                    onPlayAll = discoverTracks.firstOrNull()?.let { first ->
                                                        { onSongClick(first, discoverTracks, "daily_discover") }
                                                    }
                                                )
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(336.dp)
                                                        .padding(bottom = 16.dp)
                                                ) {
                                                    items(uiState.dailyDiscover.filter { it.recommendation.isAudioMusicCandidate() }) { item ->
                                                        DailyDiscoverCard(
                                                            item = item,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(item.recommendation.videoId),
                                                            onClick = {
                                                                onSongClick(item.recommendation, discoverTracks, "daily_discover")
                                                            },
                                                            onLongClick = {
                                                                selectedTrack = item.recommendation
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.QUICK_PICKS -> {
                                        if (quickPickTracks.isNotEmpty()) {
                                            item {
                                                SectionHeader(
                                                    title = stringResource(R.string.section_quick_picks),
                                                    onPlayAll = quickPickTracks.firstOrNull()?.let { first ->
                                                        { onSongClick(first, quickPickTracks, "quick_picks") }
                                                    }
                                                )
                                                LazyHorizontalGrid(
                                                    rows = GridCells.Fixed(4),
                                                    state = quickPicksGridState,
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    items(quickPickTracks) { track ->
                                                        TrackListItem(
                                                            track = track,
                                                            isPlaying = currentTrack?.videoId == track.videoId,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            showMenu = false,
                                                            onClick = { onSongClick(track, quickPickTracks, "quick_picks") },
                                                            onLongClick = {
                                                                selectedTrack = track
                                                                showBottomSheet = true
                                                            },
                                                            modifier = Modifier.width(320.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.FROM_COMMUNITY -> {
                                        if (uiState.communityPlaylists.isNotEmpty()) {
                                            item {
                                                CommunityPlaylistsSection(
                                                    playlists = uiState.communityPlaylists,
                                                    downloadedTrackIds = uiState.downloadedTrackIds,
                                                    onPlaylistClick = { onAlbumClick(it.playlist.id) },
                                                    onPlaylistAction = { item ->
                                                        selectedCollection = item.playlist.toCollectionActionItem(isAlbum = false)
                                                    },
                                                    onTrackClick = { track, tracks ->
                                                        onSongClick(track, tracks, "from_the_community")
                                                    },
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.RECOMMENDED -> {
                                        if (uiState.recommendedTracks.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_recommended))
                                                val thumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(uiState.recommendedTracks) { track ->
                                                        GridItem(
                                                            title = track.title,
                                                            subtitle = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            thumbnailHeight = thumbnailHeight,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            onClick = { onSongClick(track, uiState.recommendedTracks, "recommended") },
                                                            onLongClick = {
                                                                selectedTrack = track
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.SIMILAR_TO -> {
                                        uiState.similarToSections.forEach { section ->
                                            item {
                                                if (section.label != null) {
                                                    NavigationTitle(
                                                        title = section.title,
                                                        label = section.label,
                                                        thumbnail = {
                                                            if (section.thumbnailUrl != null) {
                                                                if (section.isArtistSeed) {
                                                                    ArtistThumbnail(
                                                                        thumbnailUrl = section.thumbnailUrl,
                                                                        size = 40.dp
                                                                    )
                                                                } else {
                                                                    ItemThumbnail(
                                                                        thumbnailUrl = section.thumbnailUrl,
                                                                        size = 40.dp,
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = if (!section.seedId.isNullOrBlank()) {
                                                            {
                                                                if (section.isArtistSeed) {
                                                                    onArtistClick(section.seedId)
                                                                } else {
                                                                }
                                                            }
                                                        } else null
                                                    )
                                                } else {
                                                    SectionTitle(title = section.title, subtitle = section.subtitle)
                                                }

                                                val sectionThumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(section.tracks) { track ->
                                                        GridItem(
                                                            title = track.title,
                                                            subtitle = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            thumbnailHeight = sectionThumbnailHeight,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            onClick = { 
                                                                when (track.itemType) {
                                                                    MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                                    else -> onSongClick(track, section.tracks, section.title)
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (track.itemType == MusicItemType.ALBUM || track.itemType == MusicItemType.PLAYLIST) {
                                                                    selectedCollection = MusicCollectionActionItem(
                                                                        id = track.videoId,
                                                                        title = track.title,
                                                                        subtitle = track.artist,
                                                                        thumbnailUrl = track.thumbnailUrl,
                                                                        isAlbum = track.itemType == MusicItemType.ALBUM
                                                                    )
                                                                } else {
                                                                    selectedTrack = track
                                                                    showBottomSheet = true
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.LIVE_PERFORMANCES -> {
                                        if (uiState.livePerformances.isNotEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_live_performances),
                                                    tracks = uiState.livePerformances,
                                                    downloadedTrackIds = uiState.downloadedTrackIds,
                                                    onPlayAll = {
                                                        uiState.livePerformances.firstOrNull()?.let {
                                                            onSongClick(it, uiState.livePerformances, "live_performances")
                                                        }
                                                    },
                                                    onTrackClick = { track -> onSongClick(track, uiState.livePerformances, "live_performances") },
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.MUSIC_VIDEOS_FOR_YOU -> {
                                        val videosForYou = uiState.musicVideosForYou.ifEmpty { uiState.musicVideos }
                                        if (videosForYou.isNotEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_music_videos_for_you),
                                                    tracks = videosForYou,
                                                    downloadedTrackIds = uiState.downloadedTrackIds,
                                                    onPlayAll = { videosForYou.firstOrNull()?.let(onVideoClick) },
                                                    onTrackClick = onVideoClick,
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.MUSIC_VIDEOS -> {
                                        if (uiState.musicVideos.isNotEmpty() && uiState.musicVideosForYou.isEmpty()) {
                                            item {
                                                MediaTrackListSection(
                                                    title = stringResource(R.string.section_music_videos),
                                                    tracks = uiState.musicVideos,
                                                    downloadedTrackIds = uiState.downloadedTrackIds,
                                                    onPlayAll = { uiState.musicVideos.firstOrNull()?.let(onVideoClick) },
                                                    onTrackClick = onVideoClick,
                                                    onTrackMenu = { track ->
                                                        selectedTrack = track
                                                        showBottomSheet = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    HomeSectionType.GENRES -> {
                                        uiState.genreTracks.entries.take(3).forEach { (genre, tracks) ->
                                            item {
                                                SectionTitle(title = stringResource(R.string.genre_mix_template, genre))
                                                val genreThumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(tracks) { track ->
                                                        GridItem(
                                                            title = track.title,
                                                            subtitle = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            thumbnailHeight = genreThumbnailHeight,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            onClick = { onSongClick(track, tracks, genre) },
                                                            onLongClick = {
                                                                selectedTrack = track
                                                                showBottomSheet = true
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.DYNAMIC_HOME -> {
                                        uiState.dynamicSections.forEach { section ->
                                            if (!section.title.contains("Quick picks", true) && 
                                                !section.title.contains("Music videos", true) &&
                                                !section.title.contains("Music videos for you", true) &&
                                                !section.title.contains("Live performances", true) &&
                                                !section.title.contains("Long listens", true) &&
                                                !section.title.contains("Mixed for you", true) &&
                                                !section.title.contains("Recommended", true) &&
                                                !section.title.contains("Listen again", true)) {
                                                
                                                item {
                                                    SectionTitle(title = section.title)
                                                    val sectionThumbnailHeight = currentGridThumbnailHeight()
                                                    LazyRow(
                                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        items(section.tracks) { track ->
                                                            GridItem(
                                                                title = track.title,
                                                                subtitle = track.artist,
                                                                thumbnailUrl = track.thumbnailUrl,
                                                                thumbnailHeight = sectionThumbnailHeight,
                                                                isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                                onClick = { 
                                                                    when (track.itemType) {
                                                                        MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                                        else -> onSongClick(track, section.tracks, section.title)
                                                                    }
                                                                },
                                                                onLongClick = {
                                                                    if (track.itemType == MusicItemType.ALBUM || track.itemType == MusicItemType.PLAYLIST) {
                                                                        selectedCollection = MusicCollectionActionItem(
                                                                            id = track.videoId,
                                                                            title = track.title,
                                                                            subtitle = track.artist,
                                                                            thumbnailUrl = track.thumbnailUrl,
                                                                            isAlbum = track.itemType == MusicItemType.ALBUM
                                                                        )
                                                                    } else {
                                                                        selectedTrack = track
                                                                        showBottomSheet = true
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.TOP_ALBUMS -> {
                                        if (uiState.topAlbums.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_top_albums))
                                                val albumThumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(uiState.topAlbums) { album ->
                                                        GridItem(
                                                            title = album.title,
                                                            subtitle = album.author,
                                                            thumbnailUrl = album.thumbnailUrl,
                                                            thumbnailHeight = albumThumbnailHeight,
                                                            onClick = { onAlbumClick(album.id) },
                                                            onLongClick = {
                                                                selectedCollection = album.toCollectionActionItem(isAlbum = true)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.NEW_RELEASES -> {
                                        if (uiState.newReleases.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_new_releases))
                                                val newReleaseThumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(uiState.newReleases.take(10)) { track ->
                                                        GridItem(
                                                            title = track.title,
                                                            subtitle = stringResource(R.string.subtitle_single_template, track.artist),
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            thumbnailHeight = newReleaseThumbnailHeight,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            onClick = { 
                                                                when (track.itemType) {
                                                                    MusicItemType.ALBUM, MusicItemType.PLAYLIST -> onAlbumClick(track.videoId)
                                                                    else -> onSongClick(track, uiState.newReleases, "new_releases")
                                                                }
                                                            },
                                                            onLongClick = {
                                                                if (track.itemType == MusicItemType.ALBUM || track.itemType == MusicItemType.PLAYLIST) {
                                                                    selectedCollection = MusicCollectionActionItem(
                                                                        id = track.videoId,
                                                                        title = track.title,
                                                                        subtitle = track.artist,
                                                                        thumbnailUrl = track.thumbnailUrl,
                                                                        isAlbum = track.itemType == MusicItemType.ALBUM
                                                                    )
                                                                } else {
                                                                    selectedTrack = track
                                                                    showBottomSheet = true
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.CHARTS -> {
                                        if (uiState.trendingSongs.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.trending))
                                                LazyHorizontalGrid(
                                                    rows = GridCells.Fixed(4),
                                                    state = rememberLazyGridState(),
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier
                                                        .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                                        .fillMaxWidth()
                                                ) {
                                                    items(uiState.trendingSongs.take(20).size) { index ->
                                                        val track = uiState.trendingSongs[index]
                                                        ChartTrackItem(
                                                            rank = index + 1,
                                                            title = track.title,
                                                            artist = track.artist,
                                                            thumbnailUrl = track.thumbnailUrl,
                                                            isPlaying = currentTrack?.videoId == track.videoId,
                                                            isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                                            onClick = { onSongClick(track, uiState.trendingSongs, "charts") },
                                                            onLongClick = {
                                                                selectedTrack = track
                                                                showBottomSheet = true
                                                            },
                                                            modifier = Modifier.width(280.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.POPULAR_ARTISTS -> {
                                        if (popularArtists.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_popular_artists))
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(popularArtists) { track ->
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier
                                                                .width(100.dp)
                                                                .clickable { onArtistClick(track.channelId) }
                                                        ) {
                                                            ArtistThumbnail(
                                                                thumbnailUrl = track.thumbnailUrl,
                                                                size = 100.dp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = track.artist,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.MIXED_FOR_YOU -> {
                                        if (uiState.featuredPlaylists.isNotEmpty()) {
                                            item {
                                                SectionTitle(title = stringResource(R.string.section_mixed_for_you))
                                                val playlistThumbnailHeight = currentGridThumbnailHeight()
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(uiState.featuredPlaylists) { playlist ->
                                                        GridItem(
                                                            title = playlist.title,
                                                            subtitle = playlist.author,
                                                            thumbnailUrl = playlist.thumbnailUrl,
                                                            thumbnailHeight = playlistThumbnailHeight,
                                                            onClick = { onAlbumClick(playlist.id) },
                                                            onLongClick = {
                                                                selectedCollection = playlist.toCollectionActionItem(isAlbum = false)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    HomeSectionType.MOODS_AND_GENRES -> {
                                        if (uiState.moodsAndGenres.isNotEmpty()) {
                                            item {
                                                NavigationTitle(
                                                    title = stringResource(R.string.section_mood_and_genres),
                                                    onClick = { onMoodsClick(null) },
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                                
                                                val moodItems = remember(uiState.moodsAndGenres) {
                                                    uiState.moodsAndGenres.flatMap { it.items }
                                                }
                                                
                                                val rows = 4
                                                val moodButtonWidth = ((LocalConfiguration.current.screenWidthDp.dp - 36.dp) / 2)
                                                val gridHeight = (Dimensions.MoodButtonHeight * rows) + (8.dp * (rows - 1))
                                                
                                                LazyHorizontalGrid(
                                                    rows = GridCells.Fixed(rows),
                                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier
                                                        .height(gridHeight)
                                                        .fillMaxWidth()
                                                ) {
                                                    items(moodItems) { item ->
                                                        MoodAndGenresButton(
                                                            title = item.title,
                                                            onClick = { onMoodsClick(item) },
                                                            modifier = Modifier.width(moodButtonWidth)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState.homeContinuation != null) {
                                item {
                                    LaunchedEffect(Unit) {
                                        viewModel.loadMoreHomeContent()
                                    }
                                    Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (uiState.isMoreLoading) {
                                                Modifier.padding(16.dp)
                                            } else {
                                                Modifier.height(0.dp)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.isMoreLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
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
}

private fun MusicPlaylist.toCollectionActionItem(isAlbum: Boolean): MusicCollectionActionItem =
    MusicCollectionActionItem(
        id = id,
        title = title,
        subtitle = author,
        thumbnailUrl = thumbnailUrl,
        description = if (trackCount > 0) "$trackCount tracks" else author,
        isAlbum = isAlbum
    )
