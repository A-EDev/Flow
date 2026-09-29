package io.github.aedev.flow.innertube.pages.search

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.pages.SearchFixture
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelfStyle
import kotlinx.serialization.json.JsonObject
import org.junit.Test

class SearchPageParserTest {
    private fun page(name: String) = SearchFixture(name).toSearchResultsPage()

    private fun SearchResultsPage.items() = sections.filterIsInstance<SearchSection.Result>().map { it.item }

    private fun SearchResultsPage.strips() = sections.filterIsInstance<SearchSection.Strip>().map { it.shelf }

    private fun SearchResultsPage.videos() = items().filterIsInstance<FeedItem.VideoItem>().map { it.video }

    @Test
    fun `keeps the order youtube arranged the feed in`() {
        val page = page(SearchFixture.ALL_SAM_SULEK)

        val shape =
            page.sections.take(5).map {
                when (it) {
                    is SearchSection.Result -> it.item::class.simpleName
                    is SearchSection.Strip -> "Strip:${it.shelf.style}"
                }
            }
        assertThat(shape)
            .containsExactly(
                "RelatedChannelItem",
                "Strip:List",
                "VideoItem",
                "VideoItem",
                "Strip:Grid",
            ).inOrder()
    }

    @Test
    fun `keeps every shorts strip the response carries`() {
        val strips = page(SearchFixture.ALL_SAM_SULEK).strips().filter { it.style == FeedShelfStyle.Grid }

        assertThat(strips).hasSize(2)
        assertThat(strips.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `reads the channel handle and the subscriber count off the hero card`() {
        val channel =
            page(SearchFixture.ALL_SAM_SULEK)
                .items()
                .filterIsInstance<FeedItem.RelatedChannelItem>()
                .first()
                .channel

        assertThat(channel.name).isEqualTo("Sam Sulek")
        assertThat(channel.handle).isEqualTo("@sam_sulek")
        assertThat(channel.subscriberCount).isEqualTo(4_540_000L)
        // The /channel/<id> form, never the @handle one: a handle is not a valid browseId, and the
        // channel screen browses whatever this url resolves to (400 INVALID_ARGUMENT otherwise).
        assertThat(channel.url).isEqualTo("https://www.youtube.com/channel/UCAuk798iHprjTtwlClkFxMA")
        assertThat(channel.description).isNotEmpty()
    }

    @Test
    fun `classifies the count labels by content so a field swap cannot break it`() {
        val swapped =
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
              {"itemSectionRenderer":{"contents":[{"channelRenderer":{
                "channelId":"UC1","title":{"simpleText":"Creator"},
                "subscriberCountText":{"simpleText":"16.9M subscribers"},
                "videoCountText":{"simpleText":"@creator"}
              }}]}}
            ]}}}}}
            """.trimIndent()

        val channel =
            kotlinx.serialization.json.Json
                .parseToJsonElement(swapped)
                .let { it as JsonObject }
                .toSearchResultsPage()
                .items()
                .filterIsInstance<FeedItem.RelatedChannelItem>()
                .single()
                .channel

        assertThat(channel.handle).isEqualTo("@creator")
        assertThat(channel.subscriberCount).isEqualTo(16_900_000L)
    }

    @Test
    fun `reads badges and verification off a video result`() {
        val video = page(SearchFixture.ALL_SAM_SULEK).videos().first { it.badges.isNotEmpty() }

        assertThat(video.badges).contains("4K")
        assertThat(video.isVerifiedChannel).isTrue()
    }

    @Test
    fun `reads the matched description snippet with its emphasised ranges`() {
        val video = page(SearchFixture.ALL_SAM_SULEK).videos().first { it.snippet.isNotEmpty() }

        assertThat(video.snippet).isNotEmpty()
        assertThat(video.snippetHighlights.all { it.first in video.snippet.indices }).isTrue()
    }

    @Test
    fun `takes the avatar from the card instead of a second request`() {
        val video = page(SearchFixture.ALL_SAM_SULEK).videos().first()

        assertThat(video.channelThumbnailUrl).isNotEmpty()
        assertThat(video.channelId).isNotEmpty()
    }

    @Test
    fun `flags a live result from its badge`() {
        val videos = page(SearchFixture.FEATURE_LIVE).videos()

        assertThat(videos.any { it.isLive }).isTrue()
    }

    @Test
    fun `reads the continuation token and not a header or shelf token`() {
        val page = page(SearchFixture.ALL_SAM_SULEK)

        assertThat(page.continuation).isNotNull()
        assertThat(page.header.chips.mapNotNull { it.continuation }).doesNotContain(page.continuation)
    }

    @Test
    fun `parses a continuation response through the same entry point`() {
        val page = page(SearchFixture.ALL_CONTINUATION)

        assertThat(page.sections).isNotEmpty()
        assertThat(page.continuation).isNotNull()
    }

    @Test
    fun `reads the estimated result count`() {
        assertThat(page(SearchFixture.ALL_SAM_SULEK).estimatedResults).isEqualTo(589_705L)
    }

    @Test
    fun `reads the chip cloud youtube puts above the results`() {
        val chips = page(SearchFixture.HEADER_CHIPS_AND_FILTERS).header.chips

        assertThat(chips.map { it.label })
            .containsExactly("All", "Shorts", "Unwatched", "Watched", "Videos", "Recently uploaded", "Live")
            .inOrder()
        assertThat(chips.first().selected).isTrue()
        assertThat(chips.first().continuation).isNull()
        assertThat(chips.drop(1).all { it.continuation != null }).isTrue()
    }

    @Test
    fun `reads the five filter groups and their params`() {
        val groups = page(SearchFixture.HEADER_CHIPS_AND_FILTERS).header.filterGroups

        assertThat(groups.map { it.title })
            .containsExactly("Type", "Duration", "Upload date", "Features", "Prioritize")
            .inOrder()
        assertThat(groups.first { it.title == "Type" }.options.map { it.params })
            .containsExactly("EgIQAQ%3D%3D", "EgIQCQ%3D%3D", "EgIQAg%3D%3D", "EgIQAw%3D%3D", "EgIQBA%3D%3D")
            .inOrder()
        assertThat(groups.first { it.title == "Prioritize" }.options.map { it.label })
            .containsExactly("Relevance", "Popularity")
            .inOrder()
        assertThat(
            groups
                .first { it.title == "Prioritize" }
                .options
                .first()
                .selected,
        ).isTrue()
    }

    @Test
    fun `returns channels playlists and shorts from their type-filtered responses`() {
        assertThat(page(SearchFixture.TYPE_CHANNELS).items().filterIsInstance<FeedItem.RelatedChannelItem>())
            .hasSize(10)
        assertThat(page(SearchFixture.TYPE_PLAYLISTS).items().filterIsInstance<FeedItem.PlaylistItem>())
            .isNotEmpty()
        assertThat(page(SearchFixture.TYPE_SHORTS).strips().flatMap { it.items })
            .isNotEmpty()
    }

    @Test
    fun `an unknown renderer is skipped and its siblings still parse`() {
        val withGarbage =
            """
            {"contents":{"twoColumnSearchResultsRenderer":{"primaryContents":{"sectionListRenderer":{"contents":[
              {"itemSectionRenderer":{"contents":[
                {"someFutureRenderer":{"videoId":"zzz"}},
                {"videoRenderer":{"videoId":"abc12345678","title":{"simpleText":"Kept"}}}
              ]}}
            ]}}}}}
            """.trimIndent()

        val page =
            kotlinx.serialization.json.Json
                .parseToJsonElement(withGarbage)
                .let { it as JsonObject }
                .toSearchResultsPage()

        assertThat(page.videos().map { it.title }).containsExactly("Kept")
    }

    @Test
    fun `a response with no direct match still parses its suggested results`() {
        val page = page(SearchFixture.NO_RESULTS)

        assertThat(page.sections).isNotEmpty()
    }
}
