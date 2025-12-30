package com.flow.youtube.innertube.pages

import com.flow.youtube.innertube.models.Album
import com.flow.youtube.innertube.models.Artist
import com.flow.youtube.innertube.models.BrowseEndpoint
import com.flow.youtube.innertube.models.PlaylistPanelVideoRenderer
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.models.WatchEndpoint
import com.flow.youtube.innertube.models.oddElements
import com.flow.youtube.innertube.models.splitBySeparator
import com.flow.youtube.innertube.utils.parseTime

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
