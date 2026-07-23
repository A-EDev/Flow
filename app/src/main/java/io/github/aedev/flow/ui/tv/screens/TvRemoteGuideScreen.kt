package io.github.aedev.flow.ui.tv.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.focus.rememberTvFocusState
import io.github.aedev.flow.ui.tv.focus.tvFocusScale
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

private data class TvGuideEntry(
    @StringRes val keyRes: Int,
    @StringRes val descriptionRes: Int,
)

private val navigationEntries = listOf(
    TvGuideEntry(R.string.tv_guide_key_dpad, R.string.tv_guide_dpad),
    TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_ok),
    TvGuideEntry(R.string.tv_guide_key_back, R.string.tv_guide_back),
    TvGuideEntry(R.string.tv_guide_key_left_edge, R.string.tv_guide_left_edge),
    TvGuideEntry(R.string.tv_guide_key_rail, R.string.tv_guide_rail),
)

private val searchEntries = listOf(
    TvGuideEntry(R.string.tv_guide_key_dpad, R.string.tv_guide_search_keyboard),
    TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_search_type),
    TvGuideEntry(R.string.tv_guide_key_edit, R.string.tv_guide_search_edit),
    TvGuideEntry(R.string.tv_guide_key_voice, R.string.tv_guide_search_voice),
    TvGuideEntry(R.string.tv_guide_key_right, R.string.tv_guide_search_results),
)

private val playbackEntries = listOf(
    TvGuideEntry(R.string.tv_guide_key_ok, R.string.tv_guide_player_toggle),
    TvGuideEntry(R.string.tv_guide_key_left_right, R.string.tv_guide_player_seek),
    TvGuideEntry(R.string.tv_guide_key_hold, R.string.tv_guide_player_hold),
    TvGuideEntry(R.string.tv_guide_key_up, R.string.tv_guide_player_up),
    TvGuideEntry(R.string.tv_guide_key_down, R.string.tv_guide_player_down),
    TvGuideEntry(R.string.tv_guide_key_media, R.string.tv_guide_player_media),
    TvGuideEntry(R.string.tv_guide_key_back, R.string.tv_guide_player_back),
)

/** A ten-foot reference for every remote interaction supported by the TV interface. */
@Composable
fun TvRemoteGuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalTvDimens.current

    TvScreenScaffold(
        title = stringResource(R.string.tv_remote_guide_title),
        subtitle = stringResource(R.string.tv_remote_guide_subtitle),
        modifier = modifier,
        action = {
            TvButton(
                text = stringResource(R.string.tv_remote_guide_back),
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                onClick = onNavigateBack,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = dimens.overscanHorizontal,
                    end = dimens.overscanHorizontal,
                    bottom = dimens.overscanVertical,
                ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TvGuideSection(
                title = stringResource(R.string.tv_guide_navigation_title),
                entries = navigationEntries,
                modifier = Modifier.weight(1f),
                requestInitialFocus = true,
            )
            TvGuideSection(
                title = stringResource(R.string.tv_guide_search_title),
                entries = searchEntries,
                modifier = Modifier.weight(1f),
            )
            TvGuideSection(
                title = stringResource(R.string.tv_guide_playback_title),
                entries = playbackEntries,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TvGuideSection(
    title: String,
    entries: List<TvGuideEntry>,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = false,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ProvideTvColumnPivot {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusGroup(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = entries,
                        key = { it.descriptionRes },
                    ) { entry ->
                        TvGuideRow(
                            entry = entry,
                            modifier = if (requestInitialFocus && entry === entries.first()) {
                                Modifier.tvInitialFocus()
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvGuideRow(
    entry: TvGuideEntry,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val focused = focusState.isFocused

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusScale(focusState)
            .focusable(),
        shape = MaterialTheme.shapes.medium,
        color = if (focused) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (focused) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        tonalElevation = if (focused) 3.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.widthIn(min = 76.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Text(
                    text = stringResource(entry.keyRes),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Text(
                text = stringResource(entry.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (focused) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
