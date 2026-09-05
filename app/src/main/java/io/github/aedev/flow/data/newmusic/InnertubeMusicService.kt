package io.github.aedev.flow.data.newmusic

import io.github.aedev.flow.data.music.model.ArtistDetails
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicCharts
import io.github.aedev.flow.data.music.model.MusicPlaylist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.music.model.RelatedMusic
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.YouTube.SearchFilter
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SearchSuggestions
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.WatchEndpoint
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.innertube.pages.AlbumPage
import io.github.aedev.flow.innertube.pages.ArtistSectionKind
import io.github.aedev.flow.innertube.pages.ChartsPage
import io.github.aedev.flow.innertube.pages.ExplorePage
import io.github.aedev.flow.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hybrid Music Service using Innertube for metadata and discovery.
 * Inspired by Metrolist's implementation.
 */
object InnertubeMusicService {
    // Deliberately NO locale init here: FlowApplication owns YouTube.locale from
    // the app-language + trending-region settings. An init block that reset it to
    // Locale.getDefault() used to clobber the user's chosen region on first music
    // fetch, flooding shelves with device-country content.

    /**
     * Fetch trending music tracks from Innertube's Home/Music page.
     * This returns a list of individual tracks found in the home sections.
     */
    suspend fun fetchTrendingMusic(): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.home()
                result
                    .getOrNull()
                    ?.sections
                    ?.flatMap { it.items }
                    ?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    suspend fun fetchExplore(): ExplorePage? =
        withContext(Dispatchers.IO) {
            try {
                YouTube.explore().getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun fetchMoodAndGenres(): List<io.github.aedev.flow.innertube.pages.MoodAndGenres> =
        withContext(Dispatchers.IO) {
            try {
                YouTube.moodAndGenres().getOrNull() ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Search for songs using Innertube
     */
    suspend fun searchMusic(query: String): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.search(query, SearchFilter.FILTER_SONG)
                result.getOrNull()?.items?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Get search suggestions from Innertube
     */
    suspend fun getSearchSuggestions(query: String): SearchSuggestions? =
        withContext(Dispatchers.IO) {
            try {
                YouTube.searchSuggestions(query).getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Search with summary (Top result + categories)
     */
    suspend fun searchWithSummary(query: String): SearchSummaryPage? =
        withContext(Dispatchers.IO) {
            try {
                YouTube.searchSummary(query).getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Search for playlists using Innertube
     */
    suspend fun searchPlaylists(query: String): List<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.search(query, SearchFilter.FILTER_FEATURED_PLAYLIST)
                result
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<io.github.aedev.flow.innertube.models.PlaylistItem>()
                    ?.map { convertPlaylistToMusicPlaylist(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Fetch new release albums from Innertube
     */
    suspend fun fetchNewReleases(): List<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.newReleaseAlbums()
                result.getOrNull()?.map { convertAlbumToPlaylist(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Fetch playlist details using Innertube
     */
    suspend fun fetchPlaylistDetails(playlistId: String): PlaylistDetails? =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.playlist(playlistId)
                val page = result.getOrNull() ?: return@withContext null

                val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }

                PlaylistDetails(
                    id = page.playlist.id ?: playlistId,
                    title = page.playlist.title,
                    thumbnailUrl = page.playlist.thumbnail ?: "",
                    author = page.playlist.author?.name ?: "",
                    authorId = page.playlist.author?.id,
                    trackCount = tracks.size,
                    description = null,
                    tracks = tracks,
                    continuation = page.songsContinuation ?: page.continuation,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Fetch album details using Innertube
     */
    suspend fun fetchAlbum(albumId: String): PlaylistDetails? =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.album(albumId)
                val page = result.getOrNull() ?: return@withContext null

                val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }

                PlaylistDetails(
                    id = page.album.browseId ?: albumId,
                    title = page.album.title ?: "",
                    thumbnailUrl = page.album.thumbnail ?: "",
                    author = page.album.artists?.joinToString(", ") { it.name } ?: "",
                    authorId =
                        page.album.artists
                            ?.firstOrNull()
                            ?.id,
                    trackCount = tracks.size,
                    description = page.album.year?.toString(),
                    tracks = tracks,
                    continuation = null,
                    durationText = page.durationText,
                    otherVersions = page.otherVersions.map { convertAlbumToPlaylist(it) },
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    suspend fun getRelatedPage(
        videoId: String,
        audioOnly: Boolean = false,
    ): RelatedMusic? =
        withContext(Dispatchers.IO) {
            val nextOutcome = YouTube.next(WatchEndpoint(videoId = videoId))
            val nextResult = nextOutcome.getOrNull()
            if (nextResult == null) {
                android.util.Log.w("InnertubeMusic", "related($videoId): next failed: ${nextOutcome.exceptionOrNull()}")
                return@withContext null
            }
            val relatedEndpoint = nextResult.relatedEndpoint
            if (relatedEndpoint == null) {
                android.util.Log.w("InnertubeMusic", "related($videoId): relatedEndpoint null")
                return@withContext null
            }
            val relatedOutcome = YouTube.related(relatedEndpoint)
            val related = relatedOutcome.getOrNull()
            if (related == null) {
                android.util.Log.w("InnertubeMusic", "related($videoId): related failed: ${relatedOutcome.exceptionOrNull()}")
                return@withContext null
            }
            RelatedMusic(
                tracks =
                    related.songs
                        .filterNot { audioOnly && it.isVideoSong }
                        .mapNotNull { convertToMusicTrack(it) },
                similarArtistIds = related.artists.map { it.id },
                playlists = related.playlists.map { convertPlaylistToMusicPlaylist(it) },
            )
        }

    suspend fun getRelatedMusic(
        videoId: String,
        audioOnly: Boolean = false,
    ): List<MusicTrack> = getRelatedPage(videoId, audioOnly)?.tracks.orEmpty()

    /**
     * Related music with paging. Page 1 is the related browse (the best-quality
     * similar tracks); further pages stream from the track's radio queue via
     * next-continuations — the same primitive endless radio is built on. Results
     * are deduped and the seed track itself is excluded.
     */
    suspend fun getRelatedMusicPaged(
        videoId: String,
        limit: Int,
        maxPages: Int = 3,
        audioOnly: Boolean = false,
    ): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            val collected = LinkedHashMap<String, MusicTrack>()
            getRelatedMusic(videoId, audioOnly).forEach { track ->
                if (track.videoId !in collected) collected[track.videoId] = track
            }
            var pagesFetched = 1

            try {
                var nextResult =
                    if (collected.size < limit && pagesFetched < maxPages) {
                        pagesFetched++
                        YouTube
                            .next(
                                io.github.aedev.flow.innertube.models
                                    .WatchEndpoint(videoId = videoId),
                            ).getOrNull()
                    } else {
                        null
                    }
                while (nextResult != null) {
                    nextResult.items
                        .filterNot { audioOnly && it.isVideoSong }
                        .mapNotNull { convertToMusicTrack(it) }
                        .forEach { track ->
                            if (track.videoId != videoId && track.videoId !in collected) {
                                collected[track.videoId] = track
                            }
                        }
                    val continuation = nextResult.continuation
                    nextResult =
                        if (collected.size < limit && pagesFetched < maxPages && continuation != null) {
                            pagesFetched++
                            YouTube.next(nextResult.endpoint, continuation).getOrNull()
                        } else {
                            null
                        }
                }
            } catch (e: Exception) {
                android.util.Log.w("InnertubeMusic", "relatedPaged($videoId): radio page failed: ${e.message}")
            }
            collected.values.take(limit)
        }

    suspend fun fetchCharts(): MusicCharts? =
        withContext(Dispatchers.IO) {
            val page = YouTube.getChartsPage(YouTube.locale.gl).getOrNull() ?: return@withContext null
            val items = page.sections.flatMap { it.items }
            MusicCharts(
                countryCode = page.countryCode,
                songs =
                    page.sections
                        .filter { it.chartType == ChartsPage.ChartType.SONGS }
                        .flatMap { it.items }
                        .mapNotNull { convertToMusicTrack(it) },
                playlists = items.filterIsInstance<PlaylistItem>().map { convertPlaylistToMusicPlaylist(it) },
                artists = items.filterIsInstance<ArtistItem>().map { convertArtistItemToDetails(it) },
            )
        }

    /**
     * Fetch detailed artist information including albums, singles, videos, etc.
     */
    suspend fun fetchArtistDetails(channelId: String): ArtistDetails? =
        withContext(Dispatchers.IO) {
            val page = YouTube.artist(channelId).getOrNull() ?: return@withContext null
            val artistItem = page.artist

            fun section(kind: ArtistSectionKind) = page.sections.firstOrNull { it.kind == kind }

            fun releases(kind: ArtistSectionKind) =
                section(kind)
                    ?.items
                    .orEmpty()
                    .filterIsInstance<AlbumItem>()
                    .map { convertAlbumToPlaylist(it, artistItem.id, artistItem.title) }

            fun tracks(kind: ArtistSectionKind) =
                section(kind)
                    ?.items
                    .orEmpty()
                    .filterIsInstance<SongItem>()
                    .mapNotNull { convertToMusicTrack(it) }

            val topSongs = section(ArtistSectionKind.TOP_SONGS)
            val albums = section(ArtistSectionKind.ALBUMS)
            val singles = section(ArtistSectionKind.SINGLES)
            ArtistDetails(
                name = artistItem.title,
                channelId = artistItem.id,
                thumbnailUrl = artistItem.thumbnail ?: "",
                subscriberCount = parseViewCount(page.subscriberCountText),
                monthlyListenersText = page.monthlyListenersText,
                description = page.description ?: "",
                topTracks = tracks(ArtistSectionKind.TOP_SONGS),
                albums = releases(ArtistSectionKind.ALBUMS),
                singles = releases(ArtistSectionKind.SINGLES),
                videos = tracks(ArtistSectionKind.VIDEOS),
                relatedArtists =
                    section(ArtistSectionKind.RELATED_ARTISTS)
                        ?.items
                        .orEmpty()
                        .filterIsInstance<ArtistItem>()
                        .map { convertArtistItemToDetails(it) },
                featuredOn =
                    section(ArtistSectionKind.FEATURED_ON)
                        ?.items
                        .orEmpty()
                        .filterIsInstance<PlaylistItem>()
                        .map { convertPlaylistToMusicPlaylist(it) },
                albumsBrowseId = albums?.moreEndpoint?.browseId,
                albumsParams = albums?.moreEndpoint?.params,
                singlesBrowseId = singles?.moreEndpoint?.browseId,
                singlesParams = singles?.moreEndpoint?.params,
                topTracksBrowseId = topSongs?.moreEndpoint?.browseId,
                topTracksParams = topSongs?.moreEndpoint?.params,
            )
        }

    /**
     * Fetch all items (Albums, Singles, etc.) for a specific artist section
     */
    suspend fun fetchArtistItems(
        browseId: String,
        params: String?,
    ): List<MusicPlaylist> =
        withContext(Dispatchers.IO) {
            try {
                val result =
                    YouTube.artistItems(
                        io.github.aedev.flow.innertube.models
                            .BrowseEndpoint(browseId, params),
                    )
                result
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<io.github.aedev.flow.innertube.models.AlbumItem>()
                    ?.map { convertAlbumToPlaylist(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    /**
     * Fetch continuation items for a playlist
     */
    suspend fun fetchPlaylistContinuation(
        playlistId: String,
        continuation: String,
    ): Pair<List<MusicTrack>, String?> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.playlistContinuation(continuation)
                val page = result.getOrNull() ?: return@withContext emptyList<MusicTrack>() to null

                val tracks = page.songs.mapNotNull { convertToMusicTrack(it) }
                tracks to page.continuation
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<MusicTrack>() to null
            }
        }

    /**
     * Fetch lyrics for a song
     */
    suspend fun fetchLyrics(videoId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val nextResult =
                    YouTube
                        .next(
                            io.github.aedev.flow.innertube.models
                                .WatchEndpoint(videoId = videoId),
                        ).getOrNull()
                val lyricsEndpoint = nextResult?.lyricsEndpoint ?: return@withContext null
                YouTube.lyrics(lyricsEndpoint).getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Fetch queue metadata for video IDs or a playlist
     * Uses YouTube.queue() for faster queue loading compared to next()
     */
    suspend fun fetchQueue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): List<MusicTrack> =
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.queue(videoIds, playlistId)
                result.getOrNull()?.mapNotNull { convertToMusicTrack(it) } ?: emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }

    private fun convertAlbumToPlaylist(
        item: io.github.aedev.flow.innertube.models.AlbumItem,
        fallbackArtistId: String? = null,
        fallbackArtistName: String? = null,
    ): MusicPlaylist =
        MusicPlaylist(
            id = item.browseId ?: "",
            title = item.title ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            trackCount = 0, // Not always available in list view
            author = item.year?.toString() ?: "", // Resusing author field for Year/Subtitle
            // The feedback filter needs the artist even though the card shows the year.
            authorId = item.artists?.firstOrNull()?.id ?: fallbackArtistId,
            authorName = item.artists?.firstOrNull()?.name ?: fallbackArtistName,
        )

    private fun convertPlaylistToMusicPlaylist(item: io.github.aedev.flow.innertube.models.PlaylistItem): MusicPlaylist =
        MusicPlaylist(
            id = item.id ?: "",
            title = item.title ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            trackCount = item.songCountText?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            author = item.author?.name ?: "",
            authorId = item.author?.id,
            authorName = item.author?.name,
        )

    private fun convertArtistItemToDetails(item: io.github.aedev.flow.innertube.models.ArtistItem): ArtistDetails =
        ArtistDetails(
            name = item.title ?: "",
            channelId = item.id ?: "",
            thumbnailUrl = item.thumbnail ?: "",
            subscriberCount = 0L,
            topTracks = emptyList(),
        )

    fun convertToMusicTrack(item: YTItem): MusicTrack? =
        when (item) {
            is SongItem -> {
                MusicTrack(
                    videoId = item.id,
                    title = item.title,
                    artist = item.artists.joinToString(", ") { it.name },
                    thumbnailUrl = item.thumbnail,
                    duration = item.duration ?: 0,
                    album = item.album?.name ?: "",
                    channelId = item.artists.firstOrNull()?.id ?: "",
                    isExplicit = item.explicit,
                    albumId = item.album?.id,
                    artists =
                        item.artists.map {
                            MusicArtist(it.name, it.id)
                        },
                    isVideoSong = item.isVideoSong,
                )
            }

            // We can add support for VideoItem or others here if needed
            else -> {
                null
            }
        }

    suspend fun getMediaInfo(videoId: String): io.github.aedev.flow.innertube.models.MediaInfo? =
        withContext(Dispatchers.IO) {
            try {
                YouTube.getMediaInfo(videoId).getOrNull()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    private fun parseViewCount(text: String?): Long {
        if (text == null) return 0
        val cleanText = text.split(" ").firstOrNull() ?: return 0
        return try {
            when {
                cleanText.endsWith("B", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000_000_000).toLong()
                cleanText.endsWith("M", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000_000).toLong()
                cleanText.endsWith("K", ignoreCase = true) -> (cleanText.dropLast(1).toDouble() * 1_000).toLong()
                else -> cleanText.replace(",", "").toLong()
            }
        } catch (e: Exception) {
            0
        }
    }
}
