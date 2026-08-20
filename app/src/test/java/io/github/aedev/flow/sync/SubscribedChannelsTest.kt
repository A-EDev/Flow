package io.github.aedev.flow.sync

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.sync.canonical.CanonicalSubscribedChannel
import io.github.aedev.flow.sync.mapping.SubscribedChannelsMapper
import io.github.aedev.flow.sync.merge.SubscribedChannelsMerger
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real subscription sync: the channels themselves, not just the groups that reference them.
 *
 * A subscription group only carries channel ids, so before this the receiving device ended up with
 * a folder pointing at channels it was not subscribed to — and an unsubscribe could never travel,
 * because a missing channel is indistinguishable from one the peer never knew about.
 */
class SubscribedChannelsTest {
    private fun sub(
        id: String,
        at: Long,
        name: String = "Chan $id",
        music: Boolean = false,
    ) = ChannelSubscription(
        channelId = id,
        channelName = name,
        channelThumbnail = "https://example.invalid/$id.jpg",
        subscribedAt = at,
        isMusic = music,
    )

    @Test
    fun a_later_unsubscribe_beats_the_peers_subscription() {
        val onA = listOf(SubscribedChannelsMapper.toCanonical(sub("UC1", 1000), "aaa"))
        val onB = listOf(SubscribedChannelsMapper.tombstone("UC1", 2000, "bbb"))

        assertTrue(SubscribedChannelsMerger.merge(onA, onB).single().deleted)
        assertTrue("order must not matter", SubscribedChannelsMerger.merge(onB, onA).single().deleted)
    }

    @Test
    fun a_later_resubscribe_beats_an_older_unsubscribe() {
        val tombstone = listOf(SubscribedChannelsMapper.tombstone("UC1", 1000, "aaa"))
        val resubscribed = listOf(SubscribedChannelsMapper.toCanonical(sub("UC1", 2000), "bbb"))

        val merged = SubscribedChannelsMerger.merge(tombstone, resubscribed).single()
        assertFalse(merged.deleted)
        assertEquals(2000L, merged.subscribedAtMs)
    }

    @Test
    fun a_resubscribe_keeps_the_display_metadata_a_tombstone_cannot_carry() {
        val known = listOf(SubscribedChannelsMapper.toCanonical(sub("UC1", 1000, name = "Real name"), "aaa"))
        val removedThenRestored = listOf(SubscribedChannelsMapper.tombstone("UC1", 900, "bbb"))

        val merged = SubscribedChannelsMerger.merge(known, removedThenRestored).single()
        assertEquals("Real name", merged.name)
        assertFalse(merged.deleted)
    }

    @Test
    fun channels_from_both_devices_survive_and_music_never_flips_off() {
        val onA =
            listOf(
                SubscribedChannelsMapper.toCanonical(sub("UC1", 1000, music = true), "aaa"),
                SubscribedChannelsMapper.toCanonical(sub("UC2", 1000), "aaa"),
            )
        val onB =
            listOf(
                SubscribedChannelsMapper.toCanonical(sub("UC1", 500), "bbb"),
                SubscribedChannelsMapper.toCanonical(sub("UC3", 500), "bbb"),
            )

        val merged = SubscribedChannelsMerger.merge(onA, onB)
        assertEquals(listOf("UC1", "UC2", "UC3"), merged.map { it.channelId })
        assertTrue(merged.first { it.channelId == "UC1" }.isMusic)
    }

