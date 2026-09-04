package io.github.aedev.flow.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toMusicTrack
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.ShortsCard
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.MediaRowAction
import io.github.aedev.flow.ui.components.shared.animateMediaListItem
import io.github.aedev.flow.ui.screens.history.HistoryContentFilter
import io.github.aedev.flow.ui.screens.history.HistorySort
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val ListContentPadding = PaddingValues(bottom = 96.dp)
private val FilterRowPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
private val SectionHeaderPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val ShortsRowPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
private val ItemSpacing = 4.dp
private const val DAYS_IN_WEEK = 7
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

@Composable
internal fun HistoryFilterRow(
    shortsEnabled: Boolean,
    selectedFilter: HistoryContentFilter,
    onFilterSelected: (HistoryContentFilter) -> Unit,
    selectedSort: HistorySort,
    onSortSelected: (HistorySort) -> Unit,
    selectedYear: Int?,
    availableYears: List<Int>,
    onYearSelected: (Int?) -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    val visibleFilters =
        remember(shortsEnabled) {
            HistoryContentFilter.entries.filter { shortsEnabled || it != HistoryContentFilter.Shorts }
        }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = FilterRowPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = visibleFilters, key = { it.name }) { filter ->
            FlowFilterChip(
                label = filter.label(),
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }

        item(key = "sort") {
            Box {
                FlowFilterChip(
                    label = selectedSort.label(),
                    selected = selectedSort != HistorySort.Newest,
                    onClick = { sortExpanded = true },
                )
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false },
                ) {
                    HistorySort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label()) },
                            onClick = {
                                sortExpanded = false
                                onSortSelected(sort)
                            },
                        )
                    }
                }
            }
        }

        item(key = "year") {
            Box {
                FlowFilterChip(
                    label = selectedYear?.toString() ?: stringResource(R.string.history_filter_all_years),
                    selected = selectedYear != null,
                    onClick = { yearExpanded = true },
                )
                DropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_filter_all_years)) },
                        onClick = {
                            yearExpanded = false
                            onYearSelected(null)
                        },
                    )
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                yearExpanded = false
                                onYearSelected(year)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryList(
    entries: List<VideoHistoryEntry>,
    shortVideos: Map<String, Video>,
    selectedFilter: HistoryContentFilter,
    onVideoClick: (MusicTrack) -> Unit,
    onShortClick: (row: List<Video>, tapped: Video) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onRemove: (VideoHistoryEntry) -> Unit,
) {
    val groupedEntries =
        remember(entries) {
            entries.groupBy { historySectionKey(it.timestamp) }
        }
    val musicQueue =
        remember(entries) {
            entries.filter { it.isMusic }.map { it.toMusicTrack() }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        groupedEntries.forEach { (sectionKey, sectionEntries) ->
            stickyHeader(key = "header-$sectionKey", contentType = "header") {
                HistorySectionHeader(timestamp = sectionEntries.first().timestamp)
            }

            when (selectedFilter) {
                HistoryContentFilter.Shorts -> {
                    item(key = "shorts-$sectionKey", contentType = "shorts") {
                        ShortsHistoryRow(
                            entries = sectionEntries,
                            shortVideos = shortVideos,
                            onShortClick = onShortClick,
                            onRemove = onRemove,
                        )
                    }
                }

                HistoryContentFilter.All -> {
                    val shorts = sectionEntries.filter { it.isShort && !it.isMusic }
                    val regular = sectionEntries.filter { !it.isShort || it.isMusic }

                    items(
                        items = regular,
                        key = { it.videoId },
                        contentType = { if (it.isMusic) "track" else "video" },
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            musicQueue = musicQueue,
                            onVideoClick = onVideoClick,
                            onMusicClick = onMusicClick,
                            onRemove = onRemove,
                            modifier = animateMediaListItem(),
                        )
                    }

                    if (shorts.isNotEmpty()) {
                        item(key = "shorts-$sectionKey", contentType = "shorts") {
                            ShortsHistoryRow(
                                entries = shorts,
                                shortVideos = shortVideos,
                                onShortClick = onShortClick,
                                onRemove = onRemove,
                            )
                        }
                    }
                }

                else -> {
                    items(
                        items = sectionEntries,
                        key = { it.videoId },
                        contentType = { if (it.isMusic) "track" else "video" },
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            musicQueue = musicQueue,
                            onVideoClick = onVideoClick,
                            onMusicClick = onMusicClick,
                            onRemove = onRemove,
                            modifier = animateMediaListItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistorySectionHeader(timestamp: Long) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Text(
            text = sectionTitle(timestamp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(SectionHeaderPadding),
        )
    }
}

@Composable
internal fun HistoryEntryRow(
    entry: VideoHistoryEntry,
    musicQueue: List<MusicTrack>,
    onVideoClick: (MusicTrack) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onRemove: (VideoHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = remember(entry) { entry.toMusicTrack() }
    val removeLabel = stringResource(R.string.remove_from_history)

    LibraryMediaListRow(
        track = track,
        isMusic = entry.isMusic,
        title = entry.title.ifBlank { entry.videoId },
        onVideoClick = { onVideoClick(track) },
        onMusicClick = { onMusicClick(track, musicQueue) },
        modifier = modifier,
        subtitle = entry.channelName.takeIf { it.isNotBlank() },
        thumbnailUrl = entry.thumbnailUrl,
        durationSeconds = (entry.duration / 1000L).toInt(),
    ) {
        MediaRowAction(
            icon = Icons.Default.Close,
            contentDescription = removeLabel,
            onClick = { onRemove(entry) },
        )
    }
}

@Composable
internal fun ShortsHistoryRow(
    entries: List<VideoHistoryEntry>,
    shortVideos: Map<String, Video>,
    onShortClick: (row: List<Video>, tapped: Video) -> Unit,
    onRemove: (VideoHistoryEntry) -> Unit,
) {
    val rowVideos =
        remember(entries, shortVideos) {
            entries.map { shortVideos[it.videoId] ?: it.toVideo() }
        }
    val entriesById = remember(entries) { entries.associateBy { it.videoId } }
    val removeLabel = stringResource(R.string.remove_from_history)

    LazyRow(
        contentPadding = ShortsRowPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = rowVideos,
            key = Video::id,
            contentType = { "short" },
        ) { video ->
            ShortsCard(
                video = video,
                onClick = { onShortClick(rowVideos, video) },
                trailingContent = {
                    IconButton(onClick = { entriesById[video.id]?.let(onRemove) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = removeLabel,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun HistoryContentFilter.label(): String =
    when (this) {
        HistoryContentFilter.All -> stringResource(R.string.view_all_button_label)
        HistoryContentFilter.Videos -> stringResource(R.string.history_tab_videos)
        HistoryContentFilter.Shorts -> stringResource(R.string.history_tab_shorts)
        HistoryContentFilter.Music -> stringResource(R.string.nav_music)
        HistoryContentFilter.LocalVideos -> stringResource(R.string.history_tab_local_videos)
        HistoryContentFilter.LocalMusic -> stringResource(R.string.history_tab_local_music)
    }

@Composable
internal fun HistorySort.label(): String =
    when (this) {
        HistorySort.Newest -> stringResource(R.string.history_sort_newest)
        HistorySort.Oldest -> stringResource(R.string.history_sort_oldest)
    }

internal fun startOfDay(timestamp: Long): Long =
    Calendar
        .getInstance()
        .apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

internal fun historySectionKey(timestamp: Long): String = startOfDay(timestamp).toString()

@Composable
internal fun sectionTitle(timestamp: Long): String {
    val locale = Locale.getDefault()
    val weekdayFormat = remember(locale) { SimpleDateFormat("EEEE", locale) }
    val dateFormat = remember(locale) { SimpleDateFormat("MMMM d, yyyy", locale) }
    val today = startOfDay(System.currentTimeMillis())
    val target = startOfDay(timestamp)
    val diffDays = ((today - target) / MILLIS_PER_DAY).toInt()

    return when (diffDays) {
        0 -> stringResource(R.string.time_today)
        1 -> stringResource(R.string.time_yesterday)
        in 2 until DAYS_IN_WEEK -> weekdayFormat.format(target)
        else -> dateFormat.format(target)
    }
}
