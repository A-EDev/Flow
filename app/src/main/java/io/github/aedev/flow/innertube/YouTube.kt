package io.github.aedev.flow.innertube

import android.util.Log
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.models.AccountInfo
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.BrowseEndpoint
import io.github.aedev.flow.innertube.models.GridRenderer
import io.github.aedev.flow.innertube.models.MediaInfo
import io.github.aedev.flow.innertube.models.MusicCarouselShelfRenderer
import io.github.aedev.flow.innertube.models.MusicShelfRenderer
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.Run
import io.github.aedev.flow.innertube.models.Runs
import io.github.aedev.flow.innertube.models.SearchSuggestions
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.WatchEndpoint
import io.github.aedev.flow.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.WEB
import io.github.aedev.flow.innertube.models.YouTubeClient.Companion.WEB_REMIX
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.body.BrowseBody
import io.github.aedev.flow.innertube.models.getContinuation
import io.github.aedev.flow.innertube.models.getItems
import io.github.aedev.flow.innertube.models.oddElements
import io.github.aedev.flow.innertube.models.response.AccountMenuResponse
import io.github.aedev.flow.innertube.models.response.BrowseResponse
import io.github.aedev.flow.innertube.models.response.ChannelVideosResponse
import io.github.aedev.flow.innertube.models.response.CreatePlaylistResponse
import io.github.aedev.flow.innertube.models.response.EditPlaylistResponse
import io.github.aedev.flow.innertube.models.response.GetQueueResponse
import io.github.aedev.flow.innertube.models.response.GetSearchSuggestionsResponse
import io.github.aedev.flow.innertube.models.response.GetTranscriptResponse
import io.github.aedev.flow.innertube.models.response.ImageUploadResponse
import io.github.aedev.flow.innertube.models.response.NextResponse
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.models.response.SearchResponse
import io.github.aedev.flow.innertube.models.response.channelVideoCountText
import io.github.aedev.flow.innertube.pages.AlbumPage
import io.github.aedev.flow.innertube.pages.ArtistItemsContinuationPage
import io.github.aedev.flow.innertube.pages.ArtistItemsPage
import io.github.aedev.flow.innertube.pages.ArtistPage
import io.github.aedev.flow.innertube.pages.BrowseResult
import io.github.aedev.flow.innertube.pages.ChannelShortsPage
import io.github.aedev.flow.innertube.pages.ChannelSortOption
import io.github.aedev.flow.innertube.pages.ChartsPage
import io.github.aedev.flow.innertube.pages.CommunityCommentsPage
import io.github.aedev.flow.innertube.pages.CommunityPostsPage
import io.github.aedev.flow.innertube.pages.ExplorePage
import io.github.aedev.flow.innertube.pages.HistoryPage
import io.github.aedev.flow.innertube.pages.HomePage
import io.github.aedev.flow.innertube.pages.LibraryContinuationPage
import io.github.aedev.flow.innertube.pages.LibraryPage
import io.github.aedev.flow.innertube.pages.MoodAndGenres
import io.github.aedev.flow.innertube.pages.NewReleaseAlbumPage
import io.github.aedev.flow.innertube.pages.NextPage
import io.github.aedev.flow.innertube.pages.NextResult
import io.github.aedev.flow.innertube.pages.PlaylistContinuationPage
import io.github.aedev.flow.innertube.pages.PlaylistPage
import io.github.aedev.flow.innertube.pages.RelatedPage
import io.github.aedev.flow.innertube.pages.SearchPage
import io.github.aedev.flow.innertube.pages.SearchResult
import io.github.aedev.flow.innertube.pages.SearchShortItem
import io.github.aedev.flow.innertube.pages.SearchSuggestionPage
import io.github.aedev.flow.innertube.pages.SearchSummary
import io.github.aedev.flow.innertube.pages.SearchSummaryPage
import io.github.aedev.flow.innertube.pages.SearchVideosPage
import io.github.aedev.flow.innertube.pages.ShortsPage
import io.github.aedev.flow.innertube.pages.channelSortOptions
import io.github.aedev.flow.innertube.pages.toChannelShortsPage
import io.github.aedev.flow.innertube.pages.toCommunityCommentsPage
import io.github.aedev.flow.innertube.pages.toCommunityPostsPage
import io.github.aedev.flow.innertube.pages.toSearchShorts
import io.github.aedev.flow.innertube.pages.toSearchVideosPage
import io.github.aedev.flow.innertube.pages.toShortsPage
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.net.Proxy
import java.util.Locale
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()
    private const val CHANNEL_VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
    private const val CHANNEL_LIVE_PARAMS = "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"
    private const val CHANNEL_POSTS_PARAMS = "EgVwb3N0c_IGBAoCSgA="
    private const val CHANNEL_SHORTS_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA=="

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String?
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var dataSyncId: String?
        get() = innerTube.dataSyncId
        set(value) {
            innerTube.dataSyncId = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }

    var proxyAuth: String?
        get() = innerTube.proxyAuth
        set(value) {
            innerTube.proxyAuth = value
        }
    var cacheDirectory: java.io.File?
        get() = innerTube.cacheDirectory
        set(value) {
            innerTube.cacheDirectory = value
        }
    var useLoginForBrowse: Boolean
        get() = innerTube.useLoginForBrowse
        set(value) {
            innerTube.useLoginForBrowse = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> =
        runCatching {
            val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
            SearchSuggestions(
                queries =
                    response.contents
                        ?.getOrNull(0)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            content.searchSuggestionRenderer
                                ?.suggestion
                                ?.runs
                                ?.joinToString(separator = "") { it.text }
                        }.orEmpty(),
                recommendedItems =
                    response.contents
                        ?.getOrNull(1)
                        ?.searchSuggestionsSectionRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicResponsiveListItemRenderer?.let { renderer ->
                                SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                            }
                        }.orEmpty(),
            )
        }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
            SearchSummaryPage(
                summaries =
                    response.contents
                        ?.tabbedSearchResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull { it ->
                            if (it.musicCardShelfRenderer != null) {
                                SearchSummary(
                                    title =
                                        it.musicCardShelfRenderer.header
                                            ?.musicCardShelfHeaderBasicRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text ?: YouTubeConstants.DEFAULT_TOP_RESULT,
                                    items =
                                        listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(it.musicCardShelfRenderer))
                                            .plus(
                                                it.musicCardShelfRenderer.contents
                                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                                    ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                                                    .orEmpty(),
                                            ).distinctBy { it.id }
                                            .ifEmpty { null } ?: return@mapNotNull null,
                                )
                            } else {
                                SearchSummary(
                                    title =
                                        it.musicShelfRenderer
                                            ?.title
                                            ?.runs
                                            ?.firstOrNull()
                                            ?.text ?: YouTubeConstants.DEFAULT_OTHER_RESULTS,
                                    items =
                                        it.musicShelfRenderer
                                            ?.contents
                                            ?.getItems()
                                            ?.mapNotNull {
                                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(it)
                                            }?.distinctBy { it.id }
                                            ?.ifEmpty { null } ?: return@mapNotNull null,
                                )
                            }
                        }!!,
                continuation =
                    response.contents
                        ?.tabbedSearchResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.lastOrNull()
                        ?.musicShelfRenderer
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    suspend fun search(
        query: String,
        filter: SearchFilter,
    ): Result<SearchResult> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
            SearchResult(
                items =
                    response.contents
                        ?.tabbedSearchResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.lastOrNull()
                        ?.musicShelfRenderer
                        ?.contents
                        ?.getItems()
                        ?.mapNotNull {
                            SearchPage.toYTItem(it)
                        }.orEmpty(),
                continuation =
                    response.contents
                        ?.tabbedSearchResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.lastOrNull()
                        ?.musicShelfRenderer
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    // Long-form search ignores the Shorts shelf; fetch it from the main site (not music).
    suspend fun searchShorts(query: String): Result<List<SearchShortItem>> =
        runCatching {
            innerTube.webSearch(currentWebClient(), query).body<JsonObject>().toSearchShorts()
        }.onSuccess { Log.d("SearchShorts", "query='$query' shorts=${it.size}") }
            .onFailure { Log.w("SearchShorts", "query='$query' failed: ${it.message}") }

    suspend fun searchByViews(
        query: String,
        searchParams: String,
        continuation: String? = null,
    ): Result<SearchVideosPage> =
        runCatching {
            ensureVisitorData()
            val searchClient = currentWebClient()
            innerTube
                .webSearch(
                    client = searchClient,
                    query = query.takeIf { continuation == null },
                    params = searchParams.takeIf { continuation == null },
                    continuation = continuation,
                    anonymous = true,
                    includeVisitorData = true,
                ).body<JsonObject>()
                .toSearchVideosPage()
        }

    private suspend fun ensureVisitorData() {
        if (!visitorData.isNullOrBlank()) return
        visitorData()
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?.let { visitorData = it }
    }

    private suspend fun currentWebClient(): YouTubeClient =
        withContext(Dispatchers.IO) {
            WEB.copy(
                clientVersion =
                    runCatching { YoutubeParsingHelper.getClientVersion() }
                        .getOrDefault(WEB.clientVersion),
            )
        }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> =
        runCatching {
            val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
            SearchResult(
                items =
                    response.continuationContents
                        ?.musicShelfContinuation
                        ?.contents
                        ?.mapNotNull {
                            SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                        }!!,
                continuation =
                    response.continuationContents
                        ?.musicShelfContinuation
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    /**
     * Main YouTube search exposes collaboration avatars in a modern entity block:
     * searchVideoResultEntityKey + avatar.avatarStackViewModel. NewPipe only returns
     * the uploader avatar, so callers can merge this lightweight map by video id.
     */
    suspend fun searchVideoAvatarStacks(query: String): Result<Map<String, List<String>>> =
        runCatching {
            val rawBody = innerTube.webSearch(WEB, query).bodyAsText()
            val root =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }.parseToJsonElement(rawBody)
            buildMap {
                collectSearchVideoAvatarStacks(root, this)
            }
        }

    suspend fun videoAvatarStack(videoId: String): Result<List<String>> =
        runCatching {
            val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
            val root =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }.parseToJsonElement(rawBody)
            root.findVideoOwnerAvatarStackUrls()
        }

    suspend fun videoCollaborators(videoId: String): Result<List<VideoCollaborator>> =
        runCatching {
            val rawBody = innerTube.next(WEB, videoId, null, null, null, null, null).bodyAsText()
            val root =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }.parseToJsonElement(rawBody)
            root.findVideoOwnerCollaborators()
        }

    private fun collectSearchVideoAvatarStacks(
        element: JsonElement,
        result: MutableMap<String, List<String>>,
    ) {
        when (element) {
            is JsonArray -> {
                element.forEach { collectSearchVideoAvatarStacks(it, result) }
            }

            is JsonObject -> {
                if (element.containsKey("searchVideoResultEntityKey") && element.containsKey("avatar")) {
                    val videoId = element.findFirstString("videoId")
                    val avatarUrls =
                        element["avatar"]
                            ?.collectAvatarImageUrls()
                            .orEmpty()

                    if (!videoId.isNullOrBlank() && avatarUrls.isNotEmpty()) {
                        result[videoId] = avatarUrls
                    }
                }
                element.values.forEach { collectSearchVideoAvatarStacks(it, result) }
            }

            else -> {
                Unit
            }
        }
    }

    private fun JsonElement.findFirstString(key: String): String? =
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonPrimitive)?.contentOrNull
                    ?: values.firstNotNullOfOrNull { it.findFirstString(key) }
            }

            is JsonArray -> {
                firstNotNullOfOrNull { it.findFirstString(key) }
            }

            else -> {
                null
            }
        }

    private fun JsonElement.findDirectOrNestedString(key: String): String? =
        when (this) {
            is JsonObject -> {
                (this[key] as? JsonPrimitive)?.contentOrNull
                    ?: values.firstNotNullOfOrNull { it.findDirectOrNestedString(key) }
            }

            is JsonArray -> {
                firstNotNullOfOrNull { it.findDirectOrNestedString(key) }
            }

            else -> {
                null
            }
        }

    private fun JsonElement.collectChannelBrowseIds(): List<String> {
        val ids = mutableListOf<String>()

        fun collect(element: JsonElement) {
            when (element) {
                is JsonArray -> {
                    element.forEach(::collect)
                }

                is JsonObject -> {
                    val browseId = (element["browseId"] as? JsonPrimitive)?.contentOrNull
                    if (!browseId.isNullOrBlank() && browseId.startsWith("UC")) {
                        ids += browseId
                    }
                    val channelId = (element["channelId"] as? JsonPrimitive)?.contentOrNull
                    if (!channelId.isNullOrBlank() && channelId.startsWith("UC")) {
                        ids += channelId
                    }
                    element.values.forEach(::collect)
                }

                else -> {
                    Unit
                }
            }
        }

        collect(this)
        return ids.distinct()
    }

    private fun JsonElement.collectAvatarImageUrls(): List<String> {
        val urls = mutableListOf<String>()

        fun collect(element: JsonElement) {
            when (element) {
                is JsonArray -> {
                    element.forEach(::collect)
                }

                is JsonObject -> {
                    val url = (element["url"] as? JsonPrimitive)?.contentOrNull
                    if (!url.isNullOrBlank() && url.contains("yt3.ggpht.com")) {
                        urls += url
                    }
                    element.values.forEach(::collect)
                }

                else -> {
                    Unit
                }
            }
        }

        collect(this)
        return urls
            .distinctBy { it.avatarImageIdentityKey() }
            .take(5)
    }

    private fun JsonElement.findVideoOwnerAvatarStackUrls(): List<String> =
        when (this) {
            is JsonObject -> {
                val owner = this["videoOwnerRenderer"] as? JsonObject
                val ownerStack =
                    owner
                        ?.get("avatarStack")
                        ?.collectAvatarImageUrls()
                        .orEmpty()
                        .take(2)
                if (ownerStack.size > 1) {
                    ownerStack
                } else {
                    values
                        .firstNotNullOfOrNull { child ->
                            child.findVideoOwnerAvatarStackUrls().takeIf { it.size > 1 }
                        }.orEmpty()
                }
            }

            is JsonArray -> {
                firstNotNullOfOrNull { child ->
                    child.findVideoOwnerAvatarStackUrls().takeIf { it.size > 1 }
                }.orEmpty()
            }

            else -> {
                emptyList()
            }
        }

    private fun JsonElement.findVideoOwnerCollaborators(): List<VideoCollaborator> =
        when (this) {
            is JsonObject -> {
                val owner = this["videoOwnerRenderer"] as? JsonObject
                val collaborators = owner?.extractCollaboratorDialogRows().orEmpty()
                if (collaborators.size > 1) {
                    collaborators
                } else {
                    values
                        .firstNotNullOfOrNull { child ->
                            child.findVideoOwnerCollaborators().takeIf { it.size > 1 }
                        }.orEmpty()
                }
            }

            is JsonArray -> {
                firstNotNullOfOrNull { child ->
                    child.findVideoOwnerCollaborators().takeIf { it.size > 1 }
                }.orEmpty()
            }

            else -> {
                emptyList()
            }
        }

    private fun JsonObject.extractCollaboratorDialogRows(): List<VideoCollaborator> {
        val listItems =
            getPath(
                "navigationEndpoint",
                "showDialogCommand",
                "panelLoadingStrategy",
                "inlineContent",
                "dialogViewModel",
                "customContent",
                "listViewModel",
                "listItems",
            ) as? JsonArray ?: return emptyList()

        return listItems
            .mapNotNull { item ->
                ((item as? JsonObject)?.get("listItemViewModel") as? JsonObject)
                    ?.toVideoCollaborator()
            }.filter { it.name.isNotBlank() }
            .distinctBy { it.channelId.ifBlank { it.name.lowercase(Locale.US) } }
            .take(5)
    }

    private fun JsonObject.toVideoCollaborator(): VideoCollaborator? {
        val channelId = collectChannelBrowseIds().firstOrNull().orEmpty()
        val avatarUrl = collectAvatarImageUrls().firstOrNull().orEmpty()
        val title = (getPath("title", "content") as? JsonPrimitive)?.contentOrNull
        val subtitle = (getPath("subtitle", "content") as? JsonPrimitive)?.contentOrNull
        val label =
            ((getPath("rendererContext", "accessibilityContext", "label") as? JsonPrimitive)?.contentOrNull)
                ?: findDirectOrNestedString("label")
        val parsedName =
            title
                ?: label
                    ?.substringBefore(". Go to channel")
                    ?.substringBefore(" Go to channel")
                    ?.substringBefore(" - ")
                    ?.substringBefore(" • ")
                    ?.substringBefore(" subscribers")
                    ?.substringBefore(" subscriber")
                    ?.substringBefore(", ")
                    ?.takeIf { it.isNotBlank() }
        val subscriberText =
            label
                ?.substringAfter(" - ", missingDelimiterValue = "")
                ?.substringBefore(". Go to channel")
                ?.takeIf { it.contains("subscriber", ignoreCase = true) }
                ?: subtitle
                    ?.substringAfter("•", missingDelimiterValue = "")
                    ?.takeIf { it.contains("subscriber", ignoreCase = true) }
                    .orEmpty()
                    .cleanYouTubeDecoratedText()

        val content =
            findDirectOrNestedString("content")
                ?.takeIf { !it.contains("@") && !it.contains("subscriber", ignoreCase = true) }
        val name = parsedName ?: content ?: return null
        if (name.isSubscriptionOptionLabel()) return null

        val hasChannelMetadata = channelId.startsWith("UC") && avatarUrl.isNotBlank()
        if (!hasChannelMetadata) return null

        return VideoCollaborator(
            name = name.cleanYouTubeDecoratedText(),
            channelId = channelId,
            thumbnailUrl = avatarUrl,
            subscriberCountText = subscriberText,
        )
    }

    private fun JsonObject.getPath(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { current, key ->
            (current as? JsonObject)?.get(key)
        }

    private fun String.cleanYouTubeDecoratedText(): String =
        replace("\u200E", "")
            .replace("\u2068", "")
            .replace("\u2069", "")
            .trim()

    private fun String.isSubscriptionOptionLabel(): Boolean =
        trim().lowercase(Locale.US) in
            setOf(
                "personalized",
                "all",
                "none",
                "unsubscribe",
                "subscribed",
                "subscribe",
            )

    // ── Channel-scoped video search (YouTube.com WEB API) ─────────────────────

    data class ChannelVideoSearchResult(
        val videos: List<io.github.aedev.flow.data.model.Video>,
        val continuation: String?,
        val channelVideoCountText: String? = null,
        /** The tab's Latest/Popular/Oldest bar. Empty on a continuation, which carries no header. */
        val sorts: List<ChannelSortOption> = emptyList(),
    )

    /**
     * Search for videos within [channelId] matching [query].
     * Uses the YouTube.com WEB Innertube endpoint with a channel-scoped params filter.
     */
    suspend fun channelSearch(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        query: String,
    ): Result<ChannelVideoSearchResult> =
        runCatching {
            val httpResponse = innerTube.channelSearch(currentWebClient(), channelId, query)
            val rawBody = httpResponse.bodyAsText()
            val lenientJson =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            val response = lenientJson.decodeFromString<io.github.aedev.flow.innertube.models.response.ChannelSearchResponse>(rawBody)
            parseChannelSearchResponse(response, channelId, channelName, channelThumbnailUrl)
        }

    suspend fun channelSearchContinuation(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        continuation: String,
    ): Result<ChannelVideoSearchResult> =
        runCatching {
            val httpResponse =
                innerTube.channelSearch(
                    currentWebClient(),
                    channelId,
                    query = "",
                    continuation = continuation,
                )
            val rawBody = httpResponse.bodyAsText()
            val lenientJson =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            val response = lenientJson.decodeFromString<io.github.aedev.flow.innertube.models.response.ChannelSearchResponse>(rawBody)

            val videos = mutableListOf<io.github.aedev.flow.data.model.Video>()
            var nextContinuation: String? = null

            val appendedItems =
                response.onResponseReceivedActions
                    ?.firstOrNull { it.appendContinuationItemsAction != null }
                    ?.appendContinuationItemsAction
                    ?.continuationItems
                    .orEmpty()
            if (appendedItems.isNotEmpty()) {
                appendedItems.forEach { richItem ->
                    richItem.richItemRenderer
                        ?.content
                        ?.videoRenderer
                        ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                        ?.let { videos.add(it) }
                    richItem.itemSectionRenderer?.contents?.forEach { sectionItem ->
                        sectionItem.videoRenderer
                            ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                            ?.let { videos.add(it) }
                    }
                    richItem.continuationItemRenderer
                        ?.continuationEndpoint
                        ?.continuationCommand
                        ?.token
                        ?.let { nextContinuation = it }
                }
            }

            if (videos.isEmpty()) {
                val sectionContents =
                    response.continuationContents
                        ?.sectionListContinuation
                        ?.contents
                        .orEmpty()
                sectionContents
                    .mapNotNull { it.itemSectionRenderer?.contents }
                    .flatten()
                    .mapNotNull { it.videoRenderer }
                    .mapNotNull { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                    .forEach { videos.add(it) }
                if (nextContinuation == null) {
                    nextContinuation = response.continuationContents
                        ?.sectionListContinuation
                        ?.continuations
                        ?.firstOrNull()
                        ?.nextContinuationData
                        ?.continuation
                        ?: sectionContents
                            .mapNotNull { it.continuationItemRenderer }
                            .firstOrNull()
                            ?.continuationEndpoint
                            ?.continuationCommand
                            ?.token
                }
            }

            if (videos.isEmpty()) {
                response.continuationContents?.richGridContinuation?.contents?.forEach { richItem ->
                    richItem.richItemRenderer
                        ?.content
                        ?.videoRenderer
                        ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                        ?.let { videos.add(it) }
                    richItem.continuationItemRenderer
                        ?.continuationEndpoint
                        ?.continuationCommand
                        ?.token
                        ?.let { nextContinuation = it }
                }
            }

            ChannelVideoSearchResult(videos = videos, continuation = nextContinuation)
        }

    suspend fun channelVideos(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<ChannelVideoSearchResult> = channelVideosPage(channelId, channelName, channelThumbnailUrl, CHANNEL_VIDEOS_PARAMS, false)

    suspend fun channelVideosContinuation(
        continuation: String,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<ChannelVideoSearchResult> = channelVideosPage(channelId, channelName, channelThumbnailUrl, null, false, continuation)

    suspend fun channelLiveStreams(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<ChannelVideoSearchResult> = channelVideosPage(channelId, channelName, channelThumbnailUrl, CHANNEL_LIVE_PARAMS, true)

    suspend fun channelLiveStreamsContinuation(
        continuation: String,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<ChannelVideoSearchResult> = channelVideosPage(channelId, channelName, channelThumbnailUrl, null, true, continuation)

    /**
     * A channel's Shorts tab, including its sort bar. Unlike the Videos and Live tabs this goes
     * through the native client rather than NewPipe, because NewPipe's `ChannelTabInfo` has no
     * notion of the Latest/Popular/Oldest chips and the tab is useless without them (#547).
     */
    suspend fun channelShorts(channelId: String): Result<ChannelShortsPage> = channelShortsPage(channelId = channelId)

    /** Serves both paging and sort switching — a sort chip's token is just another continuation. */
    suspend fun channelShortsContinuation(continuation: String): Result<ChannelShortsPage> = channelShortsPage(continuation = continuation)

    private suspend fun channelShortsPage(
        channelId: String? = null,
        continuation: String? = null,
    ): Result<ChannelShortsPage> =
        runCatching {
            val client = currentWebClient()
            val response =
                innerTube.channelBrowse(
                    client = client,
                    channelId = channelId,
                    params = CHANNEL_SHORTS_PARAMS.takeIf { continuation == null },
                    continuation = continuation,
                )
            Json
                .parseToJsonElement(response.bodyAsText())
                .jsonObject
                .toChannelShortsPage()
        }

    suspend fun communityPosts(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<CommunityPostsPage> = communityPostsPage(channelId, channelName, channelThumbnailUrl)

    suspend fun communityPostsContinuation(
        continuation: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): Result<CommunityPostsPage> =
        communityPostsPage(
            channelId = "",
            channelName = channelName,
            channelThumbnailUrl = channelThumbnailUrl,
            continuation = continuation,
        )

    suspend fun communityPostComments(
        postId: String,
        params: String?,
    ): Result<CommunityCommentsPage> =
        runCatching {
            val initial = communityPostCommentsPage(postId = postId, params = params)
            if (initial.comments.isNotEmpty() || initial.continuation == null) {
                initial
            } else {
                val commentsPage = communityPostCommentsPage(continuation = initial.continuation)
                commentsPage.copy(
                    commentCountText = commentsPage.commentCountText ?: initial.commentCountText,
                )
            }
        }

    suspend fun communityPostCommentsContinuation(continuation: String): Result<CommunityCommentsPage> =
        runCatching {
            communityPostCommentsPage(continuation = continuation)
        }

    private suspend fun communityPostsPage(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        continuation: String? = null,
    ): Result<CommunityPostsPage> =
        runCatching {
            val client = currentWebClient()
            val response =
                innerTube.channelBrowse(
                    client = client,
                    channelId = channelId.takeIf { continuation == null },
                    params = CHANNEL_POSTS_PARAMS.takeIf { continuation == null },
                    continuation = continuation,
                )
            Json.parseToJsonElement(response.bodyAsText()).toCommunityPostsPage(
                fallbackAuthorName = channelName,
                fallbackAuthorAvatarUrl = channelThumbnailUrl,
            )
        }

    private suspend fun communityPostCommentsPage(
        postId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): CommunityCommentsPage {
        val client = currentWebClient()
        val response =
            innerTube.postCommentsBrowse(
                client = client,
                postId = postId,
                params = params,
                continuation = continuation,
            )
        return Json.parseToJsonElement(response.bodyAsText()).toCommunityCommentsPage()
    }

    private suspend fun channelVideosPage(
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        params: String?,
        isLive: Boolean,
        continuation: String? = null,
    ): Result<ChannelVideoSearchResult> =
        runCatching {
            val client = currentWebClient()
            val httpResponse =
                innerTube.channelBrowse(
                    client = client,
                    channelId = if (continuation == null) channelId else null,
                    params = params,
                    continuation = continuation,
                )
            val rawBody = httpResponse.bodyAsText()
            val lenientJson =
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            val response = lenientJson.decodeFromString<ChannelVideosResponse>(rawBody)
            val sorts = Json.parseToJsonElement(rawBody).channelSortOptions()
            parseChannelVideosResponse(response, channelId, channelName, channelThumbnailUrl, isLive)
                .copy(sorts = sorts)
        }

    private fun parseChannelVideosResponse(
        response: ChannelVideosResponse,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): ChannelVideoSearchResult {
        val metadata = response.metadata?.channelMetadataRenderer
        val resolvedChannelId =
            metadata?.externalChannelId
                ?: metadata?.externalId
                ?: channelId
        val resolvedChannelName = metadata?.title?.takeIf { it.isNotBlank() } ?: channelName
        val resolvedThumbnail =
            metadata
                ?.avatar
                ?.thumbnails
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
                ?: channelThumbnailUrl

        val richItems = mutableListOf<ChannelVideosResponse.RichItem>()
        response.onResponseReceivedActions
            ?.flatMap {
                it.appendContinuationItemsAction?.continuationItems.orEmpty() +
                    it.reloadContinuationItemsCommand?.continuationItems.orEmpty()
            }?.let { richItems += it }

        response.continuationContents
            ?.richGridContinuation
            ?.contents
            ?.let { richItems += it }

        val tabs =
            response.contents
                ?.twoColumnBrowseResultsRenderer
                ?.tabs
                .orEmpty()
        val selectedTab =
            tabs.firstOrNull { it.tabRenderer?.selected == true }?.tabRenderer
                ?: tabs.firstOrNull { it.tabRenderer?.content?.richGridRenderer != null }?.tabRenderer
                ?: tabs.firstOrNull { it.expandableTabRenderer?.content?.richGridRenderer != null }?.expandableTabRenderer
        selectedTab
            ?.content
            ?.richGridRenderer
            ?.contents
            ?.let { richItems += it }

        val videos = mutableListOf<io.github.aedev.flow.data.model.Video>()
        var nextContinuation: String? = null
        richItems.forEach { richItem ->
            val content = richItem.richItemRenderer?.content
            content
                ?.lockupViewModel
                ?.let { parseLockupViewModel(it, resolvedChannelId, resolvedChannelName, resolvedThumbnail, isLive) }
                ?.let { videos.add(it) }
            content
                ?.videoRenderer
                ?.let { parseBrowseVideoRenderer(it, resolvedChannelId, resolvedChannelName, resolvedThumbnail, isLive) }
                ?.let { videos.add(it) }
            richItem.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
                ?.let { nextContinuation = it }
        }

        return ChannelVideoSearchResult(
            videos = videos.distinctBy { it.id },
            continuation = nextContinuation,
            channelVideoCountText = response.channelVideoCountText(),
        )
    }

    private fun parseLockupViewModel(
        lockup: ChannelVideosResponse.LockupViewModel,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): io.github.aedev.flow.data.model.Video? {
        val videoId = lockup.contentId ?: return null
        val metadata = lockup.metadata?.lockupMetadataViewModel
        val title = metadata?.title?.content?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail =
            lockup.contentImage
                ?.thumbnailViewModel
                ?.image
                ?.sources
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val durationText =
            lockup.contentImage
                ?.thumbnailViewModel
                ?.overlays
                ?.firstNotNullOfOrNull { overlay ->
                    overlay.thumbnailBottomOverlayViewModel
                        ?.badges
                        ?.firstNotNullOfOrNull { it.thumbnailBadgeViewModel?.text }
                }
        val parts =
            metadata
                ?.metadata
                ?.contentMetadataViewModel
                ?.metadataRows
                ?.firstOrNull()
                ?.metadataParts
                ?.mapNotNull { it.text?.content?.takeIf(String::isNotBlank) }
                .orEmpty()
        val viewsText =
            parts.firstOrNull { it.contains("view", ignoreCase = true) || it.contains("watching", ignoreCase = true) }
                ?: parts.firstOrNull()
        val uploadText =
            parts
                .firstOrNull {
                    !it.contains("view", ignoreCase = true) && !it.contains("watching", ignoreCase = true)
                }.orEmpty()

        return io.github.aedev.flow.data.model.Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = parseLengthText(durationText),
            viewCount = parseViewCountText(viewsText),
            uploadDate = uploadText,
            timestamp = parseRelativeUploadDate(uploadText) ?: 0L,
            channelThumbnailUrl = channelThumbnailUrl,
            isLive = isLive || viewsText?.contains("watching", ignoreCase = true) == true,
        )
    }

    private fun parseBrowseVideoRenderer(
        r: ChannelVideosResponse.VideoRenderer,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
        isLive: Boolean,
    ): io.github.aedev.flow.data.model.Video? {
        val videoId = r.videoId ?: return null
        val title = r.title?.textValue()?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail =
            r.thumbnail
                ?.thumbnails
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val uploadText = r.publishedTimeText?.textValue().orEmpty()
        val viewsText = r.viewCountText?.textValue()
        val avatarUrls = r.channelAvatarUrls(channelThumbnailUrl)
        return io.github.aedev.flow.data.model.Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = parseLengthText(r.lengthText?.textValue()),
            viewCount = parseViewCountText(viewsText),
            uploadDate = uploadText,
            timestamp = parseRelativeUploadDate(uploadText) ?: 0L,
            channelThumbnailUrl = avatarUrls.firstOrNull().orEmpty(),
            channelThumbnailUrls = avatarUrls,
            isLive = isLive || viewsText?.contains("watching", ignoreCase = true) == true,
        )
    }

    private fun ChannelVideosResponse.SimpleText.textValue(): String? = simpleText ?: runs?.joinToString("") { it.text.orEmpty() }

    private fun ChannelVideosResponse.VideoRenderer.channelAvatarUrls(fallback: String): List<String> {
        val supported = channelThumbnailSupportedRenderers
        val stackAvatars =
            listOfNotNull(
                avatarStackViewModel,
                supported?.avatarStackViewModel,
                supported?.channelThumbnailWithLinkRenderer?.avatarStack?.avatarStackViewModel,
            ).flatMap { stack ->
                stack.avatars.orEmpty().mapNotNull { avatar ->
                    avatar.avatarViewModel
                        ?.image
                        ?.sources
                        ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                        ?.url
                }
            }
        val linkedAvatar =
            supported
                ?.channelThumbnailWithLinkRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                ?.url

        return (stackAvatars + linkedAvatar + fallback)
            .filter { !it.isNullOrBlank() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(2)
            .filterNotNull()
    }

    private fun parseChannelSearchResponse(
        response: io.github.aedev.flow.innertube.models.response.ChannelSearchResponse,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): ChannelVideoSearchResult {
        val tabs =
            response.contents
                ?.twoColumnBrowseResultsRenderer
                ?.tabs
                .orEmpty()
        Log.d("ChannelSearch", "tabs=${tabs.size}")
        tabs.forEachIndexed { i, tab ->
            val url =
                tab.tabRenderer
                    ?.endpoint
                    ?.commandMetadata
                    ?.webCommandMetadata
                    ?.url
            Log.d(
                "ChannelSearch",
                "tab[$i]: url=$url, selected=${tab.tabRenderer?.selected}, " +
                    "hasSection=${tab.tabRenderer?.content?.sectionListRenderer != null}, " +
                    "hasRichGrid=${tab.tabRenderer?.content?.richGridRenderer != null}, " +
                    "isExpandable=${tab.expandableTabRenderer != null}",
            )
        }

        val tabContent =
            tabs
                .firstOrNull {
                    it.tabRenderer?.selected == true && it.tabRenderer.endpoint
                        ?.commandMetadata
                        ?.webCommandMetadata
                        ?.url
                        ?.contains("/search") == true
                }?.tabRenderer
                ?.content
                ?: tabs.firstOrNull { it.expandableTabRenderer?.content != null }?.expandableTabRenderer?.content
                ?: tabs.firstOrNull { it.tabRenderer?.selected == true }?.tabRenderer?.content

        Log.d(
            "ChannelSearch",
            "tabContent=${tabContent != null}, " +
                "hasSection=${tabContent?.sectionListRenderer != null}, " +
                "hasRichGrid=${tabContent?.richGridRenderer != null}",
        )

        val videos = mutableListOf<io.github.aedev.flow.data.model.Video>()
        var continuation: String? = null

        tabContent?.richGridRenderer?.contents?.forEach { richItem ->
            richItem.richItemRenderer
                ?.content
                ?.videoRenderer
                ?.let { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                ?.let { videos.add(it) }
            richItem.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
                ?.let { continuation = it }
        }

        if (videos.isEmpty()) {
            val sectionContents = tabContent?.sectionListRenderer?.contents.orEmpty()
            sectionContents
                .mapNotNull { it.itemSectionRenderer?.contents }
                .flatten()
                .mapNotNull { it.videoRenderer }
                .mapNotNull { parseVideoRenderer(it, channelId, channelName, channelThumbnailUrl) }
                .forEach { videos.add(it) }
            if (continuation == null) {
                continuation =
                    sectionContents
                        .mapNotNull { it.continuationItemRenderer }
                        .firstOrNull()
                        ?.continuationEndpoint
                        ?.continuationCommand
                        ?.token
            }
        }

        Log.d("ChannelSearch", "videos=${videos.size}, hasContinuation=${continuation != null}")
        return ChannelVideoSearchResult(videos = videos, continuation = continuation)
    }

    private fun parseVideoRenderer(
        r: io.github.aedev.flow.innertube.models.response.ChannelSearchResponse.VideoRenderer,
        channelId: String,
        channelName: String,
        channelThumbnailUrl: String,
    ): io.github.aedev.flow.data.model.Video? {
        val videoId = r.videoId ?: return null
        val title =
            r.title
                ?.runs
                ?.joinToString("") { it.text ?: "" }
                ?.takeIf { it.isNotBlank() } ?: return null
        val thumbnail =
            r.thumbnail
                ?.thumbnails
                ?.maxByOrNull { it.width ?: 0 }
                ?.url
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val duration = parseLengthText(r.lengthText?.simpleText)
        val viewCount = parseViewCountText(r.viewCountText?.simpleText)
        val avatarUrls = r.channelAvatarUrls(channelThumbnailUrl)
        return io.github.aedev.flow.data.model.Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = duration,
            viewCount = viewCount,
            uploadDate = r.publishedTimeText?.simpleText ?: "",
            channelThumbnailUrl = avatarUrls.firstOrNull().orEmpty(),
            channelThumbnailUrls = avatarUrls,
        )
    }

    private fun io.github.aedev.flow.innertube.models.response.ChannelSearchResponse.VideoRenderer.channelAvatarUrls(
        fallback: String,
    ): List<String> {
        val supported = channelThumbnailSupportedRenderers
        val stackAvatars =
            listOfNotNull(
                avatarStackViewModel,
                supported?.avatarStackViewModel,
                supported?.channelThumbnailWithLinkRenderer?.avatarStack?.avatarStackViewModel,
            ).flatMap { stack ->
                stack.avatars.orEmpty().mapNotNull { avatar ->
                    avatar.avatarViewModel
                        ?.image
                        ?.sources
                        ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                        ?.url
                }
            }
        val linkedAvatar =
            supported
                ?.channelThumbnailWithLinkRenderer
                ?.thumbnail
                ?.thumbnails
                ?.maxByOrNull { maxOf(it.width ?: 0, it.height ?: 0) }
                ?.url

        return (stackAvatars + linkedAvatar + fallback)
            .filter { !it.isNullOrBlank() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(2)
            .filterNotNull()
    }

    private fun parseLengthText(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.split(":").map { it.trim().toIntOrNull() ?: 0 }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> 0
        }
    }

    private fun parseViewCountText(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val normalized =
            text
                .lowercase(Locale.US)
                .replace(",", "")
                .replace("views", "")
                .replace("view", "")
                .replace("watching", "")
                .trim()
        val number =
            Regex("""(\d+(?:\.\d+)?)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
                ?: return 0L
        val multiplier =
            when {
                normalized.contains("b") -> 1_000_000_000.0
                normalized.contains("m") -> 1_000_000.0
                normalized.contains("k") -> 1_000.0
                else -> 1.0
            }
        return (number * multiplier).toLong()
    }

    private fun parseRelativeUploadDate(text: String?): Long? {
        val normalized =
            text
                ?.lowercase(Locale.US)
                ?.replace("streamed", "")
                ?.replace("premiered", "")
                ?.replace("live", "")
                ?.replace("ago", "")
                ?.trim()
                ?: return null

        if (normalized.isBlank()) return null
        if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
        if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val value =
            Regex("""(\d+)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?: return null
        val unitMillis =
            when {
                normalized.contains("second") || normalized.endsWith("s") -> 1_000L
                normalized.contains("minute") || normalized.endsWith("m") -> 60_000L
                normalized.contains("hour") || normalized.endsWith("h") -> 3_600_000L
                normalized.contains("day") || normalized.endsWith("d") -> 86_400_000L
                normalized.contains("week") || normalized.endsWith("w") -> 7L * 86_400_000L
                normalized.contains("month") || normalized.endsWith("mo") -> 30L * 86_400_000L
                normalized.contains("year") || normalized.endsWith("y") -> 365L * 86_400_000L
                else -> return null
            }

        return System.currentTimeMillis() - (value * unitMillis)
    }

    suspend fun album(
        browseId: String,
        withSongs: Boolean = true,
    ): Result<AlbumPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()
            if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
                val playlistId =
                    response.header
                        ?.musicDetailHeaderRenderer
                        ?.menu
                        ?.menuRenderer
                        ?.topLevelButtons
                        ?.firstOrNull()
                        ?.buttonRenderer
                        ?.navigationEndpoint
                        ?.watchPlaylistEndpoint
                        ?.playlistId!!
                val albumItem =
                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title =
                            response.header.musicDetailHeaderRenderer.title.runs
                                ?.firstOrNull()
                                ?.text!!,
                        artists =
                            response.header.musicDetailHeaderRenderer.subtitle.runs?.filter { it.navigationEndpoint != null }?.map {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            },
                        year =
                            response.header.musicDetailHeaderRenderer.subtitle.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail =
                            response.header.musicDetailHeaderRenderer.thumbnail.croppedSquareThumbnailRenderer
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()!!
                                .url,
                        explicit = false, // TODO: Extract explicit badge for albums from YouTube response
                    )
                return@runCatching AlbumPage(
                    album = albumItem,
                    songs =
                        response.contents
                            ?.singleColumnBrowseResultsRenderer
                            ?.tabs
                            ?.firstOrNull()
                            ?.tabRenderer
                            ?.content
                            ?.sectionListRenderer
                            ?.contents
                            ?.firstOrNull()
                            ?.musicShelfRenderer
                            ?.contents
                            ?.getItems()
                            ?.mapNotNull {
                                AlbumPage.getSong(it, albumItem)
                            }!!
                            .toMutableList(),
                    otherVersions = emptyList(),
                )
            } else {
                val playlistId =
                    response.microformat
                        ?.microformatDataRenderer
                        ?.urlCanonical
                        ?.substringAfterLast('=')!!
                val albumItem =
                    AlbumItem(
                        browseId = browseId,
                        playlistId = playlistId,
                        title =
                            response.contents
                                ?.twoColumnBrowseResultsRenderer
                                ?.tabs
                                ?.firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text!!,
                        artists =
                            response.contents.twoColumnBrowseResultsRenderer.tabs
                                .firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveHeaderRenderer
                                ?.straplineTextOne
                                ?.runs
                                ?.oddElements()
                                ?.map {
                                    Artist(
                                        name = it.text,
                                        id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                    )
                                }!!,
                        year =
                            response.contents.twoColumnBrowseResultsRenderer.tabs
                                .firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveHeaderRenderer
                                ?.subtitle
                                ?.runs
                                ?.lastOrNull()
                                ?.text
                                ?.toIntOrNull(),
                        thumbnail =
                            response.contents.twoColumnBrowseResultsRenderer.tabs
                                .firstOrNull()
                                ?.tabRenderer
                                ?.content
                                ?.sectionListRenderer
                                ?.contents
                                ?.firstOrNull()
                                ?.musicResponsiveHeaderRenderer
                                ?.thumbnail
                                ?.musicThumbnailRenderer
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()
                                ?.url!!,
                        explicit = false, // TODO: Extract explicit badge for albums from YouTube response
                    )
                return@runCatching AlbumPage(
                    album = albumItem,
                    songs =
                        if (withSongs) {
                            albumSongs(
                                playlistId,
                                albumItem,
                            ).getOrThrow()
                        } else {
                            emptyList()
                        },
                    otherVersions =
                        response.contents.twoColumnBrowseResultsRenderer.secondaryContents
                            ?.sectionListRenderer
                            ?.contents
                            ?.getOrNull(
                                1,
                            )?.musicCarouselShelfRenderer
                            ?.contents
                            ?.mapNotNull { it.musicTwoRowItemRenderer }
                            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                            .orEmpty(),
                )
            }
        }

    suspend fun albumSongs(
        playlistId: String,
        album: AlbumItem? = null,
    ): Result<List<SongItem>> =
        runCatching {
            var response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()

            val twoColumnRenderer = response.contents?.twoColumnBrowseResultsRenderer
            val secondaryContents = twoColumnRenderer?.secondaryContents
            val sectionList = secondaryContents?.sectionListRenderer
            val firstContent = sectionList?.contents?.firstOrNull()
            val playlistShelf = firstContent?.musicPlaylistShelfRenderer

            val songs =
                playlistShelf
                    ?.contents
                    ?.getItems()
                    ?.mapNotNull {
                        AlbumPage.getSong(it, album)
                    }!!
                    .toMutableList()
            var continuation = playlistShelf?.contents?.getContinuation()
            val seenContinuations = mutableSetOf<String>()
            var requestCount = 0
            val maxRequests = 50 // Prevent excessive API calls

            while (continuation != null && requestCount < maxRequests) {
                // Prevent infinite loops by tracking seen continuations
                if (continuation in seenContinuations) {
                    break
                }
                seenContinuations.add(continuation)
                requestCount++

                response =
                    innerTube
                        .browse(
                            client = WEB_REMIX,
                            continuation = continuation,
                        ).body<BrowseResponse>()
                songs +=
                    response.onResponseReceivedActions
                        ?.firstOrNull()
                        ?.appendContinuationItemsAction
                        ?.continuationItems
                        ?.getItems()
                        ?.mapNotNull {
                            AlbumPage.getSong(it, album)
                        }.orEmpty()
                continuation =
                    response.continuationContents
                        ?.musicPlaylistShelfContinuation
                        ?.continuations
                        ?.getContinuation()
            }
            songs
        }

    suspend fun artist(browseId: String): Result<ArtistPage> =
        runCatching {
            ArtistPage.fromBrowseResponse(browseId, innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>())
        }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
            val sectionContent =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()

            val gridRenderer = sectionContent?.gridRenderer
            val musicCarouselShelfRenderer = sectionContent?.musicCarouselShelfRenderer
            val musicPlaylistShelfRenderer = sectionContent?.musicPlaylistShelfRenderer
            val musicShelfRenderer = sectionContent?.musicShelfRenderer

            when {
                gridRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            gridRenderer.header
                                ?.gridHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                .orEmpty(),
                        items =
                            gridRenderer.items.mapNotNull {
                                it.musicTwoRowItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                                }
                            },
                        continuation = gridRenderer.continuations?.getContinuation(),
                    )
                }

                musicCarouselShelfRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            musicCarouselShelfRenderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                .orEmpty(),
                        items =
                            musicCarouselShelfRenderer.contents.mapNotNull { content ->
                                content.musicTwoRowItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                                } ?: content.musicResponsiveListItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicResponsiveListItemRenderer(renderer)
                                }
                            },
                        continuation = null,
                    )
                }

                musicShelfRenderer != null -> {
                    ArtistItemsPage(
                        title =
                            musicShelfRenderer.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text
                                ?: response.header
                                    ?.musicHeaderRenderer
                                    ?.title
                                    ?.runs
                                    ?.firstOrNull()
                                    ?.text
                                ?: "",
                        items =
                            musicShelfRenderer.contents?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = musicShelfRenderer.continuations?.getContinuation(),
                    )
                }

                else -> {
                    ArtistItemsPage(
                        title =
                            response.header
                                ?.musicHeaderRenderer
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text ?: "",
                        items =
                            musicPlaylistShelfRenderer?.contents?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = musicPlaylistShelfRenderer?.contents?.getContinuation(),
                    )
                }
            }
        }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()

            when {
                response.continuationContents?.gridContinuation != null -> {
                    val gridContinuation = response.continuationContents.gridContinuation
                    ArtistItemsContinuationPage(
                        items =
                            gridContinuation.items.mapNotNull {
                                it.musicTwoRowItemRenderer?.let { renderer ->
                                    ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                                }
                            },
                        continuation = gridContinuation.continuations?.getContinuation(),
                    )
                }

                response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                    val musicPlaylistShelfContinuation = response.continuationContents.musicPlaylistShelfContinuation
                    ArtistItemsContinuationPage(
                        items =
                            musicPlaylistShelfContinuation.contents.getItems().mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            },
                        continuation = musicPlaylistShelfContinuation.continuations?.getContinuation(),
                    )
                }

                response.continuationContents?.musicShelfContinuation != null -> {
                    val musicShelfContinuation = response.continuationContents!!.musicShelfContinuation!!
                    ArtistItemsContinuationPage(
                        items =
                            musicShelfContinuation.contents?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = musicShelfContinuation.continuations?.getContinuation(),
                    )
                }

                else -> {
                    val continuationItems =
                        response.onResponseReceivedActions
                            ?.firstOrNull()
                            ?.appendContinuationItemsAction
                            ?.continuationItems
                    ArtistItemsContinuationPage(
                        items =
                            continuationItems?.getItems()?.mapNotNull {
                                ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                            } ?: emptyList(),
                        continuation = continuationItems?.getContinuation(),
                    )
                }
            }
        }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "VL$playlistId",
                        setLogin = true,
                    ).body<BrowseResponse>()
            val base =
                response.contents
                    ?.twoColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
            val header =
                base?.musicResponsiveHeaderRenderer
                    ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

            val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null

            PlaylistPage(
                playlist =
                    PlaylistItem(
                        id = playlistId,
                        title =
                            header
                                ?.title
                                ?.runs
                                ?.firstOrNull()
                                ?.text!!,
                        author =
                            header.straplineTextOne?.runs?.firstOrNull()?.let {
                                Artist(
                                    name = it.text,
                                    id = it.navigationEndpoint?.browseEndpoint?.browseId,
                                )
                            },
                        songCountText =
                            header.secondSubtitle
                                ?.runs
                                ?.firstOrNull()
                                ?.text,
                        thumbnail =
                            header.thumbnail
                                ?.musicThumbnailRenderer
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()
                                ?.url!!,
                        playEndpoint = null,
                        shuffleEndpoint =
                            header.buttons
                                .lastOrNull()
                                ?.menuRenderer
                                ?.items
                                ?.firstOrNull()
                                ?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint!!,
                        radioEndpoint =
                            header.buttons
                                .getOrNull(2)
                                ?.menuRenderer
                                ?.items
                                ?.find {
                                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                                }?.menuNavigationItemRenderer
                                ?.navigationEndpoint
                                ?.watchPlaylistEndpoint,
                        isEditable = editable,
                    ),
                songs =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.firstOrNull()
                        ?.musicPlaylistShelfRenderer
                        ?.contents
                        ?.getItems()
                        ?.mapNotNull {
                            PlaylistPage.fromMusicResponsiveListItemRenderer(it)
                        } ?: emptyList(),
                songsContinuation =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.contents
                        ?.firstOrNull()
                        ?.musicPlaylistShelfRenderer
                        ?.contents
                        ?.getContinuation(),
                continuation =
                    response.contents
                        ?.twoColumnBrowseResultsRenderer
                        ?.secondaryContents
                        ?.sectionListRenderer
                        ?.continuations
                        ?.getContinuation(),
            )
        }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        browseId = "",
                        setLogin = true,
                    ).body<BrowseResponse>()

            val mainContents =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.flatMap { it.musicPlaylistShelfRenderer?.contents.orEmpty() }
                    ?: emptyList()

            val appendedContents =
                response.onResponseReceivedActions
                    ?.firstOrNull()
                    ?.appendContinuationItemsAction
                    ?.continuationItems
                    .orEmpty()

            val allContents = mainContents + appendedContents

            val songs =
                allContents
                    .mapNotNull { it.musicResponsiveListItemRenderer }
                    .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }

            val nextContinuation =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.continuations
                    ?.getContinuation()
                    ?: response.continuationContents
                        ?.musicPlaylistShelfContinuation
                        ?.continuations
                        ?.getContinuation()
                    ?: response.continuationContents
                        ?.musicShelfContinuation
                        ?.continuations
                        ?.getContinuation()
                    ?: response.onResponseReceivedActions
                        ?.firstOrNull()
                        ?.appendContinuationItemsAction
                        ?.continuationItems
                        ?.getContinuation()

            PlaylistContinuationPage(
                songs = songs,
                continuation = nextContinuation,
            )
        }

    suspend fun home(
        continuation: String? = null,
        params: String? = null,
    ): Result<HomePage> =
        runCatching {
            if (continuation != null) {
                return@runCatching homeContinuation(continuation).getOrThrow()
            }

            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_home", params = params).body<BrowseResponse>()
            val continuation =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.continuations
                    ?.getContinuation()
            val sectionListRender =
                response.contents
                    ?.singleColumnBrowseResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
            val sections =
                sectionListRender
                    ?.contents!!
                    .mapNotNull { it.musicCarouselShelfRenderer }
                    .mapNotNull {
                        HomePage.Section.fromMusicCarouselShelfRenderer(it)
                    }.toMutableList()
            val chips =
                sectionListRender.header
                    ?.chipCloudRenderer
                    ?.chips
                    ?.mapNotNull { HomePage.Chip.fromChipCloudChipRenderer(it) }
            HomePage(chips, sections, continuation)
        }

    private suspend fun homeContinuation(continuation: String): Result<HomePage> =
        runCatching {
            val response =
                innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
            val continuation =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.continuations
                    ?.getContinuation()
            HomePage(
                null,
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.mapNotNull { it.musicCarouselShelfRenderer }
                    ?.mapNotNull {
                        HomePage.Section.fromMusicCarouselShelfRenderer(it)
                    }.orEmpty(),
                continuation,
            )
        }

    suspend fun explore(): Result<ExplorePage> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
            ExplorePage(
                newReleaseAlbums =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId == "FEmusic_new_releases_albums"
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicTwoRowItemRenderer }
                        ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                        .orEmpty(),
                moodAndGenres =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.find {
                            it.musicCarouselShelfRenderer
                                ?.header
                                ?.musicCarouselShelfBasicHeaderRenderer
                                ?.moreContentButton
                                ?.buttonRenderer
                                ?.navigationEndpoint
                                ?.browseEndpoint
                                ?.browseId == "FEmusic_moods_and_genres"
                        }?.musicCarouselShelfRenderer
                        ?.contents
                        ?.mapNotNull { it.musicNavigationButtonRenderer }
                        ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                        .orEmpty(),
            )
        }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.gridRenderer
                ?.items
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
                .orEmpty()
        }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.sectionListRenderer
                ?.contents!!
                .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
        }

    suspend fun browse(
        browseId: String,
        params: String?,
    ): Result<BrowseResult> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
            BrowseResult(
                title =
                    response.header
                        ?.musicHeaderRenderer
                        ?.title
                        ?.runs
                        ?.firstOrNull()
                        ?.text,
                items =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull { content ->
                            when {
                                content.gridRenderer != null -> {
                                    BrowseResult.Item(
                                        title =
                                            content.gridRenderer.header
                                                ?.gridHeaderRenderer
                                                ?.title
                                                ?.runs
                                                ?.firstOrNull()
                                                ?.text,
                                        items =
                                            content.gridRenderer.items
                                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                    )
                                }

                                content.musicCarouselShelfRenderer != null -> {
                                    BrowseResult.Item(
                                        title =
                                            content.musicCarouselShelfRenderer.header
                                                ?.musicCarouselShelfBasicHeaderRenderer
                                                ?.title
                                                ?.runs
                                                ?.firstOrNull()
                                                ?.text,
                                        items =
                                            content.musicCarouselShelfRenderer.contents
                                                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer),
                                    )
                                }

                                else -> {
                                    null
                                }
                            }
                        }.orEmpty(),
            )
        }

    suspend fun library(
        browseId: String,
        tabIndex: Int = 0,
    ) = runCatching {
        val response =
            innerTube
                .browse(
                    client = WEB_REMIX,
                    browseId = browseId,
                    setLogin = true,
                ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents =
            if (tabs != null && tabs.size >= tabIndex) {
                tabs[tabIndex]
                    .tabRenderer.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstOrNull()
            } else {
                null
            }

        when {
            contents?.gridRenderer != null -> {
                LibraryPage(
                    items =
                        contents.gridRenderer.items
                            .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                            .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridRenderer.continuations?.getContinuation(),
                )
            }

            else -> { // contents?.musicShelfRenderer != null
                LibraryPage(
                    items =
                        contents
                            ?.musicShelfRenderer
                            ?.contents!!
                            .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                            .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.getContinuation(),
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val contents = response.continuationContents

            when {
                contents?.gridContinuation != null -> {
                    LibraryContinuationPage(
                        items =
                            contents.gridContinuation.items
                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                        continuation = contents.gridContinuation.continuations?.getContinuation(),
                    )
                }

                else -> { // contents?.musicShelfContinuation != null
                    LibraryContinuationPage(
                        items =
                            contents
                                ?.musicShelfContinuation
                                ?.contents!!
                                .mapNotNull(MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                                .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                        continuation = contents.musicShelfContinuation.continuations?.getContinuation(),
                    )
                }
            }
        }

    suspend fun libraryRecentActivity(): Result<LibraryPage> =
        runCatching {
            val continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value

            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        continuation = continuation,
                        setLogin = true,
                    ).body<BrowseResponse>()

            val items =
                response.continuationContents
                    ?.sectionListContinuation
                    ?.contents
                    ?.firstOrNull()
                    ?.gridRenderer
                    ?.items!!
                    .mapNotNull {
                        it.musicTwoRowItemRenderer?.let { renderer ->
                            LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                        }
                    }.toMutableList()

            items.forEachIndexed { index, item ->
                if (item is ArtistItem) {
                    items[index] = artist(item.id).getOrNull()?.artist!!.copy(thumbnail = item.thumbnail)
                }
            }

            LibraryPage(
                items = items,
                continuation = null,
            )
        }

    suspend fun getChartsPage(
        country: String? = null,
        continuation: String? = null,
    ): Result<ChartsPage> =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_charts",
                        params = "ggMGCgQIgAQ%3D",
                        continuation = continuation,
                        formData = country?.let { BrowseBody.FormData(selectedValues = listOf(it)) },
                    ).body<BrowseResponse>()
            ChartsPage.fromBrowseResponse(response, country)
        }

    suspend fun musicHistory() =
        runCatching {
            val response =
                innerTube
                    .browse(
                        client = WEB_REMIX,
                        browseId = "FEmusic_history",
                        setLogin = true,
                    ).body<BrowseResponse>()

            HistoryPage(
                sections =
                    response.contents
                        ?.singleColumnBrowseResultsRenderer
                        ?.tabs
                        ?.firstOrNull()
                        ?.tabRenderer
                        ?.content
                        ?.sectionListRenderer
                        ?.contents
                        ?.mapNotNull {
                            it.musicShelfRenderer?.let { musicShelfRenderer ->
                                HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                            }
                        },
            )
        }

    suspend fun likeVideo(
        videoId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likeVideo(WEB_REMIX, videoId)
        } else {
            innerTube.unlikeVideo(WEB_REMIX, videoId)
        }
    }

    suspend fun likePlaylist(
        playlistId: String,
        like: Boolean,
    ) = runCatching {
        if (like) {
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        } else {
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
        }
    }

    suspend fun subscribeChannel(
        channelId: String,
        subscribe: Boolean,
    ) = runCatching {
        if (subscribe) {
            innerTube.subscribeChannel(WEB_REMIX, channelId)
        } else {
            innerTube.unsubscribeChannel(WEB_REMIX, channelId)
        }
    }

    suspend fun getChannelId(browseId: String): String {
        artist(browseId).onSuccess {
            return it.artist.channelId ?: ""
        }
        return ""
    }

    suspend fun addToPlaylist(
        playlistId: String,
        videoId: String,
    ) = runCatching {
        innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId)
    }

    suspend fun addPlaylistToPlaylist(
        playlistId: String,
        addPlaylistId: String,
    ) = runCatching {
        innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
    }

    suspend fun removeFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
    ) = runCatching {
        innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
    }

    suspend fun moveSongPlaylist(
        playlistId: String,
        setVideoId: String,
        successorSetVideoId: String?,
    ) = runCatching {
        innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
    }

    fun createPlaylist(title: String) =
        runBlocking {
            innerTube.createPlaylist(WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
        }

    suspend fun renamePlaylist(
        playlistId: String,
        name: String,
    ) = runCatching {
        innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
    }

    suspend fun uploadCustomThumbnailLink(
        playlistId: String,
        image: ByteArray,
    ) = runCatching {
        val uploadUrl = innerTube.getUploadCustomThumbnailLink(WEB_REMIX, image.size).headers["x-guploader-uploadid"]
        val blobReq =
            innerTube.uploadCustomThumbnail(
                WEB_REMIX,
                uploadUrl!!,
                image,
            )
        val blobId = Json.decodeFromString<ImageUploadResponse>(blobReq.bodyAsText()).encryptedBlobId
        innerTube
            .setThumbnailPlaylist(WEB_REMIX, playlistId, blobId)
            .body<EditPlaylistResponse>()
            .newHeader
            ?.musicEditablePlaylistDetailHeaderRenderer
            ?.header
            ?.musicResponsiveHeaderRenderer
            ?.thumbnail
            ?.musicThumbnailRenderer
            ?.getThumbnailUrl()
    }

    suspend fun removeThumbnailPlaylist(playlistId: String) =
        runCatching {
            innerTube
                .removeThumbnailPlaylist(WEB_REMIX, playlistId)
                .body<EditPlaylistResponse>()
                .newHeader
                ?.musicEditablePlaylistDetailHeaderRenderer
                ?.header
                ?.musicResponsiveHeaderRenderer
                ?.thumbnail
                ?.musicThumbnailRenderer
                ?.getThumbnailUrl()
        }

    suspend fun deletePlaylist(playlistId: String) =
        runCatching {
            innerTube.deletePlaylist(WEB_REMIX, playlistId)
        }

    suspend fun player(
        videoId: String,
        playlistId: String? = null,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
        poToken: String? = null,
        localeOverride: YouTubeLocale? = null,
        apiUrl: String? = null,
    ): Result<PlayerResponse> =
        runCatching {
            innerTube.player(client, videoId, playlistId, signatureTimestamp, poToken, localeOverride, apiUrl).body<PlayerResponse>()
        }

    suspend fun playerWeb(
        videoId: String,
        signatureTimestamp: Int?,
        poToken: String?,
        visitorData: String?,
        locale: YouTubeLocale,
        cpn: String?,
        reloadToken: String? = null,
        client: YouTubeClient = YouTubeClient.WEB,
    ): Result<PlayerResponse> =
        runCatching {
            innerTube
                .playerWeb(videoId, signatureTimestamp, poToken, visitorData, locale, cpn, reloadToken, client)
                .body<PlayerResponse>()
        }

    suspend fun liveChatContinuation(videoId: String): Result<String?> =
        runCatching {
            innerTube
                .nextForLiveChat(videoId)
                .body<io.github.aedev.flow.innertube.models.response.LiveChatSeedResponse>()
                .seedContinuation()
        }

    suspend fun liveChat(
        continuation: String,
        offsetMs: Long? = null,
    ): Result<io.github.aedev.flow.innertube.models.response.GetLiveChatResponse> =
        runCatching {
            innerTube
                .getLiveChat(continuation, offsetMs)
                .body<io.github.aedev.flow.innertube.models.response.GetLiveChatResponse>()
        }

    suspend fun watchMetadata(videoId: String): Result<io.github.aedev.flow.innertube.models.response.WatchMetadataResponse> =
        runCatching {
            val primary =
                innerTube
                    .next(WEB, videoId, null, null, null, null, null)
                    .body<io.github.aedev.flow.innertube.models.response.WatchMetadataResponse>()
            if (primary.relatedVideos().isNotEmpty()) {
                primary
            } else {
                val webWatch =
                    runCatching {
                        innerTube
                            .nextForLiveChat(videoId)
                            .body<io.github.aedev.flow.innertube.models.response.WatchMetadataResponse>()
                    }.getOrNull()
                if (webWatch != null && webWatch.relatedVideos().size > primary.relatedVideos().size) {
                    webWatch
                } else {
                    primary
                }
            }
        }

    suspend fun watchMetadataLite(videoId: String): Result<io.github.aedev.flow.innertube.models.response.WatchMetadataResponse> =
        runCatching {
            innerTube
                .next(WEB, videoId, null, null, null, null, null)
                .body<io.github.aedev.flow.innertube.models.response.WatchMetadataResponse>()
        }

    suspend fun registerPlayback(
        playlistId: String? = null,
        playbackTracking: String,
    ) = runCatching {
        val cpn =
            (1..16)
                .map {
                    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[
                        Random.Default.nextInt(
                            0,
                            64,
                        ),
                    ]
                }.joinToString("")

        val playbackUrl =
            playbackTracking.replace(
                "https://s.youtube.com",
                "https://music.youtube.com",
            )

        innerTube.registerPlayback(
            url = playbackUrl,
            playlistId = playlistId,
            cpn = cpn,
        )
    }

    suspend fun next(
        endpoint: WatchEndpoint,
        continuation: String? = null,
    ): Result<NextResult> =
        runCatching {
            val response =
                innerTube
                    .next(
                        WEB_REMIX,
                        endpoint.videoId,
                        endpoint.playlistId,
                        endpoint.playlistSetVideoId,
                        endpoint.index,
                        endpoint.params,
                        continuation,
                    ).body<NextResponse>()
            // YouTube inserts/reorders watch-next tabs (a Comments tab appeared at
            // index 2 in 2026), so lyrics/related must be found by browseId prefix,
            // never by position.
            val watchNextTabs =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
            val lyricsBrowseEndpoint =
                watchNextTabs?.firstNotNullOfOrNull { tab ->
                    tab.tabRenderer.endpoint
                        ?.browseEndpoint
                        ?.takeIf { it.browseId.startsWith("MPLYt") }
                }
            val relatedBrowseEndpoint =
                watchNextTabs?.firstNotNullOfOrNull { tab ->
                    tab.tabRenderer.endpoint
                        ?.browseEndpoint
                        ?.takeIf { it.browseId.startsWith("MPTRt") }
                }
            val playlistPanelRenderer =
                response.continuationContents?.playlistPanelContinuation
                    ?: response.contents.singleColumnMusicWatchNextResultsRenderer
                        ?.tabbedRenderer
                        ?.watchNextTabbedResultsRenderer
                        ?.tabs
                        ?.get(0)
                        ?.tabRenderer
                        ?.content
                        ?.musicQueueRenderer
                        ?.content
                        ?.playlistPanelRenderer!!
            val title =
                response.contents.singleColumnMusicWatchNextResultsRenderer
                    ?.tabbedRenderer
                    ?.watchNextTabbedResultsRenderer
                    ?.tabs
                    ?.get(0)
                    ?.tabRenderer
                    ?.content
                    ?.musicQueueRenderer
                    ?.header
                    ?.musicQueueHeaderRenderer
                    ?.subtitle
                    ?.runs
                    ?.firstOrNull()
                    ?.text
            val items =
                playlistPanelRenderer.contents.mapNotNull { content ->
                    content.playlistPanelVideoRenderer
                        ?.let(NextPage::fromPlaylistPanelVideoRenderer)
                        ?.let { it to content.playlistPanelVideoRenderer.selected }
                }
            val songs = items.map { it.first }
            val currentIndex = items.indexOfFirst { it.second }.takeIf { it != -1 }

            // load automix items
            playlistPanelRenderer.contents
                .lastOrNull()
                ?.automixPreviewVideoRenderer
                ?.content
                ?.automixPlaylistVideoRenderer
                ?.navigationEndpoint
                ?.watchPlaylistEndpoint
                ?.let { watchPlaylistEndpoint ->
                    return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                        result.copy(
                            title = title,
                            items = songs + result.items,
                            lyricsEndpoint = lyricsBrowseEndpoint,
                            relatedEndpoint = relatedBrowseEndpoint,
                            currentIndex = currentIndex,
                            endpoint = watchPlaylistEndpoint,
                        )
                    }
                }
            NextResult(
                title = title,
                items = songs,
                currentIndex = currentIndex,
                lyricsEndpoint = lyricsBrowseEndpoint,
                relatedEndpoint = relatedBrowseEndpoint,
                continuation = playlistPanelRenderer.continuations?.getContinuation(),
                endpoint = endpoint,
            )
        }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> =
        runCatching {
            val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
            response.contents
                ?.sectionListRenderer
                ?.contents
                ?.firstOrNull()
                ?.musicDescriptionShelfRenderer
                ?.description
                ?.runs
                ?.firstOrNull()
                ?.text
        }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> =
        runCatching {
            RelatedPage.fromBrowseResponse(innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>())
        }

    suspend fun queue(
        videoIds: List<String>? = null,
        playlistId: String? = null,
    ): Result<List<SongItem>> =
        runCatching {
            if (videoIds != null) {
                assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
            }
            innerTube
                .getQueue(WEB_REMIX, videoIds, playlistId)
                .body<GetQueueResponse>()
                .queueDatas
                .mapNotNull {
                    it.content.playlistPanelVideoRenderer?.let { renderer ->
                        NextPage.fromPlaylistPanelVideoRenderer(renderer)
                    }
                }
        }

    suspend fun transcript(videoId: String): Result<String> =
        runCatching {
            val response = innerTube.getTranscript(WEB_REMIX, videoId).body<GetTranscriptResponse>()
            response.actions
                ?.firstOrNull()
                ?.updateEngagementPanelAction
                ?.content
                ?.transcriptRenderer
                ?.body
                ?.transcriptBodyRenderer
                ?.cueGroups
                ?.joinToString(separator = "\n") { group ->
                    val time =
                        group.transcriptCueGroupRenderer.cues[0]
                            .transcriptCueRenderer.startOffsetMs
                    val text =
                        group.transcriptCueGroupRenderer.cues[0]
                            .transcriptCueRenderer.cue.simpleText
                            .trim('♪')
                            .trim(' ')
                    "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
                }!!
        }

    suspend fun visitorData(): Result<String> =
        runCatching {
            Json
                .parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
                .jsonArray[0]
                .jsonArray[2]
                .jsonArray
                .first {
                    (it as? JsonPrimitive)?.contentOrNull?.let { candidate ->
                        VISITOR_DATA_REGEX.containsMatchIn(candidate)
                    } ?: false
                }.jsonPrimitive.content
        }

    suspend fun accountInfo(): Result<AccountInfo> =
        runCatching {
            innerTube
                .accountMenu(WEB_REMIX)
                .body<AccountMenuResponse>()
                .actions[0]
                .openPopupAction.popup.multiPageMenuRenderer
                .header
                ?.activeAccountHeaderRenderer
                ?.toAccountInfo()!!
        }

    suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
        runCatching {
            return innerTube.getMediaInfo(videoId)
        }

    @JvmInline
    value class SearchFilter(
        val value: String,
    ) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    @JvmInline
    value class LibraryFilter(
        val value: String,
    ) {
        companion object {
            val FILTER_RECENT_ACTIVITY = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000

    suspend fun shorts(sequenceParams: String? = null): Result<ShortsPage> =
        runCatching {
            innerTube
                .reel(
                    client = YouTubeClient.ANDROID,
                    sequenceParams = sequenceParams ?: "CA8%3D",
                ).toShortsPage()
        }

    /**
     * Fetch the Shorts reel sequence that *follows* [videoId].
     *
     * The seed belongs in `sequenceParams` — the field YouTube's own Shorts player sends. Seeding
     * through `params` instead is rejected outright (HTTP 400), which is what used to send callers
     * to the unseeded feed and open an unrelated Short (#931).
     *
     * The response never contains the seed itself, because the client that asked is already
     * playing it; whoever opens a queue on [videoId] has to supply it.
     */
    suspend fun shortsFromVideo(videoId: String): Result<ShortsPage> =
        runCatching {
            innerTube
                .reel(
                    client = YouTubeClient.ANDROID,
                    sequenceParams = buildShortsSequenceParams(videoId),
                ).toShortsPage()
        }

    /**
     * Resolve stream URLs for a Short using the ANDROID client.
     * The ANDROID client is required for Shorts-compatible stream formats.
     */
    suspend fun shortsPlayer(videoId: String): Result<PlayerResponse> =
        runCatching {
            innerTube
                .player(
                    client = YouTubeClient.ANDROID,
                    videoId = videoId,
                    playlistId = null,
                    signatureTimestamp = null,
                ).body<PlayerResponse>()
        }

    /** Protobuf `{1: videoId}`, base64url — how the reel sequence names the Short it continues from. */
    private fun buildShortsSequenceParams(videoId: String): String {
        val bytes = byteArrayOf(0x0A) + videoId.length.toByte() + videoId.toByteArray(Charsets.UTF_8)
        return java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun getNewPipeStreamUrls(videoId: String): List<Pair<Int, String>> =
        io.github.aedev.flow.innertube.pages.NewPipeExtractor
            .newPipePlayer(videoId)

    suspend fun newPipePlayer(
        videoId: String,
        tempRes: PlayerResponse,
    ): PlayerResponse? {
        val streamsList = getNewPipeStreamUrls(videoId)

        if (streamsList.isEmpty()) return null

        val newFormats =
            streamsList.map { (itag, url) ->
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = url,
                    mimeType = if (itag == 140) "audio/mp4" else "audio/webm",
                    bitrate = if (itag == 140) 128000 else 0,
                    width = null,
                    height = null,
                    contentLength = null,
                    quality = "medium",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = null,
                    audioQuality = "AUDIO_QUALITY_MEDIUM",
                    approxDurationMs = null,
                    audioSampleRate = 44100,
                    audioChannels = 2,
                    loudnessDb = null,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                )
            }

        return tempRes.copy(
            playabilityStatus = PlayerResponse.PlayabilityStatus(status = "OK", reason = null),
            streamingData =
                tempRes.streamingData?.copy(
                    adaptiveFormats = (tempRes.streamingData.adaptiveFormats + newFormats).distinctBy { it.itag },
                ) ?: PlayerResponse.StreamingData(
                    formats = emptyList(),
                    adaptiveFormats = newFormats,
                    expiresInSeconds = 21600,
                ),
        )
    }

    private val VISITOR_DATA_REGEX = Regex("^Cg[t|s]")
}
