package io.github.aedev.flow.innertube.pages.channel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tab list is the whole adaptivity contract: the screen renders one tab per descriptor, so a tab
 * dropped here disappears from the app and a tab invented here renders empty.
 */
class ChannelTabsParserTest {
    private fun tabs(vararg entries: String) =
        Json
            .parseToJsonElement(
                """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[${entries.joinToString(",")}]}}}""",
            ).toChannelTabs()

    private fun tab(
        title: String,
        url: String?,
        params: String? = null,
        selected: Boolean = false,
    ): String {
        val endpoint =
            url
                ?.let {
                    val browse = params?.let { p -> ""","browseEndpoint":{"browseId":"UCx","params":"$p"}""" }.orEmpty()
                    ""","endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"$it"}}$browse}"""
                }.orEmpty()
        return """{"tabRenderer":{"title":"$title","selected":$selected$endpoint}}"""
    }

    @Test
    fun `reads all nine tabs of a large channel in response order`() {
        val resolved =
            tabs(
                tab("Home", "/@LinusTechTips", "EghmZWF0dXJlZPIGBAoCMgA%3D", selected = true),
                tab("Videos", "/@LinusTechTips/videos", "EgZ2aWRlb3PyBgQKAjoA"),
                tab("Shorts", "/@LinusTechTips/shorts"),
                tab("Live", "/@LinusTechTips/streams"),
                tab("Shows", "/@LinusTechTips/shows"),
                tab("Podcasts", "/@LinusTechTips/podcasts"),
                tab("Playlists", "/@LinusTechTips/playlists"),
                tab("Posts", "/@LinusTechTips/community"),
                tab("Store", "/@LinusTechTips/store"),
            )

        assertEquals(
            listOf(
                ChannelTabKind.Home,
                ChannelTabKind.Videos,
                ChannelTabKind.Shorts,
                ChannelTabKind.Live,
                ChannelTabKind.Shows,
                ChannelTabKind.Podcasts,
                ChannelTabKind.Playlists,
                ChannelTabKind.Posts,
                ChannelTabKind.Store,
            ),
            resolved.map { it.kind },
        )
        assertTrue(resolved.first().selected)
    }

    @Test
    fun `a smaller channel yields only the tabs it actually has`() {
        val resolved =
            tabs(
                tab("Home", "/@Hyperplexed"),
                tab("Videos", "/@Hyperplexed/videos"),
                tab("Shorts", "/@Hyperplexed/shorts"),
                tab("Playlists", "/@Hyperplexed/playlists"),
                tab("Posts", "/@Hyperplexed/community"),
            )

        assertEquals(5, resolved.size)
        assertTrue(resolved.none { it.kind == ChannelTabKind.Live })
        assertTrue(resolved.none { it.kind == ChannelTabKind.Podcasts })
        assertTrue(resolved.none { it.kind == ChannelTabKind.Store })
    }

    @Test
    fun `resolves the kind from the url so a translated title still maps`() {
        val resolved =
            tabs(
                tab("Accueil", "/@chaine"),
                tab("Vidéos", "/@chaine/videos"),
                tab("En direct", "/@chaine/streams"),
                tab("Playlists", "/@chaine/playlists"),
            )

        assertEquals(
            listOf(ChannelTabKind.Home, ChannelTabKind.Videos, ChannelTabKind.Live, ChannelTabKind.Playlists),
            resolved.map { it.kind },
        )
        assertEquals("Vidéos", resolved[1].title)
    }

    @Test
    fun `an unrecognised tab is kept as Unknown rather than dropped`() {
        val resolved = tabs(tab("Videos", "/@x/videos"), tab("Membership", "/@x/membership", "EgltZW1iZXJzaGlw"))

        assertEquals(2, resolved.size)
        assertEquals(ChannelTabKind.Unknown, resolved[1].kind)
        assertEquals("Membership", resolved[1].title)
        assertEquals("EgltZW1iZXJzaGlw", resolved[1].params)
    }

    @Test
    fun `takes the params token from the response rather than a constant`() {
        val resolved = tabs(tab("Videos", "/@x/videos", "TOKEN_FROM_SERVER"))

        assertEquals("TOKEN_FROM_SERVER", resolved.single().params)
    }

    @Test
    fun `the landing tab is Home even without an endpoint`() {
        val resolved = tabs(tab("Home", null, selected = true), tab("Videos", "/@x/videos"))

        assertEquals(ChannelTabKind.Home, resolved.first().kind)
        assertNull(resolved.first().params)
    }

    @Test
    fun `reads an expandable tab renderer`() {
        val resolved =
            Json
                .parseToJsonElement(
                    """
                    {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[
                      {"expandableTabRenderer":{"title":"Search",
                        "endpoint":{"commandMetadata":{"webCommandMetadata":{"url":"/@x/search"}}}}}
                    ]}}}
                    """.trimIndent(),
                ).toChannelTabs()

        assertEquals(ChannelTabKind.Search, resolved.single().kind)
    }

    @Test
    fun `a channel-id landing url still resolves to Home`() {
        assertEquals(ChannelTabKind.Home, "/channel/UCXuqSBlHAE6Xw-yeJA0Tunw".toChannelTabKind())
    }
}
