package io.github.aedev.flow.ui.screens.notes

import android.text.format.DateUtils
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NoteMoments
import io.github.aedev.flow.ui.components.layout.flowContentPadding
import io.github.aedev.flow.ui.components.shared.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.FlowConnectedToggleGroup
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.FlowToggleOption
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.utils.formatDurationMillis

private const val LIST_MOMENTS = 3
private const val PREVIEW_LINES = 2
private val ListThumbnailWidth = 128.dp
private val ChannelAvatarSize = 56.dp

/** Which notes a select mode holds; a long press on a note starts one with that note picked. */
internal class NotesSelection(
    val active: Boolean,
    val keys: Set<String>,
    val onToggle: (Note) -> Unit,
    val onStart: (Note) -> Unit,
)

/** Search and kind above every note; the sort lives in the top bar. */
@Composable
internal fun NotesListPane(
    state: NotesUiState,
    openKey: String?,
    selection: NotesSelection,
    onQueryChange: (String) -> Unit,
    onFilterChange: (NotesFilter) -> Unit,
    onOpen: (Note) -> Unit,
    onPlayMoment: (Note, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FlowSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.notes_search_placeholder),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
            releaseFocusWithKeyboard = true,
        )
        FlowConnectedToggleGroup(
            options =
                listOf(
                    FlowToggleOption(NotesFilter.All, stringResource(R.string.notes_filter_all)),
                    FlowToggleOption(NotesFilter.Videos, stringResource(R.string.notes_filter_videos)),
                    FlowToggleOption(NotesFilter.Channels, stringResource(R.string.notes_filter_channels)),
                ),
            selected = state.filter,
            onSelected = onFilterChange,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        when {
            state.all == null -> {
                FlowLoadingIndicator()
            }

            state.all.isEmpty() -> {
                FlowEmptyState(
                    title = stringResource(R.string.notes_empty_title),
                    subtitle = stringResource(R.string.notes_empty_body),
                    icon = Icons.Outlined.StickyNote2,
                )
            }

            state.visible.isEmpty() -> {
                FlowEmptyState(
                    title = stringResource(R.string.notes_no_match_title),
                    subtitle = stringResource(R.string.notes_no_match_body),
                    icon = Icons.Default.Search,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = flowContentPadding(horizontal = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.visible, key = { it.key }) { note ->
                        NoteListItem(
                            note = note,
                            open = !selection.active && note.key == openKey,
                            selecting = selection.active,
                            picked = note.key in selection.keys,
                            onClick = { if (selection.active) selection.onToggle(note) else onOpen(note) },
                            onLongClick = { if (!selection.active) selection.onStart(note) },
                            onPlayMoment = { positionMs -> onPlayMoment(note, positionMs) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteListItem(
    note: Note,
    open: Boolean,
    selecting: Boolean,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayMoment: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subject = note.subject
    val isVideo = note.kind == NoteKind.Video
    val moments =
        remember(note.text, subject?.durationSeconds) {
            if (isVideo) NoteMoments.timeline(note.text, (subject?.durationSeconds ?: 0) * 1000L).take(LIST_MOMENTS) else emptyList()
        }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (open || picked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = if (isVideo) Alignment.Top else Alignment.CenterVertically,
            ) {
                if (isVideo) {
                    MediaThumbnail(
                        videoId = note.targetId,
                        thumbnailUrl = subject?.thumbnailUrl,
                        width = ListThumbnailWidth,
                        durationSeconds = subject?.durationSeconds,
                    )
                } else {
                    ChannelAvatarImage(
                        url = subject?.thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ChannelAvatarSize)
                                .clip(CircleShape),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = subject?.title ?: stringResource(if (isVideo) R.string.note_unknown_video else R.string.note_channel_note),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val byline = if (isVideo) subject?.channelName else stringResource(R.string.note_channel_note)
                    if (!byline.isNullOrBlank()) {
                        Text(
                            text = byline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = editedLabel(note.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selecting) Checkbox(checked = picked, onCheckedChange = { onClick() })
            }
            if (moments.isNotEmpty() && !selecting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moments.forEach { moment ->
                        val time = formatDurationMillis(moment.positionMs)
                        val description = stringResource(R.string.note_play_from, time)
                        AssistChip(
                            onClick = { onPlayMoment(moment.positionMs) },
                            label = { Text(time) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                            modifier = Modifier.semantics { contentDescription = description },
                        )
                    }
                }
            }
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Edited 2 days ago", in the device language from the platform's own formatter, or "Edited just now". */
@Composable
internal fun editedLabel(epochMs: Long): String {
    val now = System.currentTimeMillis()
    return if (now - epochMs < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.note_edited_just_now)
    } else {
        stringResource(R.string.note_edited, DateUtils.getRelativeTimeSpanString(epochMs, now, DateUtils.MINUTE_IN_MILLIS).toString())
    }
}

internal val NotesSort.labelRes: Int
    get() =
        when (this) {
            NotesSort.Recent -> R.string.notes_sort_recent
            NotesSort.Oldest -> R.string.notes_sort_oldest
            NotesSort.Title -> R.string.notes_sort_title
            NotesSort.Custom -> R.string.notes_sort_custom
        }
