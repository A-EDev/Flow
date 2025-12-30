package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.Album
import com.flow.youtube.innertube.models.AlbumItem
import com.flow.youtube.innertube.models.Artist
import com.flow.youtube.innertube.models.ArtistItem
import com.flow.youtube.innertube.models.MusicResponsiveListItemRenderer
import com.flow.youtube.innertube.models.MusicTwoRowItemRenderer
import com.flow.youtube.innertube.models.PlaylistItem
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.models.oddElements
import com.flow.youtube.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
