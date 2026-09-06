package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.response.BrowseResponse
import io.github.aedev.flow.innertube.models.response.NextResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal object InnerTubeJson {
    const val ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
    const val ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
    const val PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"
    const val USER = "MUSIC_PAGE_TYPE_USER_CHANNEL"
    const val DISCOGRAPHY = "MUSIC_PAGE_TYPE_ARTIST_DISCOGRAPHY"
    const val ATV = "MUSIC_VIDEO_TYPE_ATV"
    const val OMV = "MUSIC_VIDEO_TYPE_OMV"
    const val UGC = "MUSIC_VIDEO_TYPE_UGC"
    const val CIRCLE = "MUSIC_THUMBNAIL_CROP_CIRCLE"
    private const val SQUARE = "MUSIC_THUMBNAIL_CROP_UNSPECIFIED"

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    fun browse(
        sections: List<String>,
        header: String? = null,
        singleColumn: Boolean = false,
    ): BrowseResponse {
        val sectionList = """{"sectionListRenderer":{"contents":[${sections.joinToString(",")}]}}"""
        val contents =
            if (singleColumn) {
                """{"singleColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":$sectionList}}]}}"""
            } else {
                sectionList
            }
        val headerField = header?.let { ""","header":$it""" }.orEmpty()
        return json.decodeFromString(BrowseResponse.serializer(), """{"responseContext":{},"contents":$contents$headerField}""")
    }

    fun next(queueRows: List<String>): NextResponse =
        json.decodeFromString(
            NextResponse.serializer(),
            """{"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{"watchNextTabbedResultsRenderer":{"tabs":[
            |{"tabRenderer":{"content":{"musicQueueRenderer":{"content":{"playlistPanelRenderer":{"contents":[${queueRows.joinToString(
                ",",
            )}]}}}}}}
            |]}}}}}
            """.trimMargin(),
        )

    fun run(
        text: String,
        endpoint: String? = null,
    ) = if (endpoint == null) """{"text":"$text"}""" else """{"text":"$text","navigationEndpoint":$endpoint}"""

    fun separator() = run(" • ")

    fun browseEndpoint(
        browseId: String,
        pageType: String,
    ) =
        """{"browseEndpoint":{"browseId":"$browseId","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$pageType"}}}}"""

    fun watchEndpoint(
        videoId: String,
        musicVideoType: String,
    ) =
        """{"watchEndpoint":{"videoId":"$videoId","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$musicVideoType"}}}}"""

    fun songRow(
        videoId: String,
        title: String,
        musicVideoType: String,
        secondary: List<String>,
        album: String? = null,
    ): String {
        val columns =
            listOfNotNull(
                flexColumn(
                    run(title, watchEndpoint(videoId, musicVideoType)),
                ),
                flexColumn(*secondary.toTypedArray()),
                album?.let {
                    flexColumn(it)
                },
            )
        return """{"musicResponsiveListItemRenderer":{"thumbnail":${thumbnail(
            videoId,
            SQUARE,
        )},"flexColumns":[${columns.joinToString(",")}],
            |"playlistItemData":{"videoId":"$videoId"},"overlay":${playOverlay(watchEndpoint(videoId, musicVideoType))}}}
            """.trimMargin()
    }

    fun artistRow(
        browseId: String,
        name: String,
        subscribers: String,
    ) = """{"musicResponsiveListItemRenderer":{"thumbnail":${thumbnail(
        browseId,
        CIRCLE,
    )},"flexColumns":[${flexColumn(run(name))},${flexColumn(run(subscribers))}],
        |"navigationEndpoint":${browseEndpoint(browseId, ARTIST)}}}
        """.trimMargin()

    fun twoRow(
        browseId: String,
        pageType: String,
        title: String,
        subtitle: List<String>,
        playlistId: String? = null,
        crop: String = SQUARE,
    ): String {
        val playable =
            playlistId
                ?.let {
                    ""","thumbnailOverlay":${playOverlay(watchPlaylistEndpoint(it))},"menu":${menu(it)}"""
                }.orEmpty()
        return """{"musicTwoRowItemRenderer":{"thumbnailRenderer":${thumbnail(
            browseId,
            crop,
        )},"title":${runs(run(title, browseEndpoint(browseId, pageType)))},
            |"subtitle":${runs(*subtitle.toTypedArray())},"navigationEndpoint":${browseEndpoint(browseId, pageType)}$playable}}
            """.trimMargin()
    }

    fun videoTwoRow(
        videoId: String,
        title: String,
        artist: String,
    ) = """{"musicTwoRowItemRenderer":{"thumbnailRenderer":${thumbnail(
        videoId,
        SQUARE,
    )},"title":${runs(run(title))},"subtitle":${runs(run(artist))},
        |"navigationEndpoint":${watchEndpoint(videoId, OMV)}}}
        """.trimMargin()

    fun carousel(
        title: String,
        items: List<String>,
        titleEndpoint: String? = null,
        strapline: String? = null,
        moreBrowseId: String? = null,
        morePageType: String = DISCOGRAPHY,
    ): String {
        val straplineField = strapline?.let { ""","strapline":${runs(run(it))}""" }.orEmpty()
        val moreField =
            moreBrowseId
                ?.let {
                    ""","moreContentButton":{"buttonRenderer":{"text":${runs(
                        run("More"),
                    )},"navigationEndpoint":${browseEndpoint(it, morePageType)}}}"""
                }.orEmpty()
        return """{"musicCarouselShelfRenderer":{"header":{"musicCarouselShelfBasicHeaderRenderer":{"title":${runs(
            run(title, titleEndpoint),
        )}$straplineField$moreField}},
            |"contents":[${items.joinToString(",")}],"itemSize":"COLLECTION_STYLE_ITEM_SIZE_MEDIUM"}}
            """.trimMargin()
    }

    fun musicShelf(
        title: String?,
        items: List<String>,
        formItemKeys: List<String> = emptyList(),
    ): String {
        val titleField = title?.let { """"title":${runs(run(it))},""" }.orEmpty()
        val options = formItemKeys.joinToString(",") { """{"musicMultiSelectMenuItemRenderer":{"formItemEntityKey":"$it"}}""" }
        val subheaders =
            if (formItemKeys.isEmpty()) {
                ""
            } else {
                ""","subheaders":[{"musicSideAlignedItemRenderer":{"startItems":[{"musicSortFilterButtonRenderer":{"menu":{"musicMultiSelectMenuRenderer":{"options":[$options]}}}}]}}]"""
            }
        return """{"musicShelfRenderer":{$titleField"contents":[${items.joinToString(",")}]$subheaders}}"""
    }

    fun immersiveHeader(
        title: String,
        channelId: String,
        subscribers: String,
        monthlyListeners: String,
    ) = """{"musicImmersiveHeaderRenderer":{"title":${runs(run(title))},"menu":{"menuRenderer":{}},
        |"subscriptionButton":{"subscribeButtonRenderer":{"subscribed":false,"channelId":"$channelId","subscriberCountText":${runs(
        run(subscribers),
    )}}},
        |"monthlyListenerCount":${runs(run(monthlyListeners))}}}
        """.trimMargin()

    fun queueRow(
        videoId: String,
        title: String,
        length: String,
        musicVideoType: String,
        byline: List<String>,
        selected: Boolean = false,
    ) = """{"playlistPanelVideoRenderer":{"title":${runs(run(title))},"longBylineText":${runs(*byline.toTypedArray())},
        |"thumbnail":{"thumbnails":[{"url":"https://t/$videoId","width":400,"height":225}]},"lengthText":${runs(run(length))},
        |"selected":$selected,"navigationEndpoint":${watchEndpoint(videoId, musicVideoType)},"videoId":"$videoId"}}
        """.trimMargin()

    private fun runs(vararg runs: String) = """{"runs":[${runs.joinToString(",")}]}"""

    private fun flexColumn(vararg runs: String) = """{"musicResponsiveListItemFlexColumnRenderer":{"text":${runs(*runs)}}}"""

    private fun thumbnail(
        id: String,
        crop: String,
    ) =
        """{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://t/$id","width":60,"height":60}]},"thumbnailCrop":"$crop"}}"""

    private fun watchPlaylistEndpoint(playlistId: String) = """{"watchPlaylistEndpoint":{"playlistId":"$playlistId"}}"""

    private fun playOverlay(endpoint: String) =
        """{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{"playNavigationEndpoint":$endpoint}}}}"""

    private fun menu(playlistId: String): String {
        fun item(iconType: String) =
            """{"menuNavigationItemRenderer":{"text":${runs(
                run(iconType),
            )},"icon":{"iconType":"$iconType"},"navigationEndpoint":${watchPlaylistEndpoint(playlistId)}}}"""
        return """{"menuRenderer":{"items":[${item("MUSIC_SHUFFLE")},${item("MIX")}]}}"""
    }
}
