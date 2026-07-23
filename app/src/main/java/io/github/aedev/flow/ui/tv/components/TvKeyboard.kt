package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SpaceBar
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

private val KEY_SIZE = 48.dp
private val KEY_GAP = 6.dp
private const val KEYS_PER_ROW = 6

/**
 * D-pad grid keyboard for TV search — the platform IME steals focus and breaks
 * spatial navigation, so text entry stays inside the Compose focus tree.
 */
@Composable
fun TvKeyboard(
    onInput: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
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
            TvKey(onClick = { onInput(' ') }, wide = true) {
                Icon(
                    Icons.Outlined.SpaceBar,
                    contentDescription = stringResource(R.string.tv_keyboard_space),
                )
            }
            TvKey(onClick = onDelete) {
                Icon(
                    Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = stringResource(R.string.tv_keyboard_delete),
                )
            }
            TvKey(onClick = onClear, wide = true) {
                Text(
                    text = stringResource(R.string.tv_keyboard_clear),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            onVoice?.let { voice ->
                TvKey(onClick = voice) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = stringResource(R.string.tv_keyboard_voice),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        shape = MaterialTheme.shapes.small,
        color = if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.inverseOnSurface
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Box(
            modifier = Modifier.size(
                width = if (wide) KEY_SIZE * 2 + KEY_GAP else KEY_SIZE,
                height = KEY_SIZE,
            ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
