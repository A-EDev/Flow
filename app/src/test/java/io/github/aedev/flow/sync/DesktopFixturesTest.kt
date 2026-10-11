package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.mapping.SettingsMapper
import io.github.aedev.flow.sync.protocol.SyncSerialization
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop's own data fixtures (the JSON files in `src-tauri/tests/fixtures/sync/`, mirrored into
 * `resources/sync/desktop/`) decoded through Android's decoders. The suites on either side only
 * ever round-trip their own shapes, which is how field drift between the two shipped unnoticed.
 */
class DesktopFixturesTest {
    private fun lines(name: String): List<String> {
        val text =
            checkNotNull(javaClass.getResourceAsStream("/sync/desktop/$name.json")) { "missing fixture $name" }
                .use { it.readBytes().toString(Charsets.UTF_8) }
        val element = Json.parseToJsonElement(text)
        return if (element is JsonArray) element.map { it.toString() } else listOf(element.toString())
    }

    @Test
    fun watch_history_decodes_with_every_field() {
        val records = SyncSerialization.decodeWatchHistory(lines("watch_history")).associateBy { it.videoId }
        val rick = records.getValue("dQw4w9WgXcQ")
        assertEquals("Never Gonna Give You Up", rick.title)
        assertEquals(212L, rick.durationSeconds)
        assertEquals(0.8, rick.progress, 0.0)
        assertEquals("1781512000000:0:device-aaa", rick.hlc)
        assertTrue(records.getValue("9bZkp7q19f0").isMusic)
    }

    @Test
    fun likes_decode_including_a_tombstone_with_null_meta() {
        val likes = SyncSerialization.decodeLikes(lines("likes")).associateBy { it.id }
        assertEquals("Never Gonna Give You Up", likes.getValue("dQw4w9WgXcQ").meta.title)
        val unliked = likes.getValue("9bZkp7q19f0")
        assertEquals(CanonicalLike.STATE_NONE, unliked.state)
        assertEquals(CanonicalLike.KIND_MUSIC, unliked.kind)
    }

    @Test
    fun playlists_decode_including_the_reserved_watch_later() {
        val playlists = SyncSerialization.decodePlaylists(lines("playlists")).associateBy { it.syncId }
        val gym = playlists.getValue("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("Gym", gym.title)
        assertEquals("dQw4w9WgXcQ", gym.items.first().videoId)
        assertEquals("1781000001000:0:device-aaa", gym.items.first().hlc)
        assertTrue(playlists.getValue(CanonicalPlaylist.RESERVED_WATCH_LATER).isProtected)
    }

    @Test
    fun settings_decode_and_map_only_the_keys_android_has() {
        val settings = SyncSerialization.decodeSettings(lines("settings"))
        assertEquals(4, settings.size)
        val backup = SettingsMapper.applyToBackup(settings)
        assertEquals(true, backup.booleans["autoplay_enabled"])
        assertEquals("1080p", backup.strings["default_quality_wifi"])
        assertEquals(1.25f, backup.floats["playback_speed"])
        assertFalse("an object-valued key Android lacks is ignored", "sponsorblock_categories" in backup.strings)
    }

    @Test
    fun subscribed_channels_decode_including_an_unsubscribe() {
        val channels = SyncSerialization.decodeSubscribedChannels(lines("subscribed_channels")).associateBy { it.channelId }
        assertEquals("Some Channel", channels.getValue("UCabc").name)
        assertTrue(channels.getValue("UCgone").deleted)
    }

    @Test
    fun subscription_groups_decode() {
        val group = SyncSerialization.decodeSubscriptions(lines("subscriptions")).single()
        assertEquals("Tech", group.name)
        assertEquals(listOf("UCabc", "UCdef"), group.channelIds)
    }

    @Test
    fun the_music_brain_decodes() {
        val brain = checkNotNull(SyncSerialization.decodeMusicBrain(lines("music_brain")))
        val psy = brain.artistAffinity.getValue("UCpsy")
        assertEquals(12L, psy.plays.sum())
        assertEquals(0.7, psy.score, 0.0)
        assertEquals("PSY", brain.trackMeta.getValue("9bZkp7q19f0").artist)
    }
}
