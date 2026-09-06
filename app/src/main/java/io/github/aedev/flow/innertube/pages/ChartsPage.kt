package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.GridRenderer
import io.github.aedev.flow.innertube.models.MusicCarouselShelfRenderer
import io.github.aedev.flow.innertube.models.MusicResponsiveListItemRenderer
import io.github.aedev.flow.innertube.models.MusicShelfRenderer
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.models.getContinuation
import io.github.aedev.flow.innertube.models.response.BrowseResponse
import io.github.aedev.flow.innertube.models.watchPlaylistEndpointFor
import java.net.URLDecoder
import java.util.Base64

data class ChartsPage(
    val sections: List<ChartSection>,
    val countryCode: String?,
    val continuation: String?,
) {
    data class ChartSection(
        val title: String,
        val items: List<YTItem>,
        val chartType: ChartType,
    )

    enum class ChartType {
        SONGS,
        PLAYLISTS,
        ARTISTS,
        NEW_RELEASES,
    }

    companion object {
        private val COUNTRY_MENU_KEY = Regex("country_menu_\\d+([A-Z]{2})")

        fun fromBrowseResponse(
            response: BrowseResponse,
            country: String?,
        ): ChartsPage {
            val contents =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    .orEmpty()
            val supportedCountries =
                contents
                    .flatMap { it.musicShelfRenderer?.let(::formItemKeys).orEmpty() }
                    .mapNotNull(::countryCodeFromFormItemKey)
                    .toSet()
            return ChartsPage(
                sections =
                    contents.mapNotNull { content ->
                        content.musicCarouselShelfRenderer?.let(::carouselSection)
                            ?: content.gridRenderer?.let(::gridSection)
                    },
                countryCode = country?.takeIf { it in supportedCountries },
                continuation =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.getContinuation(),
            )
        }

        fun typeOf(items: List<YTItem>): ChartType =
            when {
                items.all { it is ArtistItem } -> ChartType.ARTISTS
                items.all { it is PlaylistItem } -> ChartType.PLAYLISTS
                else -> ChartType.SONGS
            }

        fun countryCodeFromFormItemKey(key: String): String? =
            runCatching {
                val decoded = Base64.getDecoder().decode(URLDecoder.decode(key, "UTF-8")).toString(Charsets.ISO_8859_1)
                COUNTRY_MENU_KEY.find(decoded)?.groupValues?.get(1)
            }.getOrNull()

        private fun formItemKeys(shelf: MusicShelfRenderer): List<String> =
            shelf.subheaders
                .orEmpty()
                .flatMap { it.musicSideAlignedItemRenderer?.startItems.orEmpty() }
                .flatMap {
                    it.musicSortFilterButtonRenderer
                        ?.menu
                        ?.musicMultiSelectMenuRenderer
                        ?.options
                        .orEmpty()
                }.mapNotNull { it.musicMultiSelectMenuItemRenderer?.formItemEntityKey }

        private fun carouselSection(renderer: MusicCarouselShelfRenderer): ChartSection? {
            val title =
                renderer.header
                    ?.musicCarouselShelfBasicHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null
            val items =
                renderer.contents.mapNotNull { item ->
                    item.musicResponsiveListItemRenderer?.let(::listItem)
                        ?: item.musicTwoRowItemRenderer?.let(HomePage.Section::fromMusicTwoRowItemRenderer)
                }
            if (items.isEmpty()) return null
            return ChartSection(title = title, items = items, chartType = typeOf(items))
        }

        private fun gridSection(renderer: GridRenderer): ChartSection? {
            val title =
                renderer.header
                    ?.gridHeaderRenderer
                    ?.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null
            val items = renderer.items.mapNotNull { it.musicTwoRowItemRenderer?.let(HomePage.Section::fromMusicTwoRowItemRenderer) }
            if (items.isEmpty()) return null
            return ChartSection(title = title, items = items, chartType = ChartType.NEW_RELEASES)
        }

        private fun listItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
            val title =
                renderer.flexColumns
                    .firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.takeIf { it.isNotBlank() } ?: return null
            val thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null
            if (renderer.isArtist) {
                val browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null
                return ArtistItem(
                    id = browseId,
                    title = title,
                    thumbnail = thumbnail,
                    channelId = browseId,
                    shuffleEndpoint = renderer.menu.watchPlaylistEndpointFor("MUSIC_SHUFFLE"),
                    radioEndpoint = renderer.menu.watchPlaylistEndpointFor("MIX"),
                    subscriberCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text,
                )
            }
            val videoId = renderer.playlistItemData?.videoId ?: return null
            val linkedRuns =
                renderer.flexColumns
                    .getOrNull(1)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
                    .orEmpty()
                    .filter { it.navigationEndpoint?.browseEndpoint?.browseId != null }
            val (albumRuns, artistRuns) =
                linkedRuns.partition { run ->
                    val browseId =
                        run.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            .orEmpty()
                    browseId.startsWith("MPRE") || browseId.startsWith("OLAK")
                }
            val rankRuns =
                renderer.flexColumns
                    .getOrNull(2)
                    ?.musicResponsiveListItemFlexColumnRenderer
                    ?.text
                    ?.runs
            return SongItem(
                id = videoId,
                title = title,
                artists = artistRuns.map { Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId) },
                album =
                    albumRuns.firstOrNull()?.let { run ->
                        run.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.let { Album(name = run.text, id = it) }
                    },
                thumbnail = thumbnail,
                musicVideoType = renderer.musicVideoType,
                explicit =
                    renderer.badges?.any {
                        it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                    } == true,
                chartPosition = rankRuns?.firstOrNull()?.text?.toIntOrNull(),
                chartChange = rankRuns?.getOrNull(1)?.text,
            )
        }
    }
}
