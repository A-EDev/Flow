package io.github.aedev.flow.ui.screens.playlists

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.RemotePlaylistPage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedPlaylistTest {
    private fun video(id: String) =
        Video(
            id = id,
            title = id,
            channelName = "",
            channelId = "",
            thumbnailUrl = "",
            duration = 0,
            viewCount = 0L,
            uploadDate = "",
        )

    private fun page(
        ids: List<String>,
        continuation: String?,
    ) = RemotePlaylistPage(
        title = "Mix tape",
        ownerName = null,
        ownerId = null,
        description = "",
        thumbnailUrl = "",
        videos = ids.map(::video),
        continuation = continuation,
    )

    @Test
    fun `the linked video starts the queue at its own position`() =
        runTest {
            val queue = findLinkedVideoQueue(page(listOf("a", "b", "c"), continuation = null), "b") { error("no second page") }

            assertEquals(listOf("a", "b", "c"), queue?.items?.map(Video::id))
            assertEquals(1, queue?.startIndex)
            assertEquals("Mix tape", queue?.title)
        }

    @Test
    fun `later pages are read until the linked video turns up`() =
        runTest {
            val pages = mapOf("p2" to (listOf(video("c"), video("d")) to "p3"), "p3" to (listOf(video("e")) to null))
            var requests = 0

            val queue =
                findLinkedVideoQueue(page(listOf("a", "b"), continuation = "p2"), "d") { token ->
                    requests++
                    pages[token]
                }

            assertEquals(listOf("a", "b", "c", "d"), queue?.items?.map(Video::id))
            assertEquals(3, queue?.startIndex)
            assertEquals(1, requests)
        }

    @Test
    fun `a video missing from the playlist plays alone`() =
        runTest {
            assertNull(findLinkedVideoQueue(page(listOf("a"), continuation = null), "z") { null })
            assertNull(findLinkedVideoQueue(page(listOf("a"), continuation = "p2"), "z") { null })
            assertNull(findLinkedVideoQueue(page(listOf("a"), continuation = "p2"), "z") { emptyList<Video>() to "p3" })
        }

    @Test
    fun `the search stops after its page budget`() =
        runTest {
            var requests = 0
            val queue =
                findLinkedVideoQueue(page(listOf("a"), continuation = "next"), "z", maxPages = 3) {
                    requests++
                    listOf(video("x$requests")) to "next"
                }

            assertNull(queue)
            assertEquals(2, requests)
        }

    @Test
    fun `a song is found by its own id`() {
        val queue = linkedQueue(listOf("a", "b"), "b", title = null) { it }

        assertEquals(1, queue?.startIndex)
        assertNull(linkedQueue(listOf("a"), "b", title = null) { it })
    }
}
