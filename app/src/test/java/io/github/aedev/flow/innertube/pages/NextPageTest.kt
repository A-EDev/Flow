package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.innertube.models.response.NextResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class NextPageTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val queueRows =
        checkNotNull(
            json
                .decodeFromString(
                    NextResponse.serializer(),
                    checkNotNull(javaClass.classLoader?.getResource("innertube/next_queen.json")).readText(),
                ).contents.singleColumnMusicWatchNextResultsRenderer
                ?.tabbedRenderer
                ?.watchNextTabbedResultsRenderer
                ?.tabs
                ?.firstOrNull()
                ?.tabRenderer
                ?.content
                ?.musicQueueRenderer
                ?.content
                ?.playlistPanelRenderer
                ?.contents,
        ).mapNotNull { it.playlistPanelVideoRenderer }

    @Test
    fun `the seed queue row carries view and like counts without an album`() {
        val seed = checkNotNull(NextPage.fromPlaylistPanelVideoRenderer(queueRows.first()))
        assertEquals("fJ9rUzIMcZQ", seed.id)
        assertEquals(listOf("Queen"), seed.artists.map { it.name })
        assertNull(seed.album)
        assertEquals("2B views", seed.viewCountText)
        assertEquals("14M likes", seed.likeCountText)
    }
}
