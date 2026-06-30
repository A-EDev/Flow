package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.canonical.CanonicalBrain
import io.github.aedev.flow.sync.canonical.CanonicalBrainVectors
import io.github.aedev.flow.sync.canonical.CanonicalLike
import io.github.aedev.flow.sync.canonical.CanonicalPlaylist
import io.github.aedev.flow.sync.canonical.CanonicalPlaylistItem
import io.github.aedev.flow.sync.canonical.CanonicalSetting
import io.github.aedev.flow.sync.canonical.CanonicalSubscriptionGroup
import io.github.aedev.flow.sync.canonical.CanonicalVector
import io.github.aedev.flow.sync.canonical.CanonicalWatchHistory
import io.github.aedev.flow.sync.canonical.GCounter
import io.github.aedev.flow.sync.identity.Hlc
import io.github.aedev.flow.sync.merge.BrainMerger
import io.github.aedev.flow.sync.merge.LikesMerger
import io.github.aedev.flow.sync.merge.PlaylistMerger
import io.github.aedev.flow.sync.merge.SettingsMerger
import io.github.aedev.flow.sync.merge.SubscriptionsMerger
import io.github.aedev.flow.sync.merge.WatchHistoryMerger
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Convergence proofs for the merge engine: every collection merge must be
 * commutative (a⊕b == b⊕a), associative ((a⊕b)⊕c == a⊕(b⊕c)), and idempotent (m⊕m == m). These
 * three properties are the formal backbone of "no conflict / no loss / no double-count".
 */
class MergeConvergenceTest {

    private fun hlc(ms: Long, c: Int, node: String) = Hlc(ms, c, node).encode()

    private fun <T> laws(name: String, a: List<T>, b: List<T>, c: List<T>, merge: (List<T>, List<T>) -> List<T>) {
        assertEquals("$name: commutativity", merge(a, b), merge(b, a))
        assertEquals("$name: associativity", merge(merge(a, b), c), merge(a, merge(b, c)))
        val ab = merge(a, b)
        assertEquals("$name: idempotency", ab, merge(ab, ab))
    }

    @Test
    fun watch_history_converges() {
        val a = listOf(
            CanonicalWatchHistory("v1", title = "A", watchedAtMs = 100, progress = 0.5, hlc = hlc(100, 0, "aaa")),
            CanonicalWatchHistory("v2", watchedAtMs = 50, progress = 0.2, isMusic = true, hlc = hlc(50, 0, "aaa")),
        )
        val b = listOf(
            CanonicalWatchHistory("v1", title = "A2", watchedAtMs = 200, progress = 0.9, hlc = hlc(200, 0, "bbb")),
            CanonicalWatchHistory("v3", watchedAtMs = 70, hlc = hlc(70, 0, "bbb")),
        )
        val c = listOf(
            CanonicalWatchHistory("v2", watchedAtMs = 60, progress = 0.8, hlc = hlc(60, 0, "ccc")),
            CanonicalWatchHistory("v1", deleted = true, hlc = hlc(300, 0, "ccc")),
        )
        laws("watch_history", a, b, c, WatchHistoryMerger::merge)

        // Specific guarantees: progress = max, isMusic OR is sticky.
        val merged = WatchHistoryMerger.merge(a, b)
        assertEquals(0.9, merged.first { it.videoId == "v1" }.progress, 0.0)
    }

    @Test
    fun likes_converge() {
        val a = listOf(CanonicalLike("video", "x", "liked", updatedAtMs = 100, hlc = hlc(100, 0, "a")))
        val b = listOf(CanonicalLike("video", "x", "none", updatedAtMs = 200, hlc = hlc(200, 0, "b")))
        val c = listOf(CanonicalLike("music", "y", "disliked", updatedAtMs = 150, hlc = hlc(150, 0, "c")))
        laws("likes", a, b, c, LikesMerger::merge)
        // Later unlike (state=none, higher HLC) wins.
        assertEquals("none", LikesMerger.merge(a, b).first().state)
    }

