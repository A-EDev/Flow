package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.BrowseEndpoint
import io.github.aedev.flow.innertube.models.PlaylistPanelVideoRenderer
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.WatchEndpoint
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.models.splitBySeparator
import io.github.aedev.flow.innertube.utils.parseTime

data class NextResult(
    val title: String? = null,
    val items: List<SongItem>,
    val currentIndex: Int? = null,
    val lyricsEndpoint: BrowseEndpoint? = null,
    val relatedEndpoint: BrowseEndpoint? = null,
    val continuation: String?,
    val endpoint: WatchEndpoint, // current or continuation next endpoint
)

object NextPage {
    fun fromPlaylistPanelVideoRenderer(renderer: PlaylistPanelVideoRenderer): SongItem? {
        val longByLineRuns = renderer.longBylineText?.runs?.splitBySeparator() ?: return null
        return SongItem(
            id = renderer.videoId ?: return null,
            title =
                renderer.title
                    ?.runs
                    ?.firstOrNull()
                    ?.text ?: return null,
            artists =
                longByLineRuns.firstOrNull()?.oddElements()?.map {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                    )
                } ?: return null,
            album =
                longByLineRuns
                    .getOrNull(1)
                    ?.firstOrNull()
                    ?.takeIf {
                        it.navigationEndpoint?.browseEndpoint != null
                    }?.let {
                        Album(
                            name = it.text,
                            id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                        )
                    },
            duration =
                renderer.lengthText
                    ?.runs
                    ?.firstOrNull()
                    ?.text
                    ?.parseTime() ?: return null,
            musicVideoType = renderer.navigationEndpoint.musicVideoType,
            thumbnail =
                renderer.thumbnail.thumbnails
                    .lastOrNull()
                    ?.url ?: return null,
            explicit =
                renderer.badges?.find {
                    it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                } != null,
            libraryAddToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
            }?.toggleMenuServiceItemRenderer, "LIBRARY_ADD"),
            libraryRemoveToken = PageHelper.extractFeedbackToken(renderer.menu?.menuRenderer?.items?.find {
                it.toggleMenuServiceItemRenderer?.defaultIcon?.iconType?.startsWith("LIBRARY_") == true
            }?.toggleMenuServiceItemRenderer, "LIBRARY_SAVED")
        )
    }
}
