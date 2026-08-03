package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.music.MusicTrack

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
    onMoveTrack: (Int, Int) -> Unit,
) {
    val accentColor = LocalPlayerAccentColor.current ?: MaterialTheme.colorScheme.primary
    val onAccentColor = LocalPlayerOnAccentColor.current ?: MaterialTheme.colorScheme.onPrimary
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.playing_from),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
                Text(
                    text = playingFrom,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Button(
                onClick = { /* Save to playlist */ },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.autoplay),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Switch(
                checked = autoplayEnabled,
                onCheckedChange = { onToggleAutoplay() },
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = onAccentColor,
                        checkedTrackColor = accentColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter Chips
        val filters =
            listOf(
                stringResource(R.string.view_all_button_label),
                stringResource(R.string.filter_discover),
                stringResource(R.string.filter_popular),
                stringResource(R.string.filter_deep_cuts),
                stringResource(R.string.filter_workout),
            )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(filters, key = { it }) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) },
                    label = { Text(filter) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = accentColor,
                            selectedLabelColor = onAccentColor,
                        ),
                    border = null,
                    shape = RoundedCornerShape(8.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Queue List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 100.dp),
        ) {
            itemsIndexed(queue, key = { index, track -> "${track.videoId}_$index" }) { index, track ->
                UpNextTrackItem(
                    track = track,
                    isCurrentlyPlaying = index == currentIndex,
                    onClick = { onTrackClick(index) },
                    onMoveUp = { if (index > 0) onMoveTrack(index, index - 1) },
                    onMoveDown = { if (index < queue.size - 1) onMoveTrack(index, index + 1) },
                )
            }

            item {
                if (autoplayEnabled) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.end_of_queue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelatedContent(
    relatedTracks: List<MusicTrack>,
    isLoading: Boolean,
    onTrackClick: (MusicTrack) -> Unit,
) {
    val dimmedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val accentColor = LocalPlayerAccentColor.current ?: MaterialTheme.colorScheme.primary

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp),
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (relatedTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_related_content), color = dimmedTextColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp, top = 16.dp),
            ) {
                items(relatedTracks, key = { it.videoId }) { track ->
                    RelatedTrackItem(
                        track = track,
                        onClick = { onTrackClick(track) },
                    )
                }
            }
        }
    }
}
