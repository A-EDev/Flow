package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.BrainCounters
import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.protocol.SyncSerialization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoder-robustness against the desktop's real canonical JSON. The receiver must not crash on
 * cross-platform quirks: an explicit `null` in a non-null field (→ schema default), unknown extra
 * fields (→ ignored), and playlists carrying items. A throw here is what surfaced to the user as
 * "merge failed and was rolled back".
 */
class SyncSerializationTest {
    @Test
    fun decodes_desktop_playlist_with_items_and_nulls() {
        val lines =
            listOf(
                // null youtubeId, an unknown "extra" field, and a populated items array.
                """{"syncId":"p1","origin":"local","youtubeId":null,"title":"Gym","description":"","isMusic":false,"isUserCreated":true,"isProtected":false,"createdAtMs":1781000000000,"updatedHlc":"100:0:aaa","deleted":false,"extra":"ignore-me","items":[{"videoId":"v1","position":0,"addedAtMs":1,"deleted":false,"title":"A","channelName":"c","channelId":"uc","thumbnailUrl":"","durationSeconds":212,"isMusic":false}]}""",
                // explicit null in the non-null "description" field must coerce to the default "".
                """{"syncId":"p2","origin":"youtube","youtubeId":"PL123","title":"Chill","description":null,"isMusic":false,"isUserCreated":false,"isProtected":false,"createdAtMs":1781000000001,"updatedHlc":"100:0:aaa","deleted":false,"items":[]}""",
            )

        val decoded = SyncSerialization.decodePlaylists(lines)

        assertEquals(2, decoded.size)
        val p1 = decoded.first { it.syncId == "p1" }
        assertNull(p1.youtubeId)
        assertEquals(1, p1.items.size)
        assertEquals("v1", p1.items.first().videoId)
        assertEquals(212L, p1.items.first().durationSeconds)
        val p2 = decoded.first { it.syncId == "p2" }
        assertEquals("", p2.description) // null coerced to default
        assertEquals("PL123", p2.youtubeId)
        assertTrue(p2.items.isEmpty())
    }

    @Test
    fun gcounter_is_the_bare_device_map_on_the_wire() {
        val brain = CanonicalBrain(counters = BrainCounters(totalInteractions = GCounter(mapOf("dev" to 12L))))
        val line = SyncSerialization.encodeBrain(listOf(brain)).lines.single()

        assertTrue("""a G-Counter must serialize as {"dev":12}""", line.contains(""""totalInteractions":{"dev":12}"""))
        assertFalse("the perDevice wrapper is what the desktop rejects", line.contains("perDevice"))
    }

    @Test
    fun every_brain_snapshot_is_kept_not_just_the_first() {
        // The desktop ships one snapshot per device it knows about; the learned vectors are a mean
        // over those devices, so none may be folded away on decode.
        val a = CanonicalBrain(deviceId = "a", counters = BrainCounters(totalInteractions = GCounter(mapOf("a" to 5L))))
        val b = CanonicalBrain(deviceId = "b", counters = BrainCounters(totalInteractions = GCounter(mapOf("b" to 7L))))
        val lines = SyncSerialization.encodeBrain(listOf(b, a)).lines

        val decoded = SyncSerialization.decodeBrainSnapshots(lines)
        assertEquals(listOf("a", "b"), decoded.map { it.deviceId })
        assertEquals(listOf(5L, 7L), decoded.map { it.counters.totalInteractions.sum() })
    }
}
