package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.MusicResponsiveListItemRenderer
import io.github.aedev.flow.innertube.models.MusicShelfRenderer
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.getItems
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.utils.parseTime

data class HistoryPage(
    val sections: List<HistorySection>?,
) {
    data class HistorySection(
        val title: String,
        val songs: List<SongItem>
    )

    companion object {
        fun fromMusicShelfRenderer(renderer: MusicShelfRenderer): HistorySection {
            return HistorySection(
                title = renderer.title?.runs?.firstOrNull()?.text!!,
                songs = renderer.contents?.getItems()?.mapNotNull {
                    fromMusicResponsiveListItemRenderer(it)
                }!!
            )
        }

        private fun fromMusicResponsiveListItemRenderer(renderer: MusicResponsiveListItemRenderer): SongItem? {
            return SongItem(
                id = renderer.playlistItemData?.videoId ?: return null,
                title = renderer.flexColumns.firstOrNull()
                    ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
                    ?.text ?: return null,
                artists = renderer.flexColumns.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                } ?: emptyList(),
                album = renderer.flexColumns.getOrNull(3)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.let {
                    Album(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId ?: return@let null
                    )
                },
                duration = renderer.fixedColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
                    ?.text?.runs?.firstOrNull()?.text?.parseTime(),
                musicVideoType = renderer.musicVideoType,
                thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                explicit = renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
                endpoint = renderer.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                libraryAddToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                    it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
                }?.toggleMenuServiceItemRenderer, "LIBRARY_ADD"),
                libraryRemoveToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                    it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
                }?.toggleMenuServiceItemRenderer, "LIBRARY_SAVED"),
                historyRemoveToken = renderer.menu?.menuRenderer?.items?.find {
                    it.menuServiceItemRenderer?.icon?.iconType == "REMOVE_FROM_HISTORY"
                }?.menuServiceItemRenderer?.serviceEndpoint?.feedbackEndpoint?.feedbackToken
            )
        }
    }
}
