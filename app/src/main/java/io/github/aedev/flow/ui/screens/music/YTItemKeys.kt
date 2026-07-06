package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem

internal fun YTItem.stableLazyKey(namespace: String): String = when (this) {
    is SongItem -> "$namespace:song:$id"
    is AlbumItem -> "$namespace:album:$id"
    is PlaylistItem -> "$namespace:playlist:$id"
    is ArtistItem -> "$namespace:artist:$id"
}
