package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

/**
 * The seed a radio is built from is inferred from the queue, so the inference decides whether
 * "Start radio" works at all: the track it seeds from is normally the one already playing, which
 * every "did the user leave their queue" heuristic reads as the session it is meant to replace.
 */
class MusicRadioPlannerTest {
    @Test
    fun `a track from outside the queue opens a new session`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "new",
                queueIds = listOf("new"),
                previousIds = listOf("a", "b", "c"),
                explicitSeedId = null,
            )

        assertThat(context.reseed).isTrue()
        assertThat(context.explicit).isFalse()
        assertThat(context.knownIds).containsExactly("new")
    }

    @Test
    fun `a skip inside the queue keeps the session`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "b",
                queueIds = listOf("a", "b", "c"),
                previousIds = listOf("a", "b", "c"),
                explicitSeedId = null,
            )

        assertThat(context.reseed).isFalse()
    }

    @Test
    fun `the first queue of the process opens a session`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "a",
                queueIds = listOf("a", "b"),
                previousIds = null,
                explicitSeedId = null,
            )

        assertThat(context.reseed).isTrue()
        assertThat(context.explicit).isFalse()
    }

    @Test
    fun `a pruned rebuild does not shrink the known context`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "b",
                queueIds = listOf("b"),
                previousIds = listOf("a", "b", "c"),
                explicitSeedId = null,
            )

        assertThat(context.reseed).isFalse()
        assertThat(context.knownIds).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `an explicit radio reseeds from a track already in the queue`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "c",
                queueIds = listOf("c"),
                previousIds = listOf("a", "b", "c", "d"),
                explicitSeedId = "c",
            )

        assertThat(context.reseed).isTrue()
        assertThat(context.explicit).isTrue()
    }

    @Test
    fun `an explicit radio leaves the old queue behind instead of remembering it`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "c",
                queueIds = listOf("c"),
                previousIds = listOf("a", "b", "c", "d"),
                explicitSeedId = "c",
            )

        assertThat(context.knownIds).containsExactly("c")
    }

    @Test
    fun `an explicit radio reseeds when it is the only track playing`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "a",
                queueIds = listOf("a"),
                previousIds = listOf("a"),
                explicitSeedId = "a",
            )

        assertThat(context.reseed).isTrue()
        assertThat(context.explicit).isTrue()
    }

    @Test
    fun `a stale seed from another track does not hijack this queue change`() {
        val context =
            MusicRadioPlanner.resolveQueueContext(
                currentId = "b",
                queueIds = listOf("a", "b", "c"),
                previousIds = listOf("a", "b", "c"),
                explicitSeedId = "z",
            )

        assertThat(context.reseed).isFalse()
        assertThat(context.explicit).isFalse()
    }

    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = "A", thumbnailUrl = "", duration = 100)

    @Test
    fun `a fresh pool never lists what is already queued`() {
        val pool =
            MusicRadioPlanner.seedPool(
                candidates = listOf(track("a"), track("b"), track("c")),
                currentId = "a",
                queueIds = setOf("b"),
            )

        assertThat(pool.map { it.videoId }).containsExactly("c")
    }

    @Test
    fun `a fresh pool keeps the order it was given`() {
        val pool =
            MusicRadioPlanner.seedPool(
                candidates = listOf(track("c"), track("a"), track("b")),
                currentId = null,
                queueIds = emptySet(),
            )

        assertThat(pool.map { it.videoId }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `growing the pool appends and never reorders what is on screen`() {
        val existing = listOf(track("a"), track("b"))

        val grown = MusicRadioPlanner.growPool(existing, listOf(track("c"), track("a")), null, emptySet())

        assertThat(grown.map { it.videoId }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `growing the pool with nothing new returns the same list`() {
        val existing = listOf(track("a"), track("b"))

        assertThat(MusicRadioPlanner.growPool(existing, listOf(track("a")), null, emptySet())).isSameInstanceAs(existing)
    }

    @Test
    fun `the pool does not grow without bound over a long session`() {
        val existing = List(MusicRadioPlanner.MAX_POOL_SIZE - 2) { track("old$it") }

        val grown = MusicRadioPlanner.growPool(existing, List(20) { track("new$it") }, null, emptySet())

        assertThat(grown).hasSize(MusicRadioPlanner.MAX_POOL_SIZE)
        assertThat(grown.map { it.videoId }.takeLast(2)).containsExactly("new0", "new1").inOrder()
    }

    @Test
    fun `a full pool is left alone`() {
        val existing = List(MusicRadioPlanner.MAX_POOL_SIZE) { track("old$it") }

        assertThat(MusicRadioPlanner.growPool(existing, listOf(track("new")), null, emptySet())).isSameInstanceAs(existing)
    }

    @Test
    fun `the queue takes the head of the list the user is looking at`() {
        val pool = listOf(track("a"), track("b"), track("c"), track("d"))

        val batch = MusicRadioPlanner.nextBatch(pool, queueIds = emptySet(), limit = 2)

        assertThat(batch.map { it.videoId }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `the queue skips pool entries it already holds`() {
        val pool = listOf(track("a"), track("b"), track("c"))

        val batch = MusicRadioPlanner.nextBatch(pool, queueIds = setOf("a"), limit = 2)

        assertThat(batch.map { it.videoId }).containsExactly("b", "c").inOrder()
    }
}