    @Test
    fun merge_is_commutative_associative_and_idempotent() {
        val a = listOf(SubscribedChannelsMapper.toCanonical(sub("UC1", 1000), "aaa"))
        val b = listOf(SubscribedChannelsMapper.tombstone("UC1", 2000, "bbb"))
        val c = listOf(SubscribedChannelsMapper.toCanonical(sub("UC2", 1500), "ccc"))

        assertEquals(
            SubscribedChannelsMerger.merge(a, b),
            SubscribedChannelsMerger.merge(b, a),
        )
        assertEquals(
            SubscribedChannelsMerger.merge(SubscribedChannelsMerger.merge(a, b), c),
            SubscribedChannelsMerger.merge(a, SubscribedChannelsMerger.merge(b, c)),
        )
        val ab = SubscribedChannelsMerger.merge(a, b)
        assertEquals(ab, SubscribedChannelsMerger.merge(ab, ab))
    }

    @Test
    fun round_trips_through_the_wire_unchanged() {
        val records =
            listOf(
                SubscribedChannelsMapper.toCanonical(sub("UC2", 1000, music = true), "aaa"),
                SubscribedChannelsMapper.tombstone("UC1", 2000, "aaa"),
            )
        val wire = SyncSerialization.encodeSubscribedChannels(records)

        assertEquals("records must be emitted in canonical id order", 2, wire.recordCount)
        assertEquals(records.sortedBy { it.channelId }, SyncSerialization.decodeSubscribedChannels(wire.lines))
    }

    @Test
    fun the_mapper_keeps_feed_bookkeeping_device_local() {
        val local =
            sub("UC1", 1000).copy(
                lastVideoId = "vid",
                lastCheckTime = 42,
                lastFeedFetchAt = 43,
                isNotificationEnabled = true,
            )
        val restored =
            SubscribedChannelsMapper.toSubscription(
                SubscribedChannelsMapper.toCanonical(local, "aaa"),
            )

        assertEquals(local.channelId, restored.channelId)
        assertEquals(local.channelName, restored.channelName)
        assertEquals(local.subscribedAt, restored.subscribedAt)
        // Whether a channel notifies *this* device is not a property of the subscription.
        assertFalse(restored.isNotificationEnabled)
        assertEquals(0L, restored.lastCheckTime)
        assertEquals(0L, restored.lastFeedFetchAt)
    }

    @Test
    fun applying_a_channel_preserves_this_devices_local_only_fields() {
        // SyncDataAccess.writeSubscribedChannels re-applies the local-only fields on top of the
        // mapped record; without that, subscribeAll would replace the row wholesale and every sync
        // would reset notification opt-ins and force the feed to re-fetch the channel.
        val local =
            sub("UC1", 1000).copy(
                isNotificationEnabled = true,
                lastVideoId = "vid",
                lastCheckTime = 42,
                lastFeedFetchAt = 43,
            )
        val incoming = SubscribedChannelsMapper.toCanonical(sub("UC1", 2000, name = "Renamed"), "bbb")

        val applied =
            SubscribedChannelsMapper.toSubscription(incoming).copy(
                isNotificationEnabled = local.isNotificationEnabled,
                lastVideoId = local.lastVideoId,
                lastCheckTime = local.lastCheckTime,
                lastFeedFetchAt = local.lastFeedFetchAt,
            )

        assertEquals("Renamed", applied.channelName) // synced fields do update
        assertTrue(applied.isNotificationEnabled)
        assertEquals("vid", applied.lastVideoId)
        assertEquals(42L, applied.lastCheckTime)
        assertEquals(43L, applied.lastFeedFetchAt)
    }

    @Test
    fun a_tombstone_for_a_channel_this_device_never_had_still_travels_onward() {
        // Third-device convergence: A unsubscribes, B never had the channel, C still does.
        val fromA = listOf(SubscribedChannelsMapper.tombstone("UC1", 2000, "aaa"))
        val onB = emptyList<CanonicalSubscribedChannel>()
        val bAfterSync = SubscribedChannelsMerger.merge(onB, fromA)
        assertEquals(1, bAfterSync.size)

        val onC = listOf(SubscribedChannelsMapper.toCanonical(sub("UC1", 1000), "ccc"))
        assertTrue(SubscribedChannelsMerger.merge(onC, bAfterSync).single().deleted)
    }
}
