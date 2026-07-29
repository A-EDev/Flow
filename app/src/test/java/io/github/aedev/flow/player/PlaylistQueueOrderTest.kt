package io.github.aedev.flow.player

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

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

        val result =
            PlaylistQueueOrder.restoreOriginal(
                original = original,
                currentItem = "c",
                keySelector = { it },
            )

        assertEquals(original, result.items)
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `remove before current keeps the same item current`() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.removeAt(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 2,
                    index = 0,
                ),
            )

        assertEquals("a", result.removedItem)
        assertEquals(listOf("b", "c", "d"), result.queue.items)
        assertEquals(1, result.queue.currentIndex)
        assertEquals("c", result.queue.items[result.queue.currentIndex])
    }

    @Test
    fun `remove rejects the current item`() {
        val result =
            PlaylistQueueOrder.removeAt(
                items = listOf("a", "b", "c"),
                currentIndex = 1,
                index = 1,
            )

        assertEquals(null, result)
    }

    @Test
    fun `remove matching deletes the selected item from original order`() {
        val original = listOf("a", "b", "c")

        val result =
            PlaylistQueueOrder.removeMatching(
                items = original,
                target = "a",
                keySelector = { it },
            )

        assertEquals(listOf("b", "c"), result)
    }

    @Test
    fun `move current item updates its index`() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.move(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 1,
                    fromIndex = 1,
                    toIndex = 3,
                ),
            )

        assertEquals(listOf("a", "c", "d", "b"), result.items)
        assertEquals(3, result.currentIndex)
        assertEquals("b", result.items[result.currentIndex])
    }

    @Test
    fun `move across current keeps the same item current`() {
        val result =
            requireNotNull(
                PlaylistQueueOrder.move(
                    items = listOf("a", "b", "c", "d"),
                    currentIndex = 2,
                    fromIndex = 0,
                    toIndex = 3,
                ),
            )

        assertEquals(listOf("b", "c", "d", "a"), result.items)
        assertEquals(1, result.currentIndex)
        assertEquals("c", result.items[result.currentIndex])
    }
}
