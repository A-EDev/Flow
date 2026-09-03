package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.CommunityMusicPlaylist
import io.github.aedev.flow.ui.components.music.card.MusicHeroCard
import io.github.aedev.flow.ui.components.music.common.MusicMosaicThumbnail

/**
 * Community playlists as a hero lane: the mosaic of the first four covers carries each item, and
 * the playlist page takes over from there.
 */
@Composable
fun CommunityPlaylistsSection(
    playlists: List<CommunityMusicPlaylist>,
    onPlaylistClick: (CommunityMusicPlaylist) -> Unit,
    modifier: Modifier = Modifier,
    onPlaylistAction: (CommunityMusicPlaylist) -> Unit = {},
) {
    MusicHeroLane(
        title = stringResource(R.string.section_from_the_community),
        items = playlists,
        key = { "community:${it.playlist.id}" },
        modifier = modifier,
    ) { item, captionAlpha ->
        MusicHeroCard(
            title = item.playlist.title,
            subtitle =
                item.playlist.author.ifBlank {
                    item.playlist.trackCount
                        .takeIf { it > 0 }
                        ?.let { stringResource(R.string.tracks_count_template, it) }
                        .orEmpty()
                },
            artwork = {
                MusicMosaicThumbnail(
                    tracks = item.tracks,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            captionAlpha = captionAlpha,
            onClick = { onPlaylistClick(item) },
            onLongClick = { onPlaylistAction(item) },
        )
    }
}
