package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

/** Every topic, channel and artist blocked in either engine, each one tap from coming back. */
@Composable
internal fun HiddenContentScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: TasteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hidden = state.hidden
    SettingsPage(title = stringResource(R.string.taste_hidden_title), onBack = onBack, highlight = highlight) {
        if (!state.loading && hidden.count == 0) {
            item("hidden.empty") {
                FlowEmptyState(
                    title = stringResource(R.string.taste_hidden_empty_title),
                    subtitle = stringResource(R.string.taste_hidden_empty_body),
                    icon = Icons.Outlined.VisibilityOff,
                )
            }
            return@SettingsPage
        }
        hiddenGroup(
            "hidden.topics",
            R.string.taste_hidden_topics,
            hidden.topics.map { NamedItem(it, it.readableTopic()) },
            viewModel::unblockTopic,
        )
        hiddenGroup("hidden.channels", R.string.taste_hidden_channels, hidden.channels, viewModel::unblockChannel)
        hiddenGroup("hidden.artists", R.string.taste_hidden_artists, hidden.artists, viewModel::unblockArtist)
    }
}

private fun SettingsListScope.hiddenGroup(
    key: String,
    header: Int,
    items: List<NamedItem>,
    onUnblock: (String) -> Unit,
) {
    group(key = key, header = header) {
        items.forEach { item ->
            row("$key.${item.id}") { shape -> HiddenRow(item.name, shape) { onUnblock(item.id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HiddenRow(
    name: String,
    shape: Shape,
    onUnblock: () -> Unit,
) {
    val description = stringResource(R.string.taste_unblock_item, name)
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        trailingContent = {
            TextButton(onClick = onUnblock, modifier = Modifier.semantics { contentDescription = description }) {
                Text(stringResource(R.string.taste_unblock))
            }
        },
    ) {
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
