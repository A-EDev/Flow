package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MUSIC_GENRE_SOURCE_PREFIX
import io.github.aedev.flow.data.music.model.MusicItemType
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.MusicTimeBucket
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.TabScrollEventBus
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.common.MusicChartRankBadge
import io.github.aedev.flow.ui.components.music.common.MusicErrorState
import io.github.aedev.flow.ui.components.music.common.MusicThumbnail
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicItemDensity
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.section.HomeSectionType
import io.github.aedev.flow.ui.components.music.section.musicHomeFeed
import io.github.aedev.flow.ui.theme.Dimensions
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

private fun MusicTrack.isAudioMusicCandidate(): Boolean {
    val usableDuration = duration == 0 || duration in 30..1200
    return itemType == MusicItemType.SONG && !isVideoSong && videoId.isNotBlank() && usableDuration
}

private fun List<MusicTrack>.audioMusicOnly(): List<MusicTrack> = filter { it.isAudioMusicCandidate() }.distinctBy { it.videoId }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EnhancedMusicScreen(
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onVideoClick: (MusicTrack) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onRecognizeClick: () -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onMoodsClick: (io.github.aedev.flow.innertube.pages.MoodAndGenres.Item?) -> Unit = {},
    viewModel: MusicViewModel = sharedMusicViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val musicListState = rememberLazyListState()
    val quickPicksGridState = rememberLazyGridState()

    // Planner-lite: the brain's maturity decides which sections lead the page —
    // a cold brain has nothing personal to say, a mature one leads with taste.
    val sectionOrder =
        remember(uiState.sessionSeed, uiState.brainMaturity) {
            val defaultOrder = HomeSectionType.values().toList()
            val anchored =
                when (uiState.brainMaturity) {
                    "cold_start" -> {
                        listOf(
                            HomeSectionType.CHARTS,
                            HomeSectionType.MOODS_AND_GENRES,
                            HomeSectionType.NEW_RELEASES,
                        )
                    }

                    "mature" -> {
                        listOf(
                            HomeSectionType.QUICK_PICKS,
                            HomeSectionType.SIMILAR_TO,
                            HomeSectionType.FROM_COMMUNITY,
                        )
                    }

                    else -> {
                        listOf(
                            HomeSectionType.QUICK_PICKS,
                            HomeSectionType.FROM_COMMUNITY,
                            HomeSectionType.DAILY_DISCOVER,
                        )
                    }
                }
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
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, selectedTrack!!.title)
                        val text =
                            context.getString(
                                R.string.share_message_template,
                                selectedTrack!!.title,
                                selectedTrack!!.artist,
                                selectedTrack!!.videoId,
                            )
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = { onAlbumClick(collection.id) },
        )
    }

    Scaffold(
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.screen_title_music),
                actions = {
                    IconButton(onClick = onRecognizeClick) {
                        Icon(Icons.Outlined.Mic, stringResource(R.string.recognize_music))
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, stringResource(R.string.search))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            val isInitialLoading = uiState.isLoading && uiState.trendingSongs.isEmpty() && uiState.dynamicSections.isEmpty()

            when {
                isInitialLoading -> {
                    MusicScreenShimmerLoading()
                }

                uiState.error != null && uiState.trendingSongs.isEmpty() -> {
                    MusicErrorState(
                        error = uiState.error ?: stringResource(R.string.error_occurred),
                        onRetry = { viewModel.retry() },
                    )
                }

                else -> {
                    val popularArtists =
                        remember(uiState.trendingSongs, uiState.newReleases) {
                            (uiState.trendingSongs + uiState.newReleases)
                                .distinctBy { it.artist }
                                .take(10)
                        }

                    // Raw concatenation is only the placeholder until the VM's
                    // brain-ranked speed dial lands.
                    val fallbackSpeedDial =
                        remember(uiState.history, uiState.forYouTracks, uiState.listenAgain) {
                            (uiState.history + uiState.forYouTracks + uiState.listenAgain)
                                .audioMusicOnly()
                                .take(26)
                        }
                    val speedDialTracks = uiState.speedDialTracks.ifEmpty { fallbackSpeedDial }
                    val quickPickTracks =
                        remember(uiState.forYouTracks) {
                            uiState.forYouTracks.audioMusicOnly().take(20)
                        }

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            state = musicListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    bottom = 80.dp,
                                ),
                        ) {
                            musicHomeFeed(
                                uiState = uiState,
                                sectionOrder = sectionOrder,
                                playingVideoId = currentTrack?.videoId,
                                quickPickTracks = quickPickTracks,
                                speedDialTracks = speedDialTracks,
                                popularArtists = popularArtists,
                                quickPicksGridState = quickPicksGridState,
                                onSongClick = onSongClick,
                                onVideoClick = onVideoClick,
                                onArtistClick = onArtistClick,
                                onAlbumClick = onAlbumClick,
                                onMoodsClick = onMoodsClick,
                                onChipToggle = { viewModel.setHomeChip(it) },
                                onTrackMenu = { track ->
                                    selectedTrack = track
                                    showBottomSheet = true
                                },
                                onCollectionMenu = { collection -> selectedCollection = collection },
                                onLoadMore = { viewModel.loadMoreHomeContent() },
                            )
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
        isAlbum = isAlbum,
    )

private fun rotationTitleRes(bucket: MusicTimeBucket): Int =
    when (bucket) {
        MusicTimeBucket.WEEKDAY_MORNING, MusicTimeBucket.WEEKEND_MORNING -> R.string.section_rotation_morning
        MusicTimeBucket.WEEKDAY_AFTERNOON, MusicTimeBucket.WEEKEND_AFTERNOON -> R.string.section_rotation_afternoon
        MusicTimeBucket.WEEKDAY_EVENING, MusicTimeBucket.WEEKEND_EVENING -> R.string.section_rotation_evening
        MusicTimeBucket.WEEKDAY_NIGHT, MusicTimeBucket.WEEKEND_NIGHT -> R.string.section_rotation_night
    }
