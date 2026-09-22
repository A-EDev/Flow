package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
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
}
