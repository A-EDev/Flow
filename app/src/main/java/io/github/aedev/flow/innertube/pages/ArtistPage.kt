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
import io.github.aedev.flow.innertube.models.splitBySeparator
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
    /** The release shelf the section's discography link opens, when it has one. */
    val discographyKind: ArtistSectionKind? = null,
    /** Every item's subtitle names its release type ("Single • 2026"), as the singles shelf does. */
    val showsReleaseType: Boolean = false,
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
                sections =
                    classify(
                        sections = sectionContents.mapNotNull(::fromSectionListRendererContent),
                        artistChannelIds = setOfNotNull(browseId, header?.subscriptionButton?.subscribeButtonRenderer?.channelId),
                    ),
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

        /**
         * Types each section without reading its title, which is localized. [artistChannelIds] tells
         * the artist's own playlists apart from the ones they are featured on.
         */
        fun classify(
            sections: List<ArtistSection>,
            artistChannelIds: Set<String> = emptySet(),
        ): List<ArtistSection> {
            val releaseKinds = releaseKinds(sections)
            var songCarousels = 0
            return sections.mapIndexed { index, section ->
                val items = section.items
                val kind =
                    when {
                        section.kind == ArtistSectionKind.TOP_SONGS -> {
                            ArtistSectionKind.TOP_SONGS
                        }

                        index in releaseKinds -> {
                            releaseKinds.getValue(index)
                        }

                        items.all { it is SongItem } -> {
                            if (songCarousels++ == 0) ArtistSectionKind.VIDEOS else ArtistSectionKind.OTHER
                        }

                        items.all { it is ArtistItem } -> {
                            ArtistSectionKind.RELATED_ARTISTS
                        }

                        items.all { it is PlaylistItem } &&
                            section.moreEndpoint == null &&
                            items.none { (it as PlaylistItem).author?.id?.let(artistChannelIds::contains) == true } -> {
                            ArtistSectionKind.FEATURED_ON
                        }

                        else -> {
                            ArtistSectionKind.OTHER
                        }
                    }
                if (kind == section.kind) section else section.copy(kind = kind)
            }
        }

        /**
         * Albums and Singles & EPs are all-album shelves. A shelf's discography link says which it is,
         * but an artist with only a few releases gets no link, so the rest are taken in page order,
         * where Albums comes first. A lone unlinked shelf goes by its subtitles instead.
         */
        private fun releaseKinds(sections: List<ArtistSection>): Map<Int, ArtistSectionKind> {
            val shelves =
                sections.withIndex().filter { (_, section) ->
                    section.kind != ArtistSectionKind.TOP_SONGS && section.items.isNotEmpty() && section.items.all { it is AlbumItem }
                }
            val kinds = mutableMapOf<Int, ArtistSectionKind>()
            shelves.forEach { (index, section) ->
                section.discographyKind?.takeIf { it !in kinds.values }?.let { kinds[index] = it }
            }
            val open = listOf(ArtistSectionKind.ALBUMS, ArtistSectionKind.SINGLES).filterNot { it in kinds.values }
            val unlinked = shelves.filterNot { it.index in kinds }
            if (unlinked.size == 1 && open.size == 2) {
                val shelf = unlinked.single()
                kinds[shelf.index] = if (shelf.value.showsReleaseType) ArtistSectionKind.SINGLES else ArtistSectionKind.ALBUMS
            } else {
                unlinked.zip(open).forEach { (shelf, kind) -> kinds[shelf.index] = kind }
            }
            return kinds
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
            val header = renderer.header?.musicCarouselShelfBasicHeaderRenderer ?: return null
            val titleRun = header.title.runs?.firstOrNull() ?: return null
            val moreEndpoint =
                header.moreContentButton
                    ?.buttonRenderer
                    ?.navigationEndpoint
                    ?.browseEndpoint
            val discographyEndpoint =
                listOfNotNull(moreEndpoint, titleRun.navigationEndpoint?.browseEndpoint)
                    .firstOrNull { it.browseId.startsWith(ARTIST_RELEASES_BROWSE_PREFIX) }
            val subtitles = renderer.contents.mapNotNull { it.musicTwoRowItemRenderer?.subtitle?.runs }
            return ArtistSection(
                title = titleRun.text,
                items =
                    renderer.contents
                        .mapNotNull { content ->
                            content.musicTwoRowItemRenderer?.let { twoRowRenderer ->
                                fromMusicTwoRowItemRenderer(twoRowRenderer)
                            } ?: content.musicResponsiveListItemRenderer?.let { listItemRenderer ->
                                fromMusicResponsiveListItemRenderer(listItemRenderer)
                            }
                        }.ifEmpty { null } ?: return null,
                moreEndpoint = moreEndpoint,
                discographyKind = ArtistDiscographyParams.releaseKind(discographyEndpoint?.params),
                showsReleaseType = subtitles.isNotEmpty() && subtitles.all { it.splitBySeparator().size > 1 },
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
                            renderer.subtitle
                                ?.runs
                                ?.firstOrNull { it.navigationEndpoint?.browseEndpoint != null }
                                ?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) }
                                ?: Artist(
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
