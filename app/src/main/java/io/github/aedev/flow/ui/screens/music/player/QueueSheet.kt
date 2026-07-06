package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.FILTER_ALL
import io.github.aedev.flow.ui.screens.music.FILTER_DEEP_CUTS
import io.github.aedev.flow.ui.screens.music.FILTER_DISCOVER
import io.github.aedev.flow.ui.screens.music.FILTER_POPULAR
import io.github.aedev.flow.ui.screens.music.FILTER_WORKOUT
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.components.TrackListItem
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.compositionLocalOf

val LocalPlayerAccentColor = compositionLocalOf<Color?> { null }
val LocalPlayerOnAccentColor = compositionLocalOf<Color?> { null }
val LocalPlayerOnSheetColor = compositionLocalOf<Color?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    sheetBackgroundColor: Color,
    accentColor: Color,
    onSheetColor: Color = Color.Unspecified,
    sheetCornerRadius: Dp,
    queue: List<MusicTrack>,
    automixTracks: List<MusicTrack>,
    currentIndex: Int,
    downloadedTrackIds: Set<String>,
    playingFrom: String,
    selectedFilter: String,
    isAutomixLoading: Boolean,
    onTrackClick: (Int) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onFilterSelect: (String) -> Unit,
    onAutomixTrackClick: (MusicTrack) -> Unit,
    onPlayNextAutomix: (MusicTrack) -> Unit,
    onAddToQueueAutomix: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    val resolvedOnSheetColor = remember(sheetBackgroundColor, onSheetColor) {
        if (onSheetColor != Color.Unspecified) {
            onSheetColor
        } else if (sheetBackgroundColor.luminance() < 0.45f) {
            Color.White
        } else {
            Color(0xFF161616)
        }
    }
    val onAccentColor = remember(accentColor) {
        if (accentColor.luminance() < 0.55f) Color.White else Color(0xFF161616)
    }
    val adaptiveSheetColors = lightColorScheme(
        primary = accentColor,
        onPrimary = onAccentColor,
        surface = sheetBackgroundColor,
        onSurface = resolvedOnSheetColor,
        onSurfaceVariant = resolvedOnSheetColor.copy(alpha = 0.72f),
        surfaceVariant = resolvedOnSheetColor.copy(alpha = 0.12f),
        outline = resolvedOnSheetColor.copy(alpha = 0.32f),
        background = sheetBackgroundColor,
        onBackground = resolvedOnSheetColor
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = sheetBackgroundColor,
        shape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
        shadowElevation = 24.dp,
        tonalElevation = 0.dp
    ) {
        CompositionLocalProvider(
            LocalPlayerAccentColor provides accentColor,
            LocalPlayerOnAccentColor provides onAccentColor,
            LocalPlayerOnSheetColor provides resolvedOnSheetColor
        ) {
            MaterialTheme(colorScheme = adaptiveSheetColors) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to resolvedOnSheetColor.copy(alpha = 0.08f),
                                    0.28f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = if (sheetBackgroundColor.luminance() < 0.45f) 0.18f else 0.04f)
                                )
                            )
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .then(dragHandleModifier)
                                .fillMaxWidth()
                                .padding(top = 14.dp, bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(5.dp)
                                    .background(
                                        color = accentColor.copy(alpha = 0.62f),
                                        shape = CircleShape
                                    )
                                    .align(Alignment.Center)
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp, start = 16.dp, end = 16.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
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
                                                text = playingFrom.ifBlank { stringResource(R.string.unknown_source) },
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                itemsIndexed(
                                    items = queue,
                                    key = { index, track -> "${track.videoId}_$index" }
                                ) { index, track ->
                                    TrackListItem(
                                        track = track,
                                        isPlaying = index == currentIndex,
                                        isDownloaded = downloadedTrackIds.contains(track.videoId),
                                        onClick = { onTrackClick(index) },
                                        showMenu = false,
                                        trailingContent = {
                                            Column {
                                                IconButton(
                                                    onClick = { if (index > 0) onMoveTrack(index, index - 1) },
                                                    modifier = Modifier.size(24.dp),
                                                    enabled = index > 0
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowUp,
                                                        contentDescription = stringResource(R.string.move_up),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (index > 0) 0.45f else 0.16f)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { if (index < queue.size - 1) onMoveTrack(index, index + 1) },
                                                    modifier = Modifier.size(24.dp),
                                                    enabled = index < queue.size - 1
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.KeyboardArrowDown,
                                                        contentDescription = stringResource(R.string.move_down),
                                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (index < queue.size - 1) 0.45f else 0.16f)
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    val filters = listOf(
                                        FILTER_ALL to stringResource(R.string.view_all_button_label),
                                        FILTER_DISCOVER to stringResource(R.string.filter_discover),
                                        FILTER_POPULAR to stringResource(R.string.filter_popular),
                                        FILTER_DEEP_CUTS to stringResource(R.string.filter_deep_cuts),
                                        FILTER_WORKOUT to stringResource(R.string.filter_workout)
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        items(filters, key = { it.first }) { (filter, label) ->
                                            FilterChip(
                                                selected = selectedFilter == filter,
                                                onClick = { onFilterSelect(filter) },
                                                label = { Text(label) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                                    selectedContainerColor = accentColor,
                                                    selectedLabelColor = onAccentColor
                                                ),
                                                border = null,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = resolvedOnSheetColor.copy(alpha = 0.15f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.similar_content),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }

                                if (automixTracks.isEmpty() && isAutomixLoading) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = accentColor,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                } else if (automixTracks.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.no_similar_content_found),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                                            modifier = Modifier.padding(vertical = 24.dp)
                                        )
                                    }
                                } else {
                                    items(automixTracks, key = { it.videoId }) { track ->
                                        TrackListItem(
                                            track = track,
                                            isDownloaded = downloadedTrackIds.contains(track.videoId),
                                            onClick = { onAutomixTrackClick(track) },
                                            showMenu = false,
                                            trailingContent = {
                                                AutomixQuickActions(
                                                    onPlayNext = { onPlayNextAutomix(track) },
                                                    onAddToQueue = { onAddToQueueAutomix(track) },
                                                    onSheetColor = resolvedOnSheetColor
                                                )
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
}

@Composable
private fun AutomixQuickActions(
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSheetColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPlayNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = stringResource(R.string.play_next),
                tint = onSheetColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onAddToQueue,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Queue,
                contentDescription = stringResource(R.string.add_to_queue),
                tint = onSheetColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
