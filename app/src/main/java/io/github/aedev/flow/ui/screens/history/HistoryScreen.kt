package io.github.aedev.flow.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.ShortsCard
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.MusicTrackRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onVideoClick: (MusicTrack) -> Unit,
    onBackClick: () -> Unit,
    onShortClick: (String) -> Unit = {},
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit = { track, _ -> onVideoClick(track) },
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val searchFocusRequester = remember { FocusRequester() }

    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(HistoryContentFilter.All) }
    var selectedSort by rememberSaveable { mutableStateOf(HistorySort.Newest) }
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val availableYears = remember(uiState.historyEntries) {
        uiState.historyEntries
            .map { historyYear(it.timestamp) }
            .distinct()
            .sortedDescending()
    }

    val displayEntries = remember(
        uiState.historyEntries,
        searchQuery,
        selectedFilter,
        selectedSort,
        selectedYear
    ) {
        uiState.historyEntries
            .asSequence()
            .filter { entry -> selectedFilter.matches(entry) }
            .filter { entry -> selectedYear == null || historyYear(entry.timestamp) == selectedYear }
            .filter { entry ->
                val query = searchQuery.trim()
                query.isBlank() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.channelName.contains(query, ignoreCase = true)
            }
            .let { sequence ->
                if (selectedSort == HistorySort.Newest) {
                    sequence.sortedByDescending { it.timestamp }
                } else {
                    sequence.sortedBy { it.timestamp }
                }
            }
            .toList()
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.library_history_label),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clear_all)) },
                                enabled = uiState.historyEntries.isNotEmpty(),
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            HistorySearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                focusRequester = searchFocusRequester
            )

            HistoryFilterRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
                selectedSort = selectedSort,
                onSortSelected = { selectedSort = it },
                selectedYear = selectedYear,
                availableYears = availableYears,
                onYearSelected = { selectedYear = it }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.historyEntries.isEmpty() -> {
                    EmptyHistoryState(modifier = Modifier.fillMaxSize())
                }

                displayEntries.isEmpty() -> {
                    EmptyHistoryState(
                        modifier = Modifier.fillMaxSize(),
                        title = stringResource(R.string.history_no_results),
                        body = stringResource(R.string.history_no_results_body)
                    )
                }

                else -> {
                    HistoryList(
                        entries = displayEntries,
                        shortVideos = uiState.shortVideos,
                        selectedFilter = selectedFilter,
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onMusicClick = onMusicClick,
                        onRemove = viewModel::removeFromHistory
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_watch_history_alert_title)) },
            text = { Text(stringResource(R.string.clear_watch_history_alert_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_watch_history)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun HistoryFilterRow(
    selectedFilter: HistoryContentFilter,
    onFilterSelected: (HistoryContentFilter) -> Unit,
    selectedSort: HistorySort,
    onSortSelected: (HistorySort) -> Unit,
    selectedYear: Int?,
    availableYears: List<Int>,
    onYearSelected: (Int?) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(HistoryContentFilter.values().toList()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label()) }
            )
        }

        item {
            Box {
                FilterChip(
                    selected = selectedSort != HistorySort.Newest,
                    onClick = { sortExpanded = true },
                    label = { Text(selectedSort.label()) }
                )
                DropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { sortExpanded = false }
                ) {
                    HistorySort.values().forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label()) },
                            onClick = {
                                sortExpanded = false
                                onSortSelected(sort)
                            }
                        )
                    }
                }
            }
        }

        item {
            Box {
                FilterChip(
                    selected = selectedYear != null,
                    onClick = { yearExpanded = true },
                    label = {
                        Text(
                            selectedYear?.toString()
                                ?: stringResource(R.string.history_filter_all_years)
                        )
                    }
                )
                DropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_filter_all_years)) },
                        onClick = {
                            yearExpanded = false
                            onYearSelected(null)
                        }
                    )
                    availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                yearExpanded = false
                                onYearSelected(year)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryList(
    entries: List<VideoHistoryEntry>,
    shortVideos: Map<String, Video>,
    selectedFilter: HistoryContentFilter,
    onVideoClick: (MusicTrack) -> Unit,
    onShortClick: (String) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onRemove: (String) -> Unit
) {
    val groupedEntries = remember(entries) {
        entries.groupBy { historySectionKey(it.timestamp) }
    }
    val musicQueue = remember(entries) {
        entries.filter { it.isMusic }.map { it.toMusicTrack() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        groupedEntries.forEach { (sectionKey, sectionEntries) ->
            item(key = "header-$sectionKey") {
                Text(
                    text = sectionTitle(sectionEntries.first().timestamp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            when (selectedFilter) {
                HistoryContentFilter.Shorts -> {
                    item(key = "shorts-$sectionKey") {
                        ShortsHistoryRow(
                            entries = sectionEntries,
                            shortVideos = shortVideos,
                            onShortClick = onShortClick
                        )
                    }
                }

                HistoryContentFilter.All -> {
                    val shorts = sectionEntries.filter { it.isShort && !it.isMusic }
                    val regular = sectionEntries.filter { !it.isShort || it.isMusic }

                    items(
                        items = regular,
                        key = { it.videoId }
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            musicQueue = musicQueue,
                            onVideoClick = onVideoClick,
                            onMusicClick = onMusicClick,
                            onRemove = onRemove
                        )
                    }

                    if (shorts.isNotEmpty()) {
                        item(key = "shorts-$sectionKey") {
                            ShortsHistoryRow(
                                entries = shorts,
                                shortVideos = shortVideos,
                                onShortClick = onShortClick
                            )
                        }
                    }
                }

                else -> {
                    items(
                        items = sectionEntries,
                        key = { it.videoId }
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            musicQueue = musicQueue,
                            onVideoClick = onVideoClick,
                            onMusicClick = onMusicClick,
                            onRemove = onRemove
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: VideoHistoryEntry,
    musicQueue: List<MusicTrack>,
    onVideoClick: (MusicTrack) -> Unit,
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onRemove: (String) -> Unit
) {
    val track = remember(entry) { entry.toMusicTrack() }
    if (entry.isMusic) {
        MusicTrackRow(
            track = track,
            onClick = { onMusicClick(track, musicQueue) },
            trailingContent = {
                IconButton(onClick = { onRemove(entry.videoId) }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.remove_from_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    } else {
        HistoryVideoCard(
            entry = entry,
            onClick = { onVideoClick(track) },
            onDeleteClick = { onRemove(entry.videoId) }
        )
    }
}

@Composable
private fun ShortsHistoryRow(
    entries: List<VideoHistoryEntry>,
    shortVideos: Map<String, Video>,
    onShortClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = entries,
            key = { it.videoId }
        ) { entry ->
            ShortsCard(
                video = shortVideos[entry.videoId] ?: entry.toShortVideo(),
                onClick = { onShortClick(entry.videoId) }
            )
        }
    }
}

@Composable
private fun HistoryVideoCard(
    entry: VideoHistoryEntry,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(156.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (entry.duration > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(entry.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (entry.progressPercentage > 0) {
                LinearProgressIndicator(
                    progress = { if (entry.progressPercentage >= 90f) 1f else entry.progressPercentage / 100f },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = entry.title.ifBlank { entry.videoId },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (entry.channelName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.channelName,
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
private fun EmptyHistoryState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.empty_watch_history),
    body: String = stringResource(R.string.empty_watch_history_body)
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private enum class HistoryContentFilter {
    All,
    Videos,
    Shorts,
    Music;

    fun matches(entry: VideoHistoryEntry): Boolean = when (this) {
        All -> true
        Videos -> !entry.isMusic && !entry.isShort
        Shorts -> !entry.isMusic && entry.isShort
        Music -> entry.isMusic
    }
}

@Composable
private fun HistoryContentFilter.label(): String = when (this) {
    HistoryContentFilter.All -> stringResource(R.string.view_all_button_label)
    HistoryContentFilter.Videos -> stringResource(R.string.history_tab_videos)
    HistoryContentFilter.Shorts -> stringResource(R.string.history_tab_shorts)
    HistoryContentFilter.Music -> stringResource(R.string.nav_music)
}

private enum class HistorySort {
    Newest,
    Oldest
}

@Composable
private fun HistorySort.label(): String = when (this) {
    HistorySort.Newest -> stringResource(R.string.history_sort_newest)
    HistorySort.Oldest -> stringResource(R.string.history_sort_oldest)
}

private fun VideoHistoryEntry.toMusicTrack(): MusicTrack = MusicTrack(
    videoId = videoId,
    title = title,
    artist = channelName,
    thumbnailUrl = thumbnailUrl,
    duration = (duration / 1000).toInt(),
    channelId = channelId
)

private fun VideoHistoryEntry.toShortVideo(): Video = Video(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    duration = (duration / 1000).toInt(),
    viewCount = 0L,
    uploadDate = "",
    timestamp = timestamp,
    isShort = true
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun historyYear(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
}

private fun historySectionKey(timestamp: Long): String {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis.toString()
}

@Composable
private fun sectionTitle(timestamp: Long): String {
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffDays = ((today.timeInMillis - target.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
    return when (diffDays) {
        0 -> stringResource(R.string.time_today)
        1 -> stringResource(R.string.time_yesterday)
        in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(target.time)
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(target.time)
    }
}
