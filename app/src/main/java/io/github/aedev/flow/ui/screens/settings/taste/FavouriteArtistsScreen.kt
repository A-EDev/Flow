package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.music.FavouriteArtist
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.flowArtistShape

private val LoadingHeight = 160.dp

/** Artists picked by hand, which the music recommendations start from before any listening. */
@Composable
internal fun FavouriteArtistsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: FavouriteArtistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val pickedIds = state.picked.mapTo(HashSet(), FavouriteArtist::id)

    SettingsPage(title = stringResource(R.string.favourite_artists_title), onBack = onBack, highlight = highlight) {
        item("artists.search") {
            FlowSearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                placeholder = stringResource(R.string.favourite_artists_search),
                onClear = { viewModel.onQueryChange("") },
                releaseFocusWithKeyboard = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        artistGroup("artists.picked", R.string.favourite_artists_picked, state.picked, pickedIds, viewModel::setFavourite)

        val candidates = state.candidates
        when {
            candidates == null -> {
                item("artists.loading") { FlowLoadingIndicator(Modifier.fillMaxWidth().height(LoadingHeight)) }
            }

            candidates.isEmpty() && state.searching -> {
                item("artists.none") {
                    FlowEmptyState(title = stringResource(R.string.favourite_artists_none), icon = Icons.Outlined.PersonSearch)
                }
            }

            else -> {
                artistGroup(
                    key = "artists.candidates",
                    header = if (state.searching) R.string.favourite_artists_results else R.string.favourite_artists_popular,
                    artists = candidates.filterNot { it.id in pickedIds },
                    pickedIds = pickedIds,
                    onToggle = viewModel::setFavourite,
                )
            }
        }
    }
}

private fun SettingsListScope.artistGroup(
    key: String,
    header: Int,
    artists: List<FavouriteArtist>,
    pickedIds: Set<String>,
    onToggle: (FavouriteArtist, Boolean) -> Unit,
) {
    group(key = key, header = header) {
        artists.forEach { artist ->
            row("$key.${artist.id}") { shape ->
                ArtistToggleRow(artist, picked = artist.id in pickedIds, shape = shape) { onToggle(artist, it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ArtistToggleRow(
    artist: FavouriteArtist,
    picked: Boolean,
    shape: Shape,
    onToggle: (Boolean) -> Unit,
) {
    val description =
        stringResource(if (picked) R.string.favourite_artists_remove else R.string.favourite_artists_add, artist.name)
    SegmentedListItem(
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(shape = shape),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingContent = {
            ArtworkThumbnail(
                thumbnailUrl = artist.thumbnailUrl.ifBlank { null },
                shape = flowArtistShape(),
                placeholder = Icons.Rounded.Person,
            )
        },
        trailingContent = {
            FilledTonalIconToggleButton(
                checked = picked,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Icon(if (picked) Icons.Rounded.Check else Icons.Rounded.Add, contentDescription = null)
            }
        },
    ) {
        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
