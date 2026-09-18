package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import kotlinx.coroutines.flow.drop

/**
 * The app's one search field: a slim pill carrying the query, its clear button and whatever the
 * surface wants beside them.
 *
 * Built on [BasicTextField] rather than `SearchBarDefaults.InputField` because every overload of
 * that API applies `Modifier.sizeIn(minWidth = 360.dp, minHeight = 56.dp, maxWidth = 720.dp)`
 * internally and exposes no height parameter, so the 40 dp pill this app uses is not reachable
 * through it. Everything else — shape, colours, type, motion — still comes from the theme.
 */
@Composable
fun FlowSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    onClear: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFieldFocused: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.height(PillHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    start = if (leadingIcon != null) PillLeadingPadding else PillStartPadding,
                    end = PillEndPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(PillItemSpacing))
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    state = state,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                            .onFocusChanged { if (it.isFocused) onFieldFocused() },
                    textStyle =
                        LocalTextStyle.current.merge(
                            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        ),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { onSearch(state.text.toString()) },
                )
            }

            if (state.text.isNotEmpty()) {
                IconButton(
                    onClick = {
                        state.clearText()
                        onClear?.invoke()
                    },
                    modifier = Modifier.size(ClearButtonSize),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            }

            trailingContent?.invoke(this)
        }
    }
}

/**
 * Overload for surfaces whose query lives in a ViewModel as a [String]. The field still owns a
 * [TextFieldState] internally — the two are kept in step in both directions.
 */
@Composable
fun FlowSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
    onClear: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onFieldFocused: () -> Unit = {},
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val state = remember { TextFieldState(initialText = query) }
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)

    // drop(1) discards snapshotFlow's replay of the text the field was seeded with, so the caller
    // only ever hears about real edits and no surface re-fires its query work on first composition.
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collect { currentOnQueryChange(it) }
    }

    LaunchedEffect(query) {
        if (query != state.text.toString()) state.setTextAndPlaceCursorAtEnd(query)
    }

    FlowSearchField(
        state = state,
        placeholder = placeholder,
        modifier = modifier,
        onSearch = { onSearch() },
        onClear = onClear,
        focusRequester = focusRequester,
        onFieldFocused = onFieldFocused,
        leadingIcon = leadingIcon,
        trailingContent = trailingContent,
    )
}

private val PillHeight = 40.dp
private val PillStartPadding = 16.dp
private val PillLeadingPadding = 12.dp
private val PillEndPadding = 4.dp
private val PillItemSpacing = 8.dp
private val ClearButtonSize = 32.dp
