package com.flow.youtube.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flow.youtube.R
import com.flow.youtube.ui.screens.music.LyricLine
import com.flow.youtube.ui.screens.music.MusicTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpNextContent(
    queue: List<MusicTrack>,
    currentIndex: Int,
    playingFrom: String,
    autoplayEnabled: Boolean,
    selectedFilter: String,
    onTrackClick: (Int) -> Unit,
    onToggleAutoplay: () -> Unit,
    onFilterSelect: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playing_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = playingFrom,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Button(
                onClick = { /* Save to playlist */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Autoplay Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.autoplay),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Switch(
                checked = autoplayEnabled,
                onCheckedChange = { onToggleAutoplay() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Filter Chips
        val filters = listOf(
            stringResource(R.string.view_all_button_label),
            stringResource(R.string.filter_discover),
            stringResource(R.string.filter_popular),
            stringResource(R.string.filter_deep_cuts),
            stringResource(R.string.filter_workout)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface
                    ),
                    border = null,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Queue List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            itemsIndexed(queue) { index, track ->
                UpNextTrackItem(
                    track = track,
                    isCurrentlyPlaying = index == currentIndex,
                    onClick = { onTrackClick(index) },
                    onMoveUp = { if (index > 0) onMoveTrack(index, index - 1) },
                    onMoveDown = { if (index < queue.size - 1) onMoveTrack(index, index + 1) }
                )
            }
        }
    }
}

@Composable
fun LyricsContent(
    lyrics: String?,
    syncedLyrics: List<LyricLine>,
    currentPosition: Long,
    isLoading: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val textColor = MaterialTheme.colorScheme.onSurface
    val dimmedTextColor = textColor.copy(alpha = 0.4f)
    val loaderColor = MaterialTheme.colorScheme.primary
    
    val currentLineIndex = remember(currentPosition, syncedLyrics) {
        val index = syncedLyrics.indexOfLast { it.time <= currentPosition }
        if (index == -1) 0 else index
    }
    
    LaunchedEffect(currentLineIndex) {
        if (syncedLyrics.isNotEmpty() && currentLineIndex > 0) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -400)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = loaderColor)
            }
        } else if (syncedLyrics.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 200.dp, start = 24.dp, end = 24.dp)
            ) {
                itemsIndexed(syncedLyrics) { index, line ->
                    val isCurrent = index == currentLineIndex
                    val alpha by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else 0.4f,
                        animationSpec = tween(durationMillis = 600),
                        label = "lyric_alpha"
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isCurrent) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
                        label = "lyric_scale"
                    )
                    
                    Text(
                        text = line.content,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 28.sp,
                            lineHeight = 38.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                this.alpha = alpha
                                this.scaleX = scale
                                this.scaleY = scale
                            }
                            .clickable { onSeekTo(line.time) },
                        textAlign = TextAlign.Start
                    )
                }
            }
        } else if (lyrics != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp)
            ) {
                item {
                    Text(
                        text = lyrics,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = textColor.copy(alpha = 0.9f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lyrics_not_available), color = dimmedTextColor)
            }
        }
    }
}

@Composable
fun RelatedContent(
    relatedTracks: List<MusicTrack>,
    isLoading: Boolean,
    onTrackClick: (MusicTrack) -> Unit
) {
    val dimmedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 20.dp)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (relatedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_related_content), color = dimmedTextColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp)
            ) {
                items(relatedTracks) { track ->
                    RelatedTrackItem(
                        track = track,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}
