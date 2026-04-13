package io.github.aedev.flow.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.utils.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onVideoClick: (videos: List<DownloadedVideo>, startIndex: Int) -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    val context = LocalContext.current

    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_VIDEO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {  }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, storagePermission) ==
            PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            permissionLauncher.launch(storagePermission)
        }
    }

    fun requestDelete(id: String, type: DeletionType) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (type) {
            DeletionType.VIDEO -> viewModel.deleteVideoDownload(id)
            DeletionType.MUSIC -> viewModel.deleteMusicDownload(id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DownloadsTabSelector(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = {
                    if (it != selectedTabIndex) {
                        haptic.performHapticFeedback(
                            HapticFeedbackType.TextHandleMove
                        )
                        selectedTabIndex = it
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Crossfade(
                targetState = selectedTabIndex,
                animationSpec = tween(250, easing = EaseOutCubic),
                label = "tab_crossfade",
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) { targetIndex ->
                when (targetIndex) {
                    0 -> VideosDownloadsList(
                        videos = uiState.downloadedVideos,
                        activeDownloads = uiState.activeVideoDownloads,
                        progressMap = uiState.downloadProgressMap,
                        isRefreshing = uiState.isScanning,
                        onRefresh = { viewModel.rescan() },
                        onVideoClick = { videos, index -> onVideoClick(videos, index) },
                        onDeleteClick = { id ->
                            requestDelete(id, DeletionType.VIDEO)
                        },
                        onHomeClick = onHomeClick
                    )
                    1 -> MusicDownloadsList(
                        tracks = uiState.downloadedMusic,
                        isRefreshing = uiState.isScanning,
                        onRefresh = { viewModel.rescan() },
                        onMusicClick = onMusicClick,
                        onDeleteClick = { id ->
                            requestDelete(id, DeletionType.MUSIC)
                        },
                        onHomeClick = onHomeClick
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// DELETION TYPE
// ═══════════════════════════════════════════════════════

private enum class DeletionType { VIDEO, MUSIC }

// ═══════════════════════════════════════════════════════
// CUSTOM TAB SELECTOR
// ═══════════════════════════════════════════════════════

@Composable
private fun DownloadsTabSelector(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        TabInfo(
            title = stringResource(R.string.tab_videos),
            icon = Icons.Outlined.VideoLibrary
        ),
        TabInfo(
            title = stringResource(R.string.tab_music),
            icon = Icons.Outlined.MusicNote
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest
                    .copy(alpha = 0.5f)
            )
            .padding(4.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / tabs.size

            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedTabIndex,
                animationSpec = spring(
                    dampingRatio = 0.75f,
                    stiffness = 400f
                ),
                label = "indicator_offset"
            )

            Box(
                modifier = Modifier
                    .width(tabWidth)
                    .fillMaxHeight()
                    .offset(x = indicatorOffset)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selectedTabIndex == index

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.7f),
                    animationSpec = tween(250),
                    label = "tab_color_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = null,
                            role = Role.Tab
                        ) { onTabSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isSelected)
                                FontWeight.SemiBold
                            else FontWeight.Normal,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

private data class TabInfo(
    val title: String,
    val icon: ImageVector
)

// ═══════════════════════════════════════════════════════
// VIDEO DOWNLOADS LIST
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    activeDownloads: List<DownloadWithItems>,
    progressMap: Map<String, Float>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onVideoClick: (List<DownloadedVideo>, Int) -> Unit,
    onDeleteClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty() && activeDownloads.isEmpty()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                EmptyDownloadsState(
                    type = stringResource(R.string.tab_videos),
                    icon = Icons.Outlined.VideoLibrary,
                    onHomeClick = onHomeClick
                )
            }
        }
    } else {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (activeDownloads.isNotEmpty()) {
                    item(key = "section_active") {
                        Text(
                            text = stringResource(R.string.section_downloading),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 6.dp
                            )
                        )
                    }
                    items(
                        items = activeDownloads,
                        key = { "active_${it.download.videoId}" }
                    ) { dl ->
                        ActiveVideoDownloadCard(
                            download = dl,
                            progressMap = progressMap,
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(300, easing = EaseOutCubic),
                                fadeOutSpec = tween(200, easing = EaseInCubic),
                                placementSpec = spring(
                                    dampingRatio = 0.8f,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        )
                    }
                    if (videos.isNotEmpty()) {
                        item(key = "section_completed") {
                            Text(
                                text = stringResource(R.string.section_completed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 6.dp
                                )
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = videos,
                    key = { _, video -> video.video.id }
                ) { index, video ->
                    VideoDownloadCard(
                        video = video,
                        onClick = { onVideoClick(videos, index) },
                        onDeleteClick = { onDeleteClick(video.video.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(300, easing = EaseOutCubic),
                            fadeOutSpec = tween(200, easing = EaseInCubic),
                            placementSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// VIDEO CARD
// ═══════════════════════════════════════════════════════

@Composable
private fun VideoDownloadCard(
    video: DownloadedVideo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleteDesc = stringResource(
        R.string.cd_delete_download,
        video.video.title
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(152.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = video.video.thumbnailUrl,
                contentDescription = stringResource(
                    R.string.cd_video_thumbnail,
                    video.video.title
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Surface(
                color = MaterialTheme.colorScheme.inverseSurface
                    .copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = formatDuration(video.video.duration),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(
                        horizontal = 4.dp,
                        vertical = 2.dp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.video.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = video.video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.semantics {
                contentDescription = deleteDesc
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// ACTIVE DOWNLOAD CARD (in-progress)
// ═══════════════════════════════════════════════════════

@Composable
private fun ActiveVideoDownloadCard(
    download: DownloadWithItems,
    progressMap: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    val progress = (progressMap[download.download.videoId] ?: download.progress).coerceIn(0f, 1f)
    val pct = (progress * 100).toInt()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(152.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = download.download.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Dimming overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
            )
            // Red progress fill from left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(Color(0xFFCD2027).copy(alpha = 0.35f))
            )
            // Percentage label centered
            Text(
                text = "$pct%",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
            // Thin progress bar at the bottom edge
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.download.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = download.download.uploader,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            val statusText = when (download.overallStatus) {
                DownloadItemStatus.PENDING  -> stringResource(R.string.download_status_queued)
                DownloadItemStatus.PAUSED   -> "$pct% · ${stringResource(R.string.download_status_paused)}"
                DownloadItemStatus.FAILED   -> stringResource(R.string.download_status_failed)
                else                        -> "$pct%"
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// MUSIC DOWNLOADS LIST
// ═══════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicDownloadsList(
    tracks: List<DownloadedTrack>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onDeleteClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                EmptyDownloadsState(
                    type = stringResource(R.string.tab_music),
                    icon = Icons.Outlined.MusicNote,
                    onHomeClick = onHomeClick
                )
            }
        }
    } else {
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.track.videoId }
                ) { index, downloadedTrack ->
                    MusicTrackCard(
                        downloadedTrack = downloadedTrack,
                        onClick = { onMusicClick(tracks, index) },
                        onDeleteClick = {
                            onDeleteClick(downloadedTrack.track.videoId)
                        },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(300, easing = EaseOutCubic),
                            fadeOutSpec = tween(200, easing = EaseInCubic),
                            placementSpec = spring(
                                dampingRatio = 0.8f,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// MUSIC TRACK CARD
// ═══════════════════════════════════════════════════════

@Composable
private fun MusicTrackCard(
    downloadedTrack: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deleteDesc = stringResource(
        R.string.cd_delete_download,
        downloadedTrack.track.title
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest
                )
        ) {
            AsyncImage(
                model = downloadedTrack.track.thumbnailUrl,
                contentDescription = stringResource(
                    R.string.cd_track_artwork,
                    downloadedTrack.track.title
                ),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = downloadedTrack.track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloadedTrack.track.isExplicit == true) {
                    Surface(
                        color = MaterialTheme.colorScheme
                            .surfaceContainerHighest,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.explicit),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                horizontal = 4.dp,
                                vertical = 1.dp
                            ),
                            color = MaterialTheme.colorScheme
                                .onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = downloadedTrack.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.semantics {
                contentDescription = deleteDesc
            }
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
                    .copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// EMPTY STATE 
// ═══════════════════════════════════════════════════════

@Composable
private fun EmptyDownloadsState(
    type: String,
    icon: ImageVector,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, easing = EaseOutCubic)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
                    .copy(alpha = 0.6f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(
                    R.string.empty_offline_title, type
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.empty_offline_body, type
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            FilledTonalButton(
                onClick = onHomeClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme
                        .primaryContainer,
                    contentColor = MaterialTheme.colorScheme
                        .onPrimaryContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.action_go_to_home),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}