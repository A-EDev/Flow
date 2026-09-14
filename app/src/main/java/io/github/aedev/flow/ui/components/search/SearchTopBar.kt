package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * The Search tab's bar, laid out the way YouTube lays its own out: the back affordance and the mic
 * sit outside the field, and the field itself is a slim pill carrying only the query and its clear
 * button.
 *
 * Back always leaves the screen. There is no collapse state to fall into first — the suggestions
 * list is part of this screen, not a surface stacked on top of it.
 *
 * The shell's scaffold already insets its content for the status bar, so this row adds none.
 */
@Composable
fun SearchTopBar(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onVoiceSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFieldFocused: () -> Unit = {},
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.btn_back),
            )
        }

        SearchInputPill(
            textFieldState = textFieldState,
            onSearch = onSearch,
            focusRequester = focusRequester,
            onFieldFocused = onFieldFocused,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onVoiceSearch) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.voice_search_cd),
            )
        }

        actions?.invoke()
    }
}

/** The filter and layout affordances, shown only once results are on screen. */
@Composable
fun SearchTopBarActions(
    activeFilterCount: Int,
    isGridMode: Boolean,
    onOpenFilters: () -> Unit,
    onToggleGridMode: () -> Unit,
) {
    BadgedBox(
        badge = { if (activeFilterCount > 0) Badge { Text(activeFilterCount.toString()) } },
    ) {
        IconButton(onClick = onOpenFilters) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.search_filters_title),
            )
        }
    }
    IconButton(onClick = onToggleGridMode) {
        Icon(
            imageVector = if (isGridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView,
            contentDescription = stringResource(R.string.search_toggle_view_mode),
        )
    }
}

/**
 * Built on [androidx.compose.foundation.text.BasicTextField] rather than Material's `TextField`
 * because every Material text field carries `TextFieldDefaults.MinHeight` (56 dp) as a floor, and a
 * search pill is half that. Everything else — shape, colours, type — still comes from the theme.
 */
@Composable
private fun SearchInputPill(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    focusRequester: FocusRequester?,
    onFieldFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(PillHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(start = PillStartPadding, end = PillEndPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_videos_channels_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    state = textFieldState,
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
                    onKeyboardAction = { onSearch(textFieldState.text.toString()) },
                )
            }
            if (textFieldState.text.isNotEmpty()) {
                IconButton(onClick = { textFieldState.clearText() }, modifier = Modifier.size(ClearButtonSize)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            }
        }
    }
}

private val BarHorizontalPadding = 4.dp
private val BarVerticalPadding = 4.dp
private val ItemSpacing = 2.dp
private val PillHeight = 40.dp
private val PillStartPadding = 16.dp
private val PillEndPadding = 4.dp
private val ClearButtonSize = 32.dp
