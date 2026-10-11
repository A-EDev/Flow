package io.github.aedev.flow.sync.apply

import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory
import io.github.aedev.flow.sync.identity.Hlc
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncChangeTrackerTest {
    private val node = "phone123"
    private val ledgers = HashMap<String, CollectionLedger>()
    private val data: SyncDataAccess = mockk()
    private val store: SyncChangeLedgerStore =
        mockk {
            val name = slot<String>()
            val transform = slot<(CollectionLedger) -> CollectionLedger>()
            coEvery { update(capture(name), capture(transform)) } answers {
                transform.captured(ledgers[name.captured] ?: CollectionLedger()).also { ledgers[name.captured] = it }
            }
            coEvery { load(capture(name)) } answers { ledgers[name.captured] ?: CollectionLedger() }
        }
    private val tracker = SyncChangeTracker(data, store)

    private fun history(vararg ids: String) =
        ids.map { CanonicalWatchHistory(videoId = it, watchedAtMs = 10, hlc = Hlc(10, 0, node).encode()) }

    @Test
    fun a_history_entry_deleted_here_goes_out_as_a_tombstone() =
        runBlocking {
            coEvery { data.readWatchHistory(node) } returns history("a", "b")
            tracker.watchHistory(node)
            coEvery { data.readWatchHistory(node) } returns history("a")

            val out = tracker.watchHistory(node)

            val tombstone = out.single { it.videoId == "b" }
            assertTrue(tombstone.deleted)
            assertTrue("stamped when the deletion was seen, after the watch", Hlc.decode(tombstone.hlc).physicalMs > 10)
        }

    @Test
    fun an_unlike_goes_out_as_state_none() =
        runBlocking {
            val like = CanonicalLike(kind = "video", id = "v1", state = CanonicalLike.STATE_LIKED, updatedAtMs = 5)
            coEvery { data.readLikes(node) } returns listOf(like)
            tracker.likes(node)
            coEvery { data.readLikes(node) } returns emptyList()

            val tombstone = tracker.likes(node).single()

            assertEquals("v1", tombstone.id)
            assertEquals("video", tombstone.kind)
            assertEquals(CanonicalLike.STATE_NONE, tombstone.state)
        }

    @Test
    fun a_desktop_tombstone_is_relayed_on_the_next_export() =
        runBlocking {
            coEvery { data.readWatchHistory(node) } returns history("a")
            tracker.watchHistory(node)
            val desktopTombstone =
                CanonicalWatchHistory(videoId = "a", hlc = Hlc(System.currentTimeMillis(), 0, "deskuuid").encode(), deleted = true)
            tracker.settleWatchHistory(listOf(desktopTombstone))
            coEvery { data.readWatchHistory(node) } returns emptyList()

            assertEquals(listOf(desktopTombstone), tracker.watchHistory(node))
        }

    @Test
    fun an_untouched_group_keeps_its_stamp_and_an_edited_one_is_restamped() =
        runBlocking {
            val tech = CanonicalSubscriptionGroup(name = "Tech", channelIds = listOf("UCa"))
            val news = CanonicalSubscriptionGroup(name = "News", channelIds = listOf("UCb"))
            coEvery { data.readSubscriptions("") } returns listOf(tech, news)
            tracker.groups(node)
            coEvery { data.readSubscriptions("") } returns listOf(tech, news.copy(channelIds = listOf("UCb", "UCc")))

            val out = tracker.groups(node).associateBy { it.name }

            assertEquals("never edited since tracking began", 0L, Hlc.decode(out.getValue("Tech").hlc).physicalMs)
            assertTrue(Hlc.decode(out.getValue("News").hlc).physicalMs > 0)
        }

    @Test
    fun a_track_removed_from_a_playlist_goes_out_as_an_item_tombstone() =
        runBlocking {
            fun gym(vararg ids: String) =
                CanonicalPlaylist(
                    syncId = "p1",
                    title = "Gym",
                    items = ids.mapIndexed { i, id -> CanonicalPlaylistItem(videoId = id, position = i.toLong()) },
                )
            coEvery { data.readPlaylists("") } returns listOf(gym("v1", "v2"))
            tracker.playlists(node)
            coEvery { data.readPlaylists("") } returns listOf(gym("v1"))

            val items = tracker.playlists(node).single().items

            assertEquals(listOf("v1", "v2"), items.map { it.videoId })
            assertTrue(items.single { it.videoId == "v2" }.deleted)
        }

    @Test
    fun a_deleted_playlist_goes_out_as_a_tombstone_built_from_its_last_known_shape() =
        runBlocking {
            val saved = CanonicalPlaylist(syncId = "yt:PL1", origin = CanonicalPlaylist.ORIGIN_YOUTUBE, youtubeId = "PL1", title = "Mix")
            coEvery { data.readPlaylists("") } returns listOf(saved)
            tracker.playlists(node)
            coEvery { data.readPlaylists("") } returns emptyList()

            val tombstone = tracker.playlists(node).single()

            assertTrue(tombstone.deleted)
            assertEquals("PL1", tombstone.youtubeId)
            assertEquals(CanonicalPlaylist.ORIGIN_YOUTUBE, tombstone.origin)
        }

    @Test
    fun desktop_only_settings_pass_through_unchanged() =
        runBlocking {
            val desktopOnly = CanonicalSetting("desktop.subtitle_font_size", JsonPrimitive(18), "700:0:deskuuid")
            val autoplay = CanonicalSetting("autoplay", JsonPrimitive(true), "")
            coEvery { data.readSettings("") } returns listOf(autoplay)
            tracker.settings(node)
            tracker.settleSettings(listOf(autoplay.copy(hlc = Hlc(0, 0, node).encode()), desktopOnly))

            val out = tracker.settings(node)

            assertEquals(desktopOnly, out.single { it.key == desktopOnly.key })
            assertEquals(0L, Hlc.decode(out.single { it.key == "autoplay" }.hlc).physicalMs)
        }
}
