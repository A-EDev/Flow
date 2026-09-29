package io.github.aedev.flow.innertube.pages.renderer

import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A collaboration's byline, avatar stack and subscribe button all open the same inline dialog, and
 * that dialog is the only place its channels are named: the byline itself links nowhere (#1162).
 */
class CollaboratorDialogTest {
    private val json = Json { ignoreUnknownKeys = true }

    private data class Channel(
        val name: String,
        val id: String,
        val handle: String,
        val subscribers: String,
    )

    private val fern = Channel("fern", "UCODHrzPMGbNv67e84WDZhQQ", "@fern-tv", "5.53M subscribers")
    private val veritasium = Channel("Veritasium", "UCHnyfMqiRRG1u-2MsSQLbXA", "@veritasium", "21.3M subscribers")

    private fun listItem(channel: Channel) =
        """
        { "listItemViewModel": {
          "title": { "content": "${channel.name}" },
          "subtitle": { "content": "‎⁨${channel.handle}⁩ • ⁨${channel.subscribers}⁩" },
          "leadingAccessory": { "avatarViewModel": { "image": { "sources": [
            { "url": "https://yt3.ggpht.com/${channel.name}=s68-c-k-c0x00ffffff-no-rj" } ] } } },
          "rendererContext": { "commandContext": { "onTap": { "innertubeCommand": {
            "browseEndpoint": { "browseId": "${channel.id}" } } } } }
        } }
        """.trimIndent()

    private fun dialog(vararg channels: Channel) =
        """
        { "showDialogCommand": { "panelLoadingStrategy": { "inlineContent": { "dialogViewModel": {
          "header": { "dialogHeaderViewModel": { "headline": { "content": "Collaborators" } } },
          "customContent": { "listViewModel": { "listItems": [ ${channels.joinToString(",") { listItem(it) }} ] } }
        } } } } }
        """.trimIndent()

    private fun avatarStack(vararg channels: Channel) =
        """
        { "avatarStackViewModel": {
          "avatars": [ ${channels.joinToString(",") {
            """{ "avatarViewModel": { "image": { "sources": [ { "url": "https://yt3.ggpht.com/${it.name}=s68" } ] } } }"""
        }} ],
          "rendererContext": { "commandContext": { "onTap": { "innertubeCommand": ${dialog(*channels)} } } }
        } }
        """.trimIndent()

    @Test
    fun `every channel in the dialog is read with its id, avatar and subscriber count`() {
        val collaborators = Json.parseToJsonElement(avatarStack(fern, veritasium)).collaboratorDialog()

        assertEquals(listOf("fern", "Veritasium"), collaborators.map { it.name })
        assertEquals(listOf(fern.id, veritasium.id), collaborators.map { it.channelId })
        assertEquals(listOf("5.53M subscribers", "21.3M subscribers"), collaborators.map { it.subscriberCountText })
        assertTrue(collaborators.all { it.thumbnailUrl.startsWith("https://yt3.ggpht.com/") })
    }

    @Test
    fun `a dialog naming one channel is not a collaboration`() {
        assertEquals(emptyList<Any>(), Json.parseToJsonElement(dialog(fern)).collaboratorDialog())
    }

    @Test
    fun `a row with no channel endpoint is dropped`() {
        val raw = dialog(fern, veritasium).replace(veritasium.id, "not-a-channel")

        assertEquals(emptyList<Any>(), Json.parseToJsonElement(raw).collaboratorDialog())
    }

    @Test
    fun `a collaboration search result opens its lead channel and carries every collaborator`() {
        val raw =
            """
            { "videoRenderer": {
              "videoId": "x32Zq-XvID4",
              "title": { "runs": [ { "text": "A collaboration" } ] },
              "ownerText": { "runs": [ { "text": "Veritasium and fern", "navigationEndpoint": ${dialog(veritasium, fern)} } ] },
              "channelThumbnailSupportedRenderers": { "channelThumbnailWithLinkRenderer": {
                "thumbnail": { "thumbnails": [ { "url": "https://yt3.ggpht.com/Veritasium=s68", "width": 68, "height": 68 } ] } } }
            } }
            """.trimIndent()

        val video = (Json.parseToJsonElement(raw).toFeedItem(FeedItemOwner()) as FeedItem.VideoItem).video

        assertEquals(veritasium.id, video.channelId)
        assertEquals(listOf("Veritasium", "fern"), video.collaborators.map { it.name })
        assertEquals(2, video.channelThumbnailUrls.size)
    }

    @Test
    fun `a collaboration lockup takes its byline and lead channel from the avatar stack`() {
        val raw =
            """
            { "lockupViewModel": {
              "contentId": "fHRS_NOs24w",
              "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
              "metadata": { "lockupMetadataViewModel": {
                "title": { "content": "A collaboration" },
                "image": ${avatarStack(fern, veritasium)},
                "metadata": { "contentMetadataViewModel": { "metadataRows": [
                  { "metadataParts": [ { "text": { "content": "fern and Veritasium" } } ] },
                  { "metadataParts": [ { "text": { "content": "5.4M views" } }, { "text": { "content": "1 year ago" } } ] }
                ] } }
              } }
            } }
            """.trimIndent()

        val video = (Json.parseToJsonElement(raw).toFeedItem(FeedItemOwner()) as FeedItem.VideoItem).video

        assertEquals("fern and Veritasium", video.channelName)
        assertEquals(fern.id, video.channelId)
        assertEquals(listOf(fern.id, veritasium.id), video.collaborators.map { it.channelId })
    }

    @Test
    fun `a related collaboration lockup resolves its channel and collaborators`() {
        val raw =
            """
            {
              "contentId": "fHRS_NOs24w",
              "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
              "metadata": { "lockupMetadataViewModel": {
                "title": { "content": "A collaboration" },
                "image": ${avatarStack(fern, veritasium)},
                "metadata": { "contentMetadataViewModel": { "metadataRows": [
                  { "metadataParts": [ { "text": { "content": "fern and Veritasium" } } ] }
                ] } }
              } }
            }
            """.trimIndent()

        val compact = json.decodeFromString<WatchMetadataResponse.LockupViewModel>(raw).toCompactVideo()!!

        assertEquals(fern.id, compact.channelId())
        assertTrue(compact.channelAvatarUrl!!.contains("fern"))
        assertEquals(listOf("fern", "Veritasium"), compact.collaborators.map { it.name })
    }
}
