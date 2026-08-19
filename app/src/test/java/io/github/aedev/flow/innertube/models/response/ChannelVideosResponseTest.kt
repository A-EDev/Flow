package io.github.aedev.flow.innertube.models.response

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Shapes taken from a live channel `browse`.
 *
 * The case that matters is the sort switch. Picking Popular or Oldest does not *append* to the grid,
 * it *replaces* it, and YouTube says so with a different action — so a model that only knows about
 * appends parses a re-sorted tab as zero videos and the tab goes blank.
 */
class ChannelVideosResponseTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun lockup(
        id: String,
        title: String,
    ) = """
        { "richItemRenderer": { "content": { "lockupViewModel": {
          "contentId": "$id",
          "metadata": { "lockupMetadataViewModel": { "title": { "content": "$title" } } }
        } } } }
        """.trimIndent()

    // Two actions come back: one carrying the re-rendered chip bar, one carrying the items. Reading
    // only the first would still parse to nothing.
    private val sortSwitch =
        """
        {
          "onResponseReceivedActions": [
            { "reloadContinuationItemsCommand": {
                "targetId": "browse-feedUC",
                "continuationItems": [ { "chipBarViewModel": { "chips": [] } } ]
            } },
            { "reloadContinuationItemsCommand": {
                "targetId": "browse-feedUC",
                "continuationItems": [
                  ${lockup("aaaaaaaaaaa", "Most viewed")},
                  ${lockup("bbbbbbbbbbb", "Second most viewed")},
                  { "continuationItemRenderer": {
                      "continuationEndpoint": { "continuationCommand": { "token": "NEXT_PAGE" } }
                  } }
                ]
            } }
          ]
        }
        """.trimIndent()

    @Test
    fun `a re-sorted tab decodes its items`() {
        val response = json.decodeFromString<ChannelVideosResponse>(sortSwitch)

        val items =
            response.onResponseReceivedActions
                .orEmpty()
                .flatMap {
                    it.appendContinuationItemsAction?.continuationItems.orEmpty() +
                        it.reloadContinuationItemsCommand?.continuationItems.orEmpty()
                }

        val videoIds =
            items.mapNotNull {
                it.richItemRenderer
                    ?.content
                    ?.lockupViewModel
                    ?.contentId
            }
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), videoIds)
    }

    @Test
    fun `a re-sorted tab still exposes its next page`() {
        val response = json.decodeFromString<ChannelVideosResponse>(sortSwitch)

        val token =
            response.onResponseReceivedActions
                .orEmpty()
                .flatMap { it.reloadContinuationItemsCommand?.continuationItems.orEmpty() }
                .firstNotNullOfOrNull {
                    it.continuationItemRenderer
                        ?.continuationEndpoint
                        ?.continuationCommand
                        ?.token
                }

        assertEquals("NEXT_PAGE", token)
    }

    // Paging is the other shape and must keep working unchanged.
    @Test
    fun `an appended page still decodes`() {
        val response =
            json.decodeFromString<ChannelVideosResponse>(
                """
                {
                  "onResponseReceivedActions": [
                    { "appendContinuationItemsAction": { "continuationItems": [
                      ${lockup("ccccccccccc", "Next page item")}
                    ] } }
                  ]
                }
                """.trimIndent(),
            )

        val action = response.onResponseReceivedActions?.single()
        assertNotNull(action?.appendContinuationItemsAction)
        assertNull(action?.reloadContinuationItemsCommand)
        assertEquals(
            "ccccccccccc",
            action
                ?.appendContinuationItemsAction
                ?.continuationItems
                ?.single()
                ?.richItemRenderer
                ?.content
                ?.lockupViewModel
                ?.contentId,
        )
    }
}
