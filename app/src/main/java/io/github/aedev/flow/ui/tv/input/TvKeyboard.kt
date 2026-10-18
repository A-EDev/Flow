package io.github.aedev.flow.ui.tv.input

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val KEY_SIZE = 48.dp
private val KEY_GAP = 6.dp
private const val KEYS_PER_ROW = 6

/**
 * D-pad grid keyboard for TV search. The platform keyboard takes focus away from the page and breaks
 * spatial navigation, so Latin letters and digits are typed here; [onSystemKeyboard] hands every
 * other script to the system keyboard.
 */
@Composable
fun TvKeyboard(
    onInput: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    onSystemKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
    onVoice: (() -> Unit)? = null,
) {
    val keys = remember { ('a'..'z') + ('0'..'9') }

    Column(
        modifier = modifier.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(KEY_GAP),
    ) {
        keys.chunked(KEYS_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { key ->
                    TvKey(onClick = { onInput(key) }) {
                        Text(
                            text = key.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            TvKey(onClick = { onInput(' ') }, span = 2) {
                Icon(Icons.Outlined.SpaceBar, contentDescription = stringResource(R.string.tv_keyboard_space))
            }
            TvKey(onClick = onDelete) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.tv_keyboard_delete))
            }
            TvKey(onClick = onClear, span = 2) {
                Text(text = stringResource(R.string.tv_keyboard_clear), style = MaterialTheme.typography.labelLarge)
            }
            onVoice?.let { voice ->
                TvKey(onClick = voice) {
                    Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.tv_keyboard_voice))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            TvKey(onClick = onSystemKeyboard, span = 3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null)
                    Text(text = stringResource(R.string.tv_keyboard_system), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
            TvKey(
                onClick = onSearch,
                span = 3,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text(text = stringResource(R.string.search), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun TvKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    span: Int = 1,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.size(width = KEY_SIZE * span + KEY_GAP * (span - 1), height = KEY_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
