package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

/**
 * The Search tab's bar, laid out the way YouTube lays its own out: the back affordance and the mic
 * sit outside the field, and the field itself is a slim pill carrying only the query and its clear
 * button.
 *
 * Back always leaves the screen. There is no collapse state to fall into first — the suggestions
 * list is part of this screen, not a surface stacked on top of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onVoiceSearch: () -> Unit,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = BarHorizontalPadding, vertical = BarVerticalPadding),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputPill(
    textFieldState: TextFieldState,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = PillHeight),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        TextField(
            state = textFieldState,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_videos_channels_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(
                        onClick = { textFieldState.clearText() },
                        modifier = Modifier.size(ClearButtonSize),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            },
            keyboardOptions =
                androidx.compose.foundation.text
                    .KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearch(textFieldState.text.toString()) },
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
        )
    }
}

private val BarHorizontalPadding = 4.dp
private val BarVerticalPadding = 4.dp
private val ItemSpacing = 2.dp
private val PillHeight = 44.dp
private val ClearButtonSize = 36.dp
