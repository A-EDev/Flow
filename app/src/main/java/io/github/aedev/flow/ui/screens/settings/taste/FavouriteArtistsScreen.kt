package io.github.aedev.flow.ui.screens.settings.taste

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.recommendation.music.FavouriteArtist
import io.github.aedev.flow.ui.components.settings.SettingsListScope
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import io.github.aedev.flow.ui.components.shared.MediaArtistToggleRow

private val LoadingHeight = 160.dp

/** Artists picked by hand, which the music recommendations start from before any listening. */
@Composable
internal fun FavouriteArtistsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
    viewModel: FavouriteArtistsViewModel = hiltViewModel(),
) {
    val picker = viewModel.picker
    val state by picker.state.collectAsStateWithLifecycle()
    val query by picker.query.collectAsStateWithLifecycle()
    val pickedIds = state.pickedIds

    SettingsPage(title = stringResource(R.string.favourite_artists_title), onBack = onBack, highlight = highlight) {
        item("artists.search") {
            FlowSearchField(
                query = query,
                onQueryChange = picker::onQueryChange,
                placeholder = stringResource(R.string.favourite_artists_search),
                onClear = { picker.onQueryChange("") },
                releaseFocusWithKeyboard = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        artistGroup("artists.picked", R.string.favourite_artists_picked, state.picked, pickedIds, picker::setFavourite)

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
                    onToggle = picker::setFavourite,
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
                MediaArtistToggleRow(
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                    picked = artist.id in pickedIds,
                    shape = shape,
                    onToggle = { onToggle(artist, it) },
                )
            }
        }
    }
}
