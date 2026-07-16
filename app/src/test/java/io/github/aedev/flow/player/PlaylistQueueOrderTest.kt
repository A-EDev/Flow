package io.github.aedev.flow.player

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistQueueOrderTest {
    @Test
    fun `next index wraps to start only when loop is enabled`() {
        assertEquals(0, PlaylistQueueOrder.nextIndex(itemCount = 4, currentIndex = 3, loopEnabled = true))
        assertEquals(null, PlaylistQueueOrder.nextIndex(itemCount = 4, currentIndex = 3, loopEnabled = false))
    }

    @Test
    fun `shuffle keeps current item first and every item once`() {
        val items = listOf("a", "b", "c", "d")

        val result = PlaylistQueueOrder.shuffleFromCurrent(items, currentIndex = 2, random = Random(4))

        assertEquals("c", result.items.first())
        assertEquals(items.toSet(), result.items.toSet())
        assertEquals(items.size, result.items.size)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `restore returns original order and current item position`() {
        val original = listOf("a", "b", "c", "d")

        val result = PlaylistQueueOrder.restoreOriginal(
            original = original,
            currentItem = "c",
            keySelector = { it },
        )

        assertEquals(original, result.items)
        assertEquals(2, result.currentIndex)
    }
}
