package com.flow.youtube.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.video.DownloadedVideo

@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    val currentItemCount = if (selectedTabIndex == 0) uiState.downloadedVideos.size else uiState.downloadedMusic.size

    // Background Gradient: Gives depth to the screen
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), // Subtle transition
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Header Section
            DownloadsHeader(
                onBackClick = onBackClick,
                itemCount = currentItemCount
            )

            // 2. Animated Custom Tabs
            DownloadsTabSelector(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // 3. Main Content List
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> VideosDownloadsList(
                        videos = uiState.downloadedVideos,
                        onVideoClick = onVideoClick,
                        onDeleteClick = { viewModel.deleteVideoDownload(it) }
                    )
                    1 -> MusicDownloadsList(
                        tracks = uiState.downloadedMusic,
                        onMusicClick = onMusicClick,
                        onDeleteClick = { viewModel.deleteMusicDownload(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsHeader(
    onBackClick: () -> Unit,
    itemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button with soft background
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = CircleShape
                )
                .size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Downloads",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$itemCount items saved",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A Custom Segmented Control (Pill Shape) Tab Bar
 * Much cleaner than the default Android TabRow
 */
@Composable
fun DownloadsTabSelector(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf("Videos", "Music")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(4.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            
            // The Sliding Indicator
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedTabIndex,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                label = "tab_indicator"
            )

            // The White/Active Background Pill
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .shadow(4.dp, RoundedCornerShape(22.dp))
                    .background(
                        color = MaterialTheme.colorScheme.background, // Or Primary depending on taste
                        shape = RoundedCornerShape(22.dp)
                    )
            )

            // The Text Labels
            Row(modifier = Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTabIndex == index
                    
                    // Animate Text Color
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        label = "text_color"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null // Remove ripple for cleaner look
                            ) { onTabSelected(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (index == 0) Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyDownloadsState(type = "Videos")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(videos) { downloadedVideo ->
                PremiumVideoDownloadCard(
                    video = downloadedVideo,
                    onClick = { onVideoClick(downloadedVideo.video.id) },
                    onDeleteClick = { onDeleteClick(downloadedVideo.video.id) }
                )
            }
        }
    }
}

@Composable
fun PremiumVideoDownloadCard(
    video: DownloadedVideo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Card with minimal elevation but distinct shape
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // 1. Thumbnail Area with Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                AsyncImage(
                    model = video.video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Dark Gradient for Text Readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                )

                // Play Button Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                
                // Duration / Quality Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                     Text(
                        text = "Downloaded", // Replace with duration if available
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }

            // 2. Info Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.video.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MusicDownloadsList(
    tracks: List<DownloadedTrack>,
    onMusicClick: (List<DownloadedTrack>, Int) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyDownloadsState(type = "Music")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(tracks) { index, downloadedTrack ->
                PremiumMusicDownloadCard(
                    downloadedTrack = downloadedTrack,
                    onClick = { onMusicClick(tracks, index) },
                    onDeleteClick = { onDeleteClick(downloadedTrack.track.videoId) }
                )
            }
        }
    }
}

@Composable
fun PremiumMusicDownloadCard(
    downloadedTrack: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    // Elegant Row Layout
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.size(56.dp)
            ) {
                AsyncImage(
                    model = downloadedTrack.track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = downloadedTrack.track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = downloadedTrack.track.artist,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // Subtle Delete Button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyDownloadsState(type: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No $type Found",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Your downloaded $type will appear here.\nStart downloading to play offline!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}