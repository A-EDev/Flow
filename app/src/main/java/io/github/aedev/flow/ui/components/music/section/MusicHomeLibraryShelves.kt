package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction

/** The viewer's own playlists and music subscriptions, and the sections they chose to hide. */
@Immutable
class MusicHomeLibrary(
    val playlists: List<MusicPlaylist> = emptyList(),
    val subscriptions: List<ArtistDetails> = emptyList(),
    val hidden: Set<MusicHomeShelf> = emptySet(),
    val onPlaylistClick: (String) -> Unit = {},
    val onAllPlaylistsClick: () -> Unit = {},
)

internal fun LazyListScope.yourLibrary(
    library: MusicHomeLibrary,
    onArtistClick: (String) -> Unit,
) {
    if (MusicHomeShelf.YOUR_PLAYLISTS !in library.hidden && library.playlists.isNotEmpty()) {
        item(key = "your_playlists") {
            MusicCollectionShelf(
                title = stringResource(R.string.music_home_your_playlists),
                collections = library.playlists,
                keyNamespace = "your_playlists",
                onCollectionClick = { library.onPlaylistClick(it.id) },
                onCollectionMenu = { library.onPlaylistClick(it.id) },
                action = MusicSectionAction.Navigate(library.onAllPlaylistsClick),
                collectionSubtitle = { stringResource(R.string.tracks_count_template, it.trackCount) },
            )
        }
    }
    if (MusicHomeShelf.YOUR_SUBSCRIPTIONS !in library.hidden && library.subscriptions.isNotEmpty()) {
        item(key = "your_subscriptions") {
            MusicArtistShelf(
                title = stringResource(R.string.music_home_your_subscriptions),
                artists = library.subscriptions,
                key = { "your_subscriptions:${it.channelId}" },
                name = { it.name },
                thumbnailUrl = { it.thumbnailUrl },
                onArtistClick = { onArtistClick(it.channelId) },
            )
        }
    }
}
