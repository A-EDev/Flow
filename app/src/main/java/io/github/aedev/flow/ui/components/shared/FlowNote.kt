package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.notes.NoteMoments
import io.github.aedev.flow.utils.formatDurationMillis

private const val COLLAPSED_LINES = 3

/**
 * A note the user wrote, shown compactly until they tap it.
 *
 * One component for both the channel page and the description sheet: a note reads the same either
 * way, and two copies would drift.
 */
@Composable
fun FlowNoteCard(
    text: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = contentColorFor(containerColor),
    linkColor: Color = MaterialTheme.colorScheme.primary,
    durationMs: Long = 0L,
    onTimestampClick: ((Long) -> Unit)? = null,
) {
    if (text.isBlank()) return
    val linkedText = rememberNoteText(text, linkColor, durationMs, onTimestampClick)
    var expanded by rememberSaveable(text) { mutableStateOf(false) }

    Surface(
        onClick = { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        modifier =
            modifier
                .fillMaxWidth()
                .animateContentSize(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.StickyNote2,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.note_title),
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = stringResource(R.string.note_edit),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = linkedText,
                    modifier = Modifier.padding(end = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A note's text with every time in it ([NoteMoments]) as a link, in [linkColor] like the times in a
 * description. Without [onTimestampClick] the text stays plain.
 */
@Composable
fun rememberNoteText(
    text: String,
    linkColor: Color,
    durationMs: Long = 0L,
    onTimestampClick: ((Long) -> Unit)?,
): AnnotatedString {
    val currentOnClick by rememberUpdatedState(onTimestampClick)
    val linked = onTimestampClick != null
    return remember(text, linkColor, durationMs, linked) {
        buildAnnotatedString {
            append(text)
            if (!linked) return@buildAnnotatedString
            val styles = TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.SemiBold))
            NoteMoments.find(text, durationMs).forEach { moment ->
                addLink(
                    LinkAnnotation.Clickable(tag = NOTE_TIME_LINK, styles = styles) { currentOnClick?.invoke(moment.positionMs) },
                    start = moment.range.first,
                    end = moment.range.last + 1,
                )
            }
        }
    }
}

private const val NOTE_TIME_LINK = "note-time"

/**
 * Writes or clears one note. Saving empty text deletes it, so there is no separate delete action to
 * reason about. With [insertPositionMs], a chip writes that time in at the cursor.
 */
@Composable
fun FlowNoteEditorDialog(
    initialText: String,
    title: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    insertPositionMs: Long? = null,
) {
    var value by remember(initialText) { mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length))) }
    val fieldFocus = remember { FocusRequester() }

    FlowAlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.StickyNote2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (insertPositionMs != null) {
                    AssistChip(
                        onClick = {
                            val (inserted, cursor) = NoteMoments.insert(value.text, value.selection.start, insertPositionMs)
                            value = TextFieldValue(inserted, TextRange(cursor))
                            fieldFocus.requestFocus()
                        },
                        label = { Text(stringResource(R.string.note_insert_time, formatDurationMillis(insertPositionMs))) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.MoreTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = EditorMinHeight)
                            .focusRequester(fieldFocus),
                    placeholder = { Text(stringResource(R.string.note_placeholder)) },
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(value.text)
                onDismiss()
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private val EditorMinHeight = 140.dp
