package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.model.distinctByNonBlankKeyOrSelf
import io.github.aedev.flow.data.recommendation.MusicSection

internal fun MusicUiState.withUniqueLazyContent(): MusicUiState {
    val uniqueDailyDiscover = dailyDiscover.distinctByNonBlankKeyOrSelf {
        it.recommendation.videoId
    }
    val uniqueForYou = forYouTracks.uniqueMusicTracks()
    val uniqueRecommended = recommendedTracks.uniqueMusicTracks()
    val uniqueListenAgain = listenAgain.uniqueMusicTracks()
    val uniqueTrending = trendingSongs.uniqueMusicTracks()
    val uniqueNewReleases = newReleases.uniqueMusicTracks()
    val uniqueMusicVideos = musicVideos.uniqueMusicTracks()
    val uniqueMusicVideosForYou = musicVideosForYou.uniqueMusicTracks()
    val uniqueLivePerformances = livePerformances.uniqueMusicTracks()
    val uniqueLongListens = longListens.uniqueMusicTracks()
    val uniqueHistory = history.uniqueMusicTracks()
    val uniqueAllSongs = allSongs.uniqueMusicTracks()
    val uniqueCommunityPlaylists = communityPlaylists.distinctByNonBlankKeyOrSelf {
        it.playlist.id
    }
    val uniqueFeaturedPlaylists = featuredPlaylists.uniqueMusicPlaylists()
    val uniqueTopAlbums = topAlbums.uniqueMusicPlaylists()
    val uniqueDynamicSections = dynamicSections.uniqueSectionTracks()
    val uniqueSimilarSections = similarToSections.uniqueSectionTracks()
    val uniqueHomeChips = homeChips.distinctByNonBlankKeyOrSelf { it.title }
    val uniqueGenreTracks = genreTracks.mapValuesIfChanged { tracks ->
        tracks.uniqueMusicTracks()
    }
    val uniqueArtistDetails = artistDetails?.withUniqueLazyContent()
    val uniqueSearchArtists = searchResultsArtists.distinctByNonBlankKeyOrSelf {
        it.channelId
    }

    if (
        uniqueDailyDiscover === dailyDiscover &&
        uniqueForYou === forYouTracks &&
        uniqueRecommended === recommendedTracks &&
        uniqueListenAgain === listenAgain &&
        uniqueTrending === trendingSongs &&
        uniqueNewReleases === newReleases &&
        uniqueMusicVideos === musicVideos &&
        uniqueMusicVideosForYou === musicVideosForYou &&
        uniqueLivePerformances === livePerformances &&
        uniqueLongListens === longListens &&
        uniqueHistory === history &&
        uniqueAllSongs === allSongs &&
        uniqueCommunityPlaylists === communityPlaylists &&
        uniqueFeaturedPlaylists === featuredPlaylists &&
        uniqueTopAlbums === topAlbums &&
        uniqueDynamicSections === dynamicSections &&
        uniqueSimilarSections === similarToSections &&
        uniqueHomeChips === homeChips &&
        uniqueGenreTracks === genreTracks &&
        uniqueArtistDetails === artistDetails &&
        uniqueSearchArtists === searchResultsArtists
    ) {
        return this
    }

    return copy(
        dailyDiscover = uniqueDailyDiscover,
        forYouTracks = uniqueForYou,
        recommendedTracks = uniqueRecommended,
        listenAgain = uniqueListenAgain,
        trendingSongs = uniqueTrending,
        newReleases = uniqueNewReleases,
        musicVideos = uniqueMusicVideos,
        musicVideosForYou = uniqueMusicVideosForYou,
        livePerformances = uniqueLivePerformances,
        communityPlaylists = uniqueCommunityPlaylists,
        longListens = uniqueLongListens,
        history = uniqueHistory,
        allSongs = uniqueAllSongs,
        genreTracks = uniqueGenreTracks,
        featuredPlaylists = uniqueFeaturedPlaylists,
        topAlbums = uniqueTopAlbums,
        dynamicSections = uniqueDynamicSections,
        homeChips = uniqueHomeChips,
        artistDetails = uniqueArtistDetails,
        searchResultsArtists = uniqueSearchArtists,
        similarToSections = uniqueSimilarSections
    )
}

private fun List<MusicTrack>.uniqueMusicTracks(): List<MusicTrack> =
    distinctByNonBlankKeyOrSelf(MusicTrack::videoId)

private fun List<MusicPlaylist>.uniqueMusicPlaylists(): List<MusicPlaylist> =
    distinctByNonBlankKeyOrSelf(MusicPlaylist::id)

private fun List<MusicSection>.uniqueSectionTracks(): List<MusicSection> {
    var changed = false
    val normalized = map { section ->
        val uniqueTracks = section.tracks.uniqueMusicTracks()
        if (uniqueTracks === section.tracks) {
            section
        } else {
            changed = true
            section.copy(tracks = uniqueTracks)
        }
    }
    return if (changed) normalized else this
}

private fun ArtistDetails.withUniqueLazyContent(): ArtistDetails {
    val uniqueTopTracks = topTracks.uniqueMusicTracks()
    val uniqueAlbums = albums.uniqueMusicPlaylists()
    val uniqueSingles = singles.uniqueMusicPlaylists()
    val uniqueVideos = videos.uniqueMusicTracks()
    val uniqueRelatedArtists = relatedArtists.distinctByNonBlankKeyOrSelf(ArtistDetails::channelId)
    val uniqueFeaturedOn = featuredOn.uniqueMusicPlaylists()
    return if (
        uniqueTopTracks === topTracks &&
        uniqueAlbums === albums &&
        uniqueSingles === singles &&
        uniqueVideos === videos &&
        uniqueRelatedArtists === relatedArtists &&
        uniqueFeaturedOn === featuredOn
    ) {
        this
    } else {
        copy(
            topTracks = uniqueTopTracks,
            albums = uniqueAlbums,
            singles = uniqueSingles,
            videos = uniqueVideos,
            relatedArtists = uniqueRelatedArtists,
            featuredOn = uniqueFeaturedOn
        )
    }
}

private fun Map<String, List<MusicTrack>>.mapValuesIfChanged(
    transform: (List<MusicTrack>) -> List<MusicTrack>
): Map<String, List<MusicTrack>> {
    var changed = false
    val normalized = mapValues { (_, tracks) ->
        transform(tracks).also { if (it !== tracks) changed = true }
    }
    return if (changed) normalized else this
}
