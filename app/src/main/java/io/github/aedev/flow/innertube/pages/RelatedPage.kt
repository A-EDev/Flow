package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.MusicResponsiveListItemRenderer
import io.github.aedev.flow.innertube.models.MusicTwoRowItemRenderer
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.models.response.BrowseResponse
import io.github.aedev.flow.innertube.models.splitBySeparator
import io.github.aedev.flow.innertube.models.watchPlaylistEndpointFor

enum class RelatedShelfType {
    SIMILAR,
    PLAYLISTS,
    OTHER_PERFORMANCES,
    SIMILAR_ARTISTS,
    MORE_FROM_ARTIST,
    UNKNOWN,
}

data class RelatedShelf(
    val type: RelatedShelfType,
    val title: String,
    val artistBrowseId: String?,
    val items: List<YTItem>,
)

data class RelatedPage(
    val sections: List<RelatedShelf>,
) {
    val songs: List<SongItem>
        get() = itemsOf(RelatedShelfType.SIMILAR).filterIsInstance<SongItem>().filterNot { it.isVideoSong }

    val otherPerformances: List<SongItem>
        get() = itemsOf(RelatedShelfType.OTHER_PERFORMANCES).filterIsInstance<SongItem>()

    val albums: List<AlbumItem>
        get() = itemsOf(RelatedShelfType.MORE_FROM_ARTIST).filterIsInstance<AlbumItem>()

    val artists: List<ArtistItem>
        get() = itemsOf(RelatedShelfType.SIMILAR_ARTISTS).filterIsInstance<ArtistItem>()

    val playlists: List<PlaylistItem>
        get() = itemsOf(RelatedShelfType.PLAYLISTS).filterIsInstance<PlaylistItem>()

    private fun itemsOf(type: RelatedShelfType): List<YTItem> = sections.filter { it.type == type }.flatMap { it.items }

    companion object {
        fun fromBrowseResponse(response: BrowseResponse): RelatedPage {
            val shelves =
                response.contents
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
                    .mapNotNull { content ->
                        val renderer = content.musicCarouselShelfRenderer ?: return@mapNotNull null
                        val titleRun =
                            renderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                        RelatedShelf(
                            type = RelatedShelfType.UNKNOWN,
                            title = titleRun?.text.orEmpty(),
                            artistBrowseId =
                                titleRun
                                    ?.navigationEndpoint
                                    ?.browseEndpoint
                                    ?.browseId
                                    ?.takeIf { it.startsWith("UC") },
                            items =
                                renderer.contents.mapNotNull { item ->
                                    item.musicResponsiveListItemRenderer?.let(::fromMusicResponsiveListItemRenderer)
                                        ?: item.musicTwoRowItemRenderer?.let(::fromMusicTwoRowItemRenderer)
                                },
                        )
                    }
            return RelatedPage(classify(shelves))
        }

        fun classify(shelves: List<RelatedShelf>): List<RelatedShelf> {
            var songShelves = 0
            return shelves.map { shelf ->
                val items = shelf.items
                val type =
                    when {
                        items.isEmpty() -> {
                            RelatedShelfType.UNKNOWN
                        }

                        shelf.artistBrowseId != null -> {
                            RelatedShelfType.MORE_FROM_ARTIST
                        }

                        items.all { it is ArtistItem } -> {
                            RelatedShelfType.SIMILAR_ARTISTS
                        }

                        items.all { it is PlaylistItem } -> {
                            RelatedShelfType.PLAYLISTS
                        }

                        items.all { it is SongItem } -> {
                            val position = songShelves++
                            if (position == 0 && items.none { (it as SongItem).isVideoSong }) {
                                RelatedShelfType.SIMILAR
                            } else {
                                RelatedShelfType.OTHER_PERFORMANCES
                            }
                        }

                        else -> {
                            RelatedShelfType.UNKNOWN
                        }
                    }
                val typedItems =
                    if (type == RelatedShelfType.MORE_FROM_ARTIST) {
                        val shelfArtist = Artist(name = shelf.title, id = shelf.artistBrowseId)
                        items.map { item ->
                            if (item is AlbumItem && item.artists.isNullOrEmpty()) item.copy(artists = listOf(shelfArtist)) else item
                        }
                    } else {
                        items
                    }
                shelf.copy(type = type, items = typedItems)
            }
        }

        fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            val secondaryLine =
                renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.splitBySeparator()
                    .orEmpty()
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
                artists =
                    secondaryLine.firstOrNull()?.oddElements()?.map {
                        Artist(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId,
                        )
                    } ?: return null,
                album =
                    renderer.flexColumns
                        .getOrNull(2)
                        ?.musicResponsiveListItemFlexColumnRenderer
                        ?.text
                        ?.runs
                        ?.firstOrNull()
                        ?.let { run ->
                            run.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId
                                ?.let { Album(name = run.text, id = it) }
                        },
                duration = null,
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit =
                    renderer.badges?.find {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } != null,
                viewCountText =
                    secondaryLine
                        .getOrNull(1)
                        ?.firstOrNull()
                        ?.takeIf { it.navigationEndpoint == null }
                        ?.text,
            )
        }

        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): YTItem? {
            val thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
            val title =
                renderer.title.runs
                    ?.firstOrNull()
                    ?.text ?: return null
            val browseId = renderer.navigationEndpoint.browseEndpoint?.browseId
            return when {
                renderer.isAlbum -> {
                    AlbumItem(
                        browseId = browseId ?: return null,
                        playlistId =
                            renderer.thumbnailOverlay
                                ?.musicItemThumbnailOverlayRenderer
                                ?.content
                                ?.musicPlayButtonRenderer
                                ?.playNavigationEndpoint
                                ?.watchPlaylistEndpoint
                                ?.playlistId ?: return null,
                        title = title,
                        artists =
                            renderer.subtitle
                                ?.runs
                                ?.filter {
                                    it.navigationEndpoint
                                        ?.browseEndpoint
                                        ?.browseId
                                        ?.startsWith("UC") == true
                                }?.map { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) },
                        year =
                            renderer.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail = thumbnail,
                        explicit =
                            renderer.subtitleBadges?.find {
                                it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                            } != null,
                    )
                }

                renderer.isPlaylist -> {
                    PlaylistItem(
                        id = browseId?.removePrefix("VL") ?: return null,
                        title = title,
                        author =
                            renderer.subtitle
                                ?.runs
                                ?.splitBySeparator()
                                ?.getOrNull(1)
                                ?.firstOrNull()
                                ?.let { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) },
                        songCountText = null,
                        thumbnail = thumbnail,
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
                        id = browseId ?: return null,
                        title = title,
                        thumbnail = thumbnail,
                        channelId = browseId,
                        shuffleEndpoint = renderer.menu.watchPlaylistEndpointFor("MUSIC_SHUFFLE"),
                        radioEndpoint = renderer.menu.watchPlaylistEndpointFor("MIX"),
                        subscriberCountText =
                            renderer.subtitle
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                    )
                }

                else -> {
                    null
                }
            }
        }
    }
}
