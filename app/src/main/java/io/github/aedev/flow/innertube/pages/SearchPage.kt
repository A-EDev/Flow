package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.Album
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.MusicResponsiveListItemRenderer
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.Run
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.models.splitBySeparator
import io.github.aedev.flow.innertube.utils.parseTime

data class SearchResult(
    val items: List<YTItem>,
    val continuation: String? = null,
)

object SearchPage {
    fun toYTItem(renderer: MusicResponsiveListItemRenderer): YTItem? {
        val secondaryLine =
            renderer.flexColumns
                .getOrNull(1)
                ?.musicResponsiveListItemFlexColumnRenderer
                ?.text
                ?.runs
                ?.splitBySeparator()
                ?: return null
        return when {
            renderer.isEpisode -> {
                episode(renderer, secondaryLine)
            }

            renderer.isPodcast -> {
                podcast(renderer, secondaryLine)
            }

            renderer.isUserChannel -> {
                profile(renderer)
            }

            renderer.isSong -> {
                SongItem(
                    id = renderer.videoId ?: return null,
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
                        secondaryLine.getOrNull(1)?.firstOrNull()?.takeIf { it.navigationEndpoint?.browseEndpoint != null }?.let {
                            Album(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId!!,
                            )
                        },
                    duration =
                        secondaryLine
                            .lastOrNull()
                            ?.firstOrNull()
                            ?.text
                            ?.parseTime(),
                    musicVideoType = renderer.musicVideoType,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                    viewCountText =
                        secondaryLine
                            .flatten()
                            .find {
                                it.text.endsWith("views", ignoreCase = true) ||
                                    it.text.endsWith("view", ignoreCase = true)
                            }?.text,
                )
            }

            renderer.isArtist -> {
                ArtistItem(
                    id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text
                            ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    shuffleEndpoint =
                        renderer.menuPlaylistEndpoint("MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint =
                        renderer.menuPlaylistEndpoint("MIX") ?: return null,
                )
            }

            renderer.isAlbum -> {
                AlbumItem(
                    browseId = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
                    playlistId =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.anyWatchEndpoint
                            ?.playlistId
                            ?: renderer.menuPlaylistEndpoint("MUSIC_SHUFFLE")?.playlistId
                            ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    artists =
                        secondaryLine.getOrNull(1)?.oddElements()?.map {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        } ?: return null,
                    year =
                        secondaryLine
                            .getOrNull(2)
                            ?.firstOrNull()
                            ?.text
                            ?.toIntOrNull(),
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    explicit =
                        renderer.badges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null,
                )
            }

            renderer.isPlaylist -> {
                PlaylistItem(
                    id =
                        renderer.navigationEndpoint
                            ?.browseEndpoint
                            ?.browseId
                            ?.removePrefix("VL") ?: return null,
                    title =
                        renderer.flexColumns
                            .firstOrNull()
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.firstOrNull()
                            ?.text ?: return null,
                    author =
                        secondaryLine.firstOrNull()?.firstOrNull()?.let {
                            Artist(
                                name = it.text,
                                id = it.navigationEndpoint?.browseEndpoint?.browseId,
                            )
                        } ?: return null,
                    songCountText =
                        renderer.flexColumns
                            .getOrNull(1)
                            ?.musicResponsiveListItemFlexColumnRenderer
                            ?.text
                            ?.runs
                            ?.lastOrNull()
                            ?.text ?: return null,
                    thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                    playEndpoint =
                        renderer.overlay
                            ?.musicItemThumbnailOverlayRenderer
                            ?.content
                            ?.musicPlayButtonRenderer
                            ?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint
                            ?: renderer.menuPlaylistEndpoint("MUSIC_SHUFFLE")
                            ?: return null,
                    shuffleEndpoint =
                        renderer.menuPlaylistEndpoint("MUSIC_SHUFFLE") ?: return null,
                    radioEndpoint =
                        renderer.menuPlaylistEndpoint("MIX"),
                )
            }

            else -> {
                null
            }
        }
    }

    private fun MusicResponsiveListItemRenderer.titleText(): String? =
        flexColumns
            .firstOrNull()
            ?.musicResponsiveListItemFlexColumnRenderer
            ?.text
            ?.runs
            ?.firstOrNull()
            ?.text

    // The linked run of an episode or podcast names its show or its channel; the rest is a date or a label.
    private fun List<List<Run>>.linkedName(): String? = flatten().firstOrNull { it.navigationEndpoint?.browseEndpoint != null }?.text

    /** An episode plays like a song; its show stands in for the artist. */
    private fun episode(
        renderer: MusicResponsiveListItemRenderer,
        secondaryLine: List<List<Run>>,
    ): SongItem? =
        SongItem(
            id = renderer.videoId ?: return null,
            title = renderer.titleText() ?: return null,
            artists = listOfNotNull(secondaryLine.linkedName()?.let { Artist(name = it, id = null) }),
            musicVideoType = renderer.musicVideoType,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
        )

    /** A podcast is a playlist of its episodes: `MPSP` + the playlist id. */
    private fun podcast(
        renderer: MusicResponsiveListItemRenderer,
        secondaryLine: List<List<Run>>,
    ): PlaylistItem? {
        val playEndpoint = renderer.playNavigationEndpoint?.watchPlaylistEndpoint
        return PlaylistItem(
            id =
                playEndpoint?.playlistId
                    ?: renderer.navigationEndpoint
                        ?.browseEndpoint
                        ?.browseId
                        ?.removePrefix("MPSP")
                    ?: return null,
            title = renderer.titleText() ?: return null,
            author = secondaryLine.linkedName()?.let { Artist(name = it, id = null) },
            songCountText = null,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
            playEndpoint = playEndpoint,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
    }

    /** A YouTube channel that is not an artist, which YouTube Music lists as a profile. */
    private fun profile(renderer: MusicResponsiveListItemRenderer): ArtistItem? =
        ArtistItem(
            id = renderer.navigationEndpoint?.browseEndpoint?.browseId ?: return null,
            title = renderer.titleText() ?: return null,
            thumbnail = renderer.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl(),
            shuffleEndpoint = null,
            radioEndpoint = null,
            isProfile = true,
        )
}
