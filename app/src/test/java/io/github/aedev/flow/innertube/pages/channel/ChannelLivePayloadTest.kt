package io.github.aedev.flow.innertube.pages.channel

import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import io.github.aedev.flow.innertube.pages.renderer.FeedShelfStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the parsers against payloads captured from the live endpoint for Linus Tech Tips, trimmed to a
 * few items per list. Hand-written fixtures pass whatever the author imagined; these fail when the
 * real shape is not the imagined one, which is how the empty Home, Shows and Playlists tabs got
 * through the first time.
 */
class ChannelLivePayloadTest {
    private fun fixture(name: String): JsonElement {
        val stream =
            requireNotNull(javaClass.classLoader?.getResourceAsStream("channel/$name.json")) {
                "missing fixture channel/$name.json"
            }
        return Json.parseToJsonElement(stream.bufferedReader().use { it.readText() })
    }

    private val owner = FeedItemOwner(id = "UCXuqSBlHAE6Xw-yeJA0Tunw", name = "Linus Tech Tips")

    private fun content(
        name: String,
        kind: ChannelTabKind,
    ) = fixture(name).toChannelTabContent(kind, owner)

    // ── tabs ──────────────────────────────────────────────────────────────────

    @Test
    fun `the landing response lists all ten tabs with their real params`() {
        val tabs = fixture("landing").toChannelTabs()

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
                ChannelTabKind.Search,
            ),
            tabs.map { it.kind },
        )
        assertEquals("EgZ2aWRlb3PyBgQKAjoA", tabs.first { it.kind == ChannelTabKind.Videos }.params)
        assertTrue(tabs.first().selected)
    }

    // ── items ─────────────────────────────────────────────────────────────────

    @Test
    fun `the videos tab yields videos`() {
        val items = content("videos", ChannelTabKind.Videos).items

        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it is FeedItem.VideoItem })
        val first = (items.first() as FeedItem.VideoItem).video
        assertTrue(first.id.isNotBlank())
        assertTrue(first.title.isNotBlank())
        assertTrue("duration should come off the badge", first.duration > 0)
        assertEquals("Linus Tech Tips", first.channelName)
    }

    @Test
    fun `the shorts tab yields shorts`() {
        val items = content("shorts", ChannelTabKind.Shorts).items

        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it is FeedItem.ShortItem })
        assertTrue((items.first() as FeedItem.ShortItem).video.isShort)
    }

    @Test
    fun `the live tab yields videos`() {
        assertTrue(content("live", ChannelTabKind.Live).items.isNotEmpty())
    }

    @Test
    fun `the playlists tab is not empty`() {
        val items = content("playlists", ChannelTabKind.Playlists).items

        assertTrue("playlists nest a gridRenderer inside a section list", items.isNotEmpty())
        assertTrue(items.all { it is FeedItem.PlaylistItem })
        val first = (items.first() as FeedItem.PlaylistItem).playlist
        assertTrue(first.id.isNotBlank())
        assertTrue(first.name.isNotBlank())
    }

    @Test
    fun `the shows tab is not empty`() {
        val items = content("shows", ChannelTabKind.Shows).items

        assertTrue("shows arrive as gridShowRenderer", items.isNotEmpty())
        val show = items.first() as FeedItem.PlaylistItem
        assertTrue(show.playlist.name.isNotBlank())
        assertTrue("the VL prefix is not part of the playlist id", !show.playlist.id.startsWith("VL"))
        assertTrue(show.playlist.thumbnailUrl.isNotBlank())
    }

    @Test
    fun `the podcasts tab is not empty`() {
        val items = content("podcasts", ChannelTabKind.Podcasts).items

        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it is FeedItem.PlaylistItem })
    }

    @Test
    fun `every grid tab reports a continuation`() {
        listOf("videos" to ChannelTabKind.Videos, "shorts" to ChannelTabKind.Shorts, "live" to ChannelTabKind.Live)
            .forEach { (name, kind) ->
                assertNotNull("$name should page", content(name, kind).continuation)
            }
    }

    // ── filters ───────────────────────────────────────────────────────────────

    @Test
    fun `the videos tab exposes the sort dropdown and the visibility chips separately`() {
        val groups = fixture("videos").channelFilterGroups()

        assertEquals(2, groups.size)

        val sort = groups.first()
        assertTrue("the sort control is a dropdown, not chips", sort.isDropdown)
        assertEquals(listOf("Latest", "Popular", "Oldest"), sort.options.map { it.label })
        assertEquals(0, sort.selectedIndex)
        assertTrue(sort.options.all { !it.continuation.isNullOrBlank() })

        val visibility = groups[1]
        assertTrue(!visibility.isDropdown)
        assertEquals(listOf("Members only", "Public"), visibility.options.map { it.label })
    }

    @Test
    fun `the shorts and live tabs expose three plain sort chips`() {
        listOf("shorts", "live").forEach { name ->
            val groups = fixture(name).channelFilterGroups()
            assertEquals(name, 1, groups.size)
            assertTrue(name, !groups.single().isDropdown)
            assertEquals(name, listOf("Latest", "Popular", "Oldest"), groups.single().options.map { it.label })
        }
    }

    @Test
    fun `the playlists tab exposes its sort-by menu with browse params`() {
        val groups = fixture("playlists").channelFilterGroups()

        assertEquals(1, groups.size)
        val sort = groups.single()
        assertEquals("Sort by", sort.title)
        assertTrue(sort.isDropdown)
        assertEquals(listOf("Date added (newest)", "Last video added"), sort.options.map { it.label })
        assertTrue("playlists sort by browse params, not a continuation", sort.options.all { !it.params.isNullOrBlank() })
        assertEquals(0, sort.selectedIndex)
    }

    @Test
    fun `tabs without filters report none`() {
        assertTrue(fixture("shows").channelFilterGroups().isEmpty())
        assertTrue(fixture("podcasts").channelFilterGroups().isEmpty())
    }

    // ── header and about ──────────────────────────────────────────────────────

    @Test
    fun `the landing header carries identity but not the about panel`() {
        val header = fixture("landing").toChannelHeader("UCXuqSBlHAE6Xw-yeJA0Tunw")

        assertEquals("Linus Tech Tips", header.title)
        assertEquals("@LinusTechTips", header.handle)
        assertEquals("16.9M subscribers", header.subscriberCountText)
        assertEquals("7.9K videos", header.videoCountText)
        assertTrue(header.avatarUrl.isNotBlank())
        assertTrue(header.bannerUrl!!.isNotBlank())
        assertTrue("the about panel is a separate request", header.links.isEmpty())
        assertEquals(null, header.joinedDateText)
    }

    @Test
    fun `the about continuation fills in everything the landing response lacks`() {
        val about = fixture("about").toChannelAbout()

        assertNotNull(about)
        assertEquals("Canada", about!!.countryText)
        assertEquals("Joined Nov 25, 2008", about.joinedDateText)
        assertEquals("9,807,161,908 views", about.viewCountText)
        assertEquals("7,917 videos", about.videoCountText)
        assertTrue(about.description!!.startsWith("Linus Tech Tips is a passionate team"))
    }

    @Test
    fun `about links keep their title, display text and real destination`() {
        val links = fixture("about").toChannelAbout()!!.links

        assertEquals(3, links.size)
        assertEquals("lttstore.com", links.first().title)
        assertEquals("lttstore.com", links.first().displayText)
        assertEquals(
            "the youtube redirect wrapper must be unwrapped",
            "https://www.lttstore.com/",
            links.first().url,
        )
        assertTrue(links.first().iconUrl!!.isNotBlank())
    }

    @Test
    fun `the about panel is reachable from the landing header`() {
        assertNotNull(
            "without this token the About tab can never be filled",
            fixture("landing").channelAboutContinuation(),
        )
    }

    // ── home ──────────────────────────────────────────────────────────────────

    @Test
    fun `the home tab is not empty and keeps its shelves separate`() {
        val home = content("landing", ChannelTabKind.Home)

        assertTrue("home is a section list, not a grid", home.sections.isNotEmpty())
        assertTrue("shelves must not be flattened into one list", home.items.isEmpty())
        val titles = home.sections.mapNotNull { it.title }
        assertTrue(titles.contains("Videos"))
        assertTrue(titles.contains("Popular videos"))
        assertTrue(home.sections.all { it.items.isNotEmpty() })
    }

    @Test
    fun `the home trailer is its own section`() {
        val trailer = content("landing", ChannelTabKind.Home).sections.first()

        assertEquals(FeedShelfStyle.Trailer, trailer.style)
        assertTrue((trailer.items.single() as FeedItem.VideoItem).video.id.isNotBlank())
    }

    @Test
    fun `a home shelf of sister channels parses as channels`() {
        val featured =
            content("landing", ChannelTabKind.Home).sections.first { it.title == "Featured Channels" }

        assertTrue(featured.items.all { it is FeedItem.RelatedChannelItem })
    }

    @Test
    fun `the home shorts shelf parses as shorts`() {
        val shorts = content("landing", ChannelTabKind.Home).sections.first { it.title == "Shorts" }

        assertTrue(shorts.items.all { it is FeedItem.ShortItem })
    }

    // ── members-only ──────────────────────────────────────────────────────────

    @Test
    fun `members-only uploads carry youtube's own badge text`() {
        val videos =
            content("videos", ChannelTabKind.Videos)
                .items
                .map { (it as FeedItem.VideoItem).video }

        val members = videos.filter { it.membersOnlyText != null }
        assertTrue("the fixture should contain at least one members-only upload", members.isNotEmpty())
        assertEquals("Members only", members.first().membersOnlyText)
        assertTrue("an ordinary upload carries no badge", videos.any { it.membersOnlyText == null })
    }

    @Test
    fun `a members-only upload has no view count, which is why its badge matters`() {
        val members =
            content("videos", ChannelTabKind.Videos)
                .items
                .map { (it as FeedItem.VideoItem).video }
                .first { it.membersOnlyText != null }

        assertEquals(0L, members.viewCount)
        assertTrue(members.uploadDate.isNotBlank())
    }

    @Test
    fun `home section ids are unique so the lazy list cannot crash on a duplicate key`() {
        val ids = content("landing", ChannelTabKind.Home).sections.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `the home tab includes the posts shelf`() {
        val posts =
            content("landing", ChannelTabKind.Home)
                .sections
                .firstOrNull { section -> section.items.any { it is FeedItem.PostItem } }

        assertNotNull("the Home posts shelf ships bare postRenderers, not thread wrappers", posts)
        val post = (posts!!.items.first { it is FeedItem.PostItem } as FeedItem.PostItem).post
        assertTrue(post.id.isNotBlank())
        assertTrue(post.authorName.isNotBlank())
    }

    @Test
    fun `a home shelf only advertises a target it can actually open`() {
        val sections = content("landing", ChannelTabKind.Home).sections

        assertTrue("some shelf must link somewhere", sections.any { it.morePlaylistId != null || it.moreParams != null })
        assertTrue("and some must not", sections.any { it.morePlaylistId == null && it.moreParams == null })
    }
}
