package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.BrowseEndpoint
import io.github.aedev.flow.innertube.models.MusicCarouselShelfRenderer
import io.github.aedev.flow.innertube.models.MusicResponsiveListItemRenderer
import io.github.aedev.flow.innertube.models.MusicShelfRenderer
import io.github.aedev.flow.innertube.models.MusicTwoRowItemRenderer
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SectionListRenderer
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.models.getItems
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.models.response.BrowseResponse
import io.github.aedev.flow.innertube.models.watchPlaylistEndpointFor

enum class ArtistSectionKind {
    TOP_SONGS,
    ALBUMS,
    SINGLES,
    VIDEOS,
    FEATURED_ON,
    RELATED_ARTISTS,
    OTHER,
}

data class ArtistSection(
    val title: String,
    val items: List<YTItem>,
    val moreEndpoint: BrowseEndpoint?,
    val kind: ArtistSectionKind = ArtistSectionKind.OTHER,
)

data class ArtistPage(
    val artist: ArtistItem,
    val sections: List<ArtistSection>,
    val description: String?,
    val subscriberCountText: String? = null,
    val monthlyListenersText: String? = null,
) {
    companion object {
        private const val ARTIST_RELEASES_BROWSE_PREFIX = "MPAD"

        fun fromBrowseResponse(
            browseId: String,
            response: BrowseResponse,
        ): ArtistPage {
            val header = response.header?.musicImmersiveHeaderRenderer
            val sectionContents =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val firstShelfItem =
                sectionContents
                    .firstOrNull()
                    ?.musicShelfRenderer
                    ?.contents
                    ?.firstOrNull()
                    ?.musicResponsiveListItemRenderer
            return ArtistPage(
                artist =
                    ArtistItem(
                        id = browseId,
                        title =
                            checkNotNull(
                                header
                                    ?.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text
                                    ?: response.header
                                        ?.musicVisualHeaderRenderer
                                        ?.title
                                        ?.runs
                                        ?.firstOrNull()
                                        ?.text
                                    ?: response.header
                                        ?.musicHeaderRenderer
                                        ?.title
                                        ?.runs
                                        ?.firstOrNull()
                                        ?.text,
                            ),
                        thumbnail =
                            header?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                                ?: response.header
                                    ?.musicVisualHeaderRenderer
                                    ?.foregroundThumbnail
                                    ?.musicThumbnailRenderer
                                    ?.getThumbnailUrl()
                                ?: response.header
                                    ?.musicDetailHeaderRenderer
                                    ?.thumbnail
                                    ?.musicThumbnailRenderer
                                    ?.getThumbnailUrl(),
                        channelId = header?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                        playEndpoint =
                            firstShelfItem
                                ?.overlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchEndpoint,
                        shuffleEndpoint =
                            header
                                ?.playButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint
                                ?: firstShelfItem?.navigationEndpoint?.watchPlaylistEndpoint,
                        radioEndpoint =
                            header
                                ?.startRadioButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.watchEndpoint,
                    ),
                sections = classify(sectionContents.mapNotNull(::fromSectionListRendererContent)),
                description =
                    header
                        ?.description
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                subscriberCountText =
                    header
                        ?.subscriptionButton
                        ?.subscribeButtonRenderer
                        ?.subscriberCountText
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                monthlyListenersText =
                    header
                        ?.monthlyListenerCount
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
            )
        }

        fun classify(sections: List<ArtistSection>): List<ArtistSection> {
            var releaseShelves = 0
            var songCarousels = 0
            return sections.map { section ->
                val items = section.items
                val kind =
                    when {
                        section.kind == ArtistSectionKind.TOP_SONGS -> {
                            ArtistSectionKind.TOP_SONGS
                        }

                        items.all { it is AlbumItem } && section.moreEndpoint?.browseId?.startsWith(
                            ARTIST_RELEASES_BROWSE_PREFIX,
                        ) == true -> {
                            if (releaseShelves++ == 0) ArtistSectionKind.ALBUMS else ArtistSectionKind.SINGLES
                        }

                        items.all { it is SongItem } -> {
                            if (songCarousels++ == 0) ArtistSectionKind.VIDEOS else ArtistSectionKind.OTHER
                        }

                        items.all { it is ArtistItem } -> {
                            ArtistSectionKind.RELATED_ARTISTS
                        }

                        items.all { it is PlaylistItem } && section.moreEndpoint == null -> {
                            ArtistSectionKind.FEATURED_ON
                        }

                        else -> {
                            ArtistSectionKind.OTHER
                        }
                    }
                if (kind == section.kind) section else section.copy(kind = kind)
            }
        }

        fun fromSectionListRendererContent(content: SectionListRenderer.Content): ArtistSection? =
            when {
                content.musicShelfRenderer != null -> fromMusicShelfRenderer(content.musicShelfRenderer)
                content.musicCarouselShelfRenderer != null -> fromMusicCarouselShelfRenderer(content.musicCarouselShelfRenderer)
                else -> null
            }

        private fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): ArtistSection? {
            return ArtistSection(
                title =
                    renderer.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: "",
                items =
                    renderer.contents
                        ?.getItems()
                        ?.mapNotNull {
                            fromMusicResponsiveListItemRenderer(it)
                        }?.ifEmpty { null } ?: return null,
                moreEndpoint =
                    renderer.title
                        ?.runs
                        ?.firstOrNull()
                        ?.navigationEndpoint
                        ?.browseEndpoint,
                kind = ArtistSectionKind.TOP_SONGS,
            )
        }

        private fun fromMusicCarouselShelfRenderer(renderer: MusicCarouselShelfRenderer): ArtistSection? {
            return ArtistSection(
                title =
                    renderer.header
                        ?.musicCarouselShelfBasicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                items =
                    renderer.contents
                        .mapNotNull { content ->
                            content.musicTwoRowItemRenderer?.let { twoRowRenderer ->
                                fromMusicTwoRowItemRenderer(twoRowRenderer)
                            } ?: content.musicResponsiveListItemRenderer?.let { listItemRenderer ->
                                fromMusicResponsiveListItemRenderer(listItemRenderer)
                            }
                        }.ifEmpty { null } ?: return null,
                moreEndpoint =
                    renderer.header.musicCarouselShelfBasicHeaderRenderer.moreContentButton
                        ?.buttonRenderer
                        ?.navigationEndpoint
                        ?.browseEndpoint,
            )
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val artists =
                renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.oddElements()
                    ?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    }

            val album =
                renderer.flexColumns
                    .lastOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.let {
                        if (it.navigationEndpoint?.browseEndpoint?.browseId != null) {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint.browseEndpoint.browseId,
                            )
                        } else {
                            null
                        }
                    }

            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title =
                    renderer.flexColumns
                        .firstOrNull()
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.text ?: return null,
                artists = artists ?: return null,
                album = album,
                duration = null,
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                endpoint =
                    renderer.overlay
                        ?.musicItemThumbnailOverlayRenderer
                        ?.content
                        ?.musicPlayButtonRenderer
                        ?.playNavigationEndpoint
                        ?.watchEndpoint,
                viewCountText =
                    renderer.flexColumns
                        .flatMap { it.musicResponsiveListItemFlexColumnRenderer.text?.runs ?: emptyList() }
                        .find { it.text.contains("views", ignoreCase = true) || it.text.contains("plays", ignoreCase = true) }
                        ?.text,
            )
        }

        private fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            return when {
                renderer.isSong -> {
                    SongItem(
                        id = renderer.navigationEndpoint.watchEndpoint?.videoId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists =
                            listOfNotNull(
                                renderer.subtitle?.runs?.firstOrNull()?.let {
                                    Artist(
                                        name = it.text,
                                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                },
                            ),
                        album = null,
                        duration = null,
                        musicVideoType = renderer.musicVideoType,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.isAlbum -> {
                    AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.anyWatchEndpoint
                                ?.playlistId ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        artists = null,
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.isPlaylist -> {
                    PlaylistItem(
                        id =
                            renderer.navigationEndpoint.browseEndpoint
                                ?.browseId
                                ?.removePrefix("VL") ?: return null,
                        title =
                            renderer.title.runs
                                ?.firstOrNull()
                                ?.text ?: return null,
                        author =
                            Artist(
                                name =
                                    renderer.subtitle
                                        ?.runs
                                        ?.lastOrNull()
                                        ?.text ?: return null,
                                id = null,
                            ),
                        songCountText = null,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        playEndpoint =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint ?: return null,
                        shuffleEndpoint = renderer.menu.watchPlaylistEndpointFor("MUSIC_SHUFFLE"),
                        radioEndpoint = renderer.menu.watchPlaylistEndpointFor("MIX"),
                    )
                }

                renderer.isArtist -> {
                    ArtistItem(
                        id = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        title =
                            renderer.title.runs
                                ?.lastOrNull()
                                ?.text ?: return null,
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        channelId =
                            renderer.menu
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType == "SUBSCRIBE"
                                }?.toggleMenuServiceItemRenderer
                                ?.defaultServiceEndpoint
                                ?.subscribeEndpoint
                                ?.channelIds
                                ?.firstOrNull(),
                        shuffleEndpoint = renderer.menu.watchPlaylistEndpointFor("MUSIC_SHUFFLE"),
                        radioEndpoint = renderer.menu.watchPlaylistEndpointFor("MIX"),
                    )
                }

                else -> {
                    null
                }
            }
        }
    }
}