    @Test
    fun settings_converge() {
        val a = listOf(CanonicalSetting("autoplay", JsonPrimitive(true), hlc(100, 0, "a")))
        val b = listOf(CanonicalSetting("autoplay", JsonPrimitive(false), hlc(200, 0, "b")))
        val c = listOf(CanonicalSetting("playback_speed", JsonPrimitive(1.5f), hlc(50, 0, "c")))
        laws("settings", a, b, c, SettingsMerger::merge)
        assertEquals(JsonPrimitive(false), SettingsMerger.merge(a, b).first { it.key == "autoplay" }.value)
    }

    @Test
    fun subscriptions_converge() {
        val a = listOf(CanonicalSubscriptionGroup("Tech", listOf("UC1", "UC2"), 0, hlc(100, 0, "a")))
        val b = listOf(CanonicalSubscriptionGroup("Tech", listOf("UC2", "UC3"), 1, hlc(200, 0, "b")))
        val c = listOf(CanonicalSubscriptionGroup("Music", listOf("UC9"), 0, hlc(50, 0, "c")))
        laws("subscriptions", a, b, c, SubscriptionsMerger::merge)
        // channelIds union.
        assertEquals(listOf("UC1", "UC2", "UC3"), SubscriptionsMerger.merge(a, b).first { it.name == "Tech" }.channelIds)
    }

    @Test
    fun playlists_converge() {
        fun item(id: String, pos: Long, hlcStr: String) = CanonicalPlaylistItem(videoId = id, position = pos, hlc = hlcStr)
        val a = listOf(
            CanonicalPlaylist(
                syncId = "p1", title = "Gym", updatedHlc = hlc(100, 0, "a"),
                items = listOf(item("v1", 0, hlc(100, 0, "a")), item("v2", 1, hlc(100, 0, "a"))),
            )
        )
        val b = listOf(
            CanonicalPlaylist(
                syncId = "p1", title = "Gym Mix", updatedHlc = hlc(200, 0, "b"),
                items = listOf(item("v2", 0, hlc(200, 0, "b")), item("v3", 1, hlc(200, 0, "b"))),
            )
        )
        val c = listOf(
            CanonicalPlaylist(syncId = "p2", title = "Chill", updatedHlc = hlc(50, 0, "c"), items = listOf(item("v9", 0, hlc(50, 0, "c")))),
        )
        laws("playlists", a, b, c, PlaylistMerger::merge)
        // Metadata LWW: higher HLC title wins; items union.
        val p1 = PlaylistMerger.merge(a, b).first { it.syncId == "p1" }
        assertEquals("Gym Mix", p1.title)
        assertEquals(setOf("v1", "v2", "v3"), p1.items.map { it.videoId }.toSet())
    }

    @Test
    fun brain_converges() {
        fun brain(node: String, topics: Map<String, Double>, idf: Long, blocked: Set<String>) = CanonicalBrain(
            deviceId = node,
            hlc = hlc(idf, 0, node),
            vectors = CanonicalBrainVectors(globalVector = CanonicalVector(topics = topics)),
            idfTotalDocuments = GCounter(mapOf(node to idf)),
            blockedTopics = blocked,
        )
        val a = brain("a", mapOf("kotlin" to 0.8, "rust" to 0.3), 1000, setOf("politics"))
        val b = brain("b", mapOf("kotlin" to 0.5, "go" to 0.6), 500, setOf("spam"))
        val c = brain("c", mapOf("rust" to 0.9), 200, setOf("politics"))

        assertEquals(BrainMerger.merge(a, b), BrainMerger.merge(b, a).copy(deviceId = "a"))
        val abc1 = BrainMerger.merge(BrainMerger.merge(a, b), c)
        val abc2 = BrainMerger.merge(a, BrainMerger.merge(b, c))
        assertEquals(abc1, abc2)
        val ab = BrainMerger.merge(a, b)
        assertEquals(ab, BrainMerger.merge(ab, ab))

        // G-Counter sums experience; vectors keep the stronger signal; blocklists union.
        assertEquals(1500L, ab.idfTotalDocuments.sum())
        assertEquals(0.8, ab.vectors.globalVector.topics["kotlin"]!!, 0.0)
        assertEquals(setOf("politics", "spam"), ab.blockedTopics)
    }
}
