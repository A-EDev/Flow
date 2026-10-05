package io.github.aedev.flow.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.notes.Note
import io.github.aedev.flow.data.notes.NoteKind
import io.github.aedev.flow.data.notes.NoteMoments
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import io.github.aedev.flow.ui.components.shared.ChannelAvatarImage
import io.github.aedev.flow.ui.components.shared.DurationBadge
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowRowGroup
import io.github.aedev.flow.ui.components.shared.FlowSectionHeader
import io.github.aedev.flow.ui.components.shared.VideoThumbnailImage
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape
import io.github.aedev.flow.ui.components.shared.rememberNoteText
import io.github.aedev.flow.utils.formatDurationMillis

private val TwoColumnMinWidth = 720.dp
private val ChannelAvatarLarge = 96.dp

/** [FlowRowGroup] insets its rows by this much, so everything else in the pane does too. */
private val ContentInset = 12.dp

/**
 * One note, read in full: what it is about, a row per time it mentions, and the text with every
 * time a link. Wide enough, the picture and the text sit side by side.
 */
@Composable
internal fun NoteDetailPane(
    note: Note,
    onPlay: (startPositionMs: Long?) -> Unit,
    onOpenChannel: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideo = note.kind == NoteKind.Video
    val durationMs = (note.subject?.durationSeconds ?: 0) * 1000L
    val moments = remember(note.text, durationMs) { if (isVideo) NoteMoments.timeline(note.text, durationMs) else emptyList() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoColumns = maxWidth >= TwoColumnMinWidth
        val scroll = rememberScrollState()
        val padded =
            Modifier
                .verticalScroll(scroll)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = flowBottomContentPadding())
        if (twoColumns) {
            Row(modifier = padded, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    NoteSubjectHeader(note, onPlay, onOpenChannel, onEdit, onCopy, onDelete)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NoteBody(note, moments, durationMs, onPlay)
                }
            }
        } else {
            Column(modifier = padded.widthIn(max = TwoColumnMinWidth), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NoteSubjectHeader(note, onPlay, onOpenChannel, onEdit, onCopy, onDelete)
                NoteBody(note, moments, durationMs, onPlay)
            }
        }
    }
}

@Composable
private fun NoteSubjectHeader(
    note: Note,
    onPlay: (Long?) -> Unit,
    onOpenChannel: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = ContentInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NoteSubjectHeaderContent(note, onPlay, onOpenChannel, onEdit, onCopy, onDelete)
    }
}

@Composable
private fun NoteSubjectHeaderContent(
    note: Note,
    onPlay: (Long?) -> Unit,
    onOpenChannel: (String) -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val subject = note.subject
    val isVideo = note.kind == NoteKind.Video
    val channelId = if (isVideo) subject?.channelId else note.targetId
    if (isVideo) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onPlay(null) },
        ) {
            VideoThumbnailImage(
                videoId = note.targetId,
                model = subject?.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            subject?.durationSeconds?.takeIf { it > 0 }?.let { seconds ->
                DurationBadge(seconds = seconds, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
    } else {
        ChannelAvatarImage(
            url = subject?.thumbnailUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(ChannelAvatarLarge)
                    .clip(CircleShape),
        )
    }
    Text(
        text = subject?.title ?: stringResource(if (isVideo) R.string.note_unknown_video else R.string.note_channel_note),
        style = MaterialTheme.typography.titleLarge,
    )
    if (isVideo && !subject?.channelName.isNullOrBlank()) {
        Row(
            modifier =
                Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = !channelId.isNullOrBlank()) { channelId?.let(onOpenChannel) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = subject.channelName, style = MaterialTheme.typography.titleSmall)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isVideo) {
            Button(onClick = { onPlay(null) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.resume), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        } else if (!channelId.isNullOrBlank()) {
            Button(onClick = { onOpenChannel(channelId) }) { Text(stringResource(R.string.go_to_channel)) }
        }
        FilledTonalButton(onClick = onEdit) {
            Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.note_edit), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        NoteMenu(onCopy = onCopy, onDelete = onDelete)
    }
}

@Composable
private fun NoteMenu(
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.note_copy_text)) },
                onClick = {
                    open = false
                    onCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.NoteBody(
    note: Note,
    moments: List<io.github.aedev.flow.data.notes.NoteMoment>,
    durationMs: Long,
    onPlay: (Long?) -> Unit,
) {
    if (moments.isNotEmpty()) {
        FlowSectionHeader(stringResource(R.string.note_timestamps))
        FlowRowGroup {
            moments.forEachIndexed { index, moment ->
                val time = formatDurationMillis(moment.positionMs)
                FlowNavRow(
                    title = moment.label.ifBlank { time },
                    onClick = { onPlay(moment.positionMs) },
                    shape = flowRowGroupShape(index, moments.size),
                    leadingContent = { MomentTime(time) },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = stringResource(R.string.note_play_from, time),
                        )
                    },
                )
            }
        }
    }
    FlowSectionHeader(stringResource(R.string.note_title))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ContentInset),
    ) {
        SelectionContainer(modifier = Modifier.padding(16.dp)) {
            Text(
                text =
                    rememberNoteText(
                        text = note.text,
                        linkColor = MaterialTheme.colorScheme.primary,
                        durationMs = durationMs,
                        onTimestampClick = if (note.kind == NoteKind.Video) onPlay else null,
                    ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    Text(
        text = editedLabel(note.updatedAt),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ContentInset + 4.dp),
    )
}

@Composable
private fun MomentTime(time: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
