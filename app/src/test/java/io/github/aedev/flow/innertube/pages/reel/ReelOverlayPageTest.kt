package io.github.aedev.flow.innertube.pages.reel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelOverlayPageTest {
    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject.toReelOverlay()

    @Test
    fun `reads the legacy renderer overlay`() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "likeButton": { "likeButtonRenderer": { "likeCount": 2772, "likeCountText": { "runs": [ { "text": "2.7K" } ] }, "likeStatus": "INDIFFERENT" } },
                    "reelPlayerHeaderSupportedRenderers": { "reelPlayerHeaderRenderer": {
                      "reelTitleText": { "runs": [ { "text": "Custom PC" } ] },
                      "timestampText": { "runs": [ { "text": "15 hours ago" } ] },
                      "channelNavigationEndpoint": { "browseEndpoint": { "browseId": "UCXuqSBlHAE6Xw-yeJA0Tunw" } },
                      "channelTitleText": { "runs": [ { "text": "@LinusTechTips" } ] },
                      "channelThumbnail": { "thumbnails": [ { "url": "https://yt3.test/s48", "width": 48, "height": 48 }, { "url": "https://yt3.test/s176", "width": 176, "height": 176 } ] }
                    } },
                    "viewCommentsButton": { "buttonRenderer": { "text": { "runs": [ { "text": "62" } ] } } },
                    "subscribeButtonRenderer": { "channelId": "UCXuqSBlHAE6Xw-yeJA0Tunw", "subscribed": false },
                    "pivotButton": { "pivotButtonRenderer": { "contentDescription": { "runs": [ { "text": "See more videos using this sound" } ] }, "thumbnail": { "thumbnails": [ { "url": "https://yt3.test/sound", "width": 48, "height": 48 } ] } } }
                } } }
                """.trimIndent(),
            )

        assertNotNull(overlay)
        assertEquals("Custom PC", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertEquals("https://yt3.test/s176", overlay.channelAvatarUrl)
        assertEquals(2772L, overlay.likeCount)
        assertEquals("2.7K", overlay.likeCountText)
        assertEquals(62L, overlay.commentCount)
        assertEquals("15 hours ago", overlay.relativeTimestamp)
        assertEquals("See more videos using this sound", overlay.soundTitle)
        assertEquals("https://yt3.test/sound", overlay.soundThumbnailUrl)
        assertEquals(false, overlay.isSubscribed)
    }

    @Test
    fun `reads the web view-model overlay`() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "reelPlayerHeaderSupportedRenderers": { "reelPlayerHeaderRenderer": { "timestampText": { "simpleText": "2 days ago" } } },
                    "playerOverlay": { "reelPlayerOverlayViewModel": {
                      "metapanel": { "reelMetapanelViewModel": { "metadataItems": [
                        { "reelChannelBarViewModel": {
                            "channelName": { "content": "@LinusTechTips", "commandRuns": [ { "onTap": { "innertubeCommand": { "browseEndpoint": { "browseId": "UCXuqSBlHAE6Xw-yeJA0Tunw", "canonicalBaseUrl": "/@LinusTechTips/shorts" } } } } ] },
                            "decoratedAvatarViewModel": { "decoratedAvatarViewModel": { "avatar": { "avatarViewModel": { "image": { "sources": [ { "url": "https://yt3.test/avatar", "width": 48, "height": 48 } ] } } } } },
                            "subscribeStateEntityStoreKey": "EhhVQ1h1cVNCbEhBRTZYdy15ZUpBMFR1bncgMygB"
                        } },
                        { "shortsVideoTitleViewModel": { "text": { "content": "Steam Frame Wireless" } } },
                        { "reelCarouselViewModel": { "buttonViewModels": [ { "reelCarouselButtonViewModel": { "buttonViewModel": { "buttonViewModel": { "onTap": { "innertubeCommand": { "watchEndpoint": { "videoId": "UYpEQKCWfM4" } } } } } } } ] } }
                      ] } },
                      "actionBar": { "reelActionBarViewModel": { "buttonViewModels": [
                        { "likeButtonViewModel": { "toggleButtonViewModel": { "toggleButtonViewModel": {
                            "defaultButtonViewModel": { "buttonViewModel": { "iconName": "EMPTY_HEART", "title": "2.7K" } },
                            "toggledButtonViewModel": { "buttonViewModel": { "iconName": "FULL_HEART", "title": "2.7K", "accessibilityText": "2,746 likes" } }
                        } } } },
                        { "buttonViewModel": { "iconName": "SHORTS_COMMENT", "title": "62", "accessibilityText": "View 62 comments" } },
                        { "buttonViewModel": { "iconName": "SHORTS_SHARE", "title": "Share" } },
                        { "pivotButtonViewModel": { "soundAttributionTitle": { "content": "Original Sound" }, "thumbnail": { "sources": [ { "url": "https://yt3.test/sound", "width": 48, "height": 48 } ] } } }
                      ] } }
                    } }
                } },
                  "frameworkUpdates": { "entityBatchUpdate": { "mutations": [ { "payload": { "subscriptionStateEntity": { "key": "k", "subscribed": false } } } ] } } }
                """.trimIndent(),
            )

        assertNotNull(overlay)
        assertEquals("Steam Frame Wireless", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertEquals("https://yt3.test/avatar", overlay.channelAvatarUrl)
        assertEquals(2746L, overlay.likeCount)
        assertEquals("2.7K", overlay.likeCountText)
        assertEquals(62L, overlay.commentCount)
        assertEquals("62", overlay.commentCountText)
        assertEquals("2 days ago", overlay.relativeTimestamp)
        assertEquals("Original Sound", overlay.soundTitle)
        assertEquals("UYpEQKCWfM4", overlay.companionVideoId)
        assertEquals(false, overlay.isSubscribed)
    }

    @Test
    fun `a collaboration recovers the channel id from the subscribe entity key`() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": { "playerOverlay": { "reelPlayerOverlayViewModel": {
                    "metapanel": { "reelMetapanelViewModel": { "metadataItems": [
                      { "reelChannelBarViewModel": {
                          "channelName": { "content": "@LinusTechTips and 2 more", "commandRuns": [ { "onTap": { "innertubeCommand": { "showDialogCommand": {} } } } ] },
                          "avatarStackViewModel": { "avatarStackViewModel": { "avatars": [ { "avatarViewModel": { "image": { "sources": [ { "url": "https://yt3.test/first", "width": 48 } ] } } } ] } },
                          "subscribeStateEntityStoreKey": "EhhVQ1h1cVNCbEhBRTZYdy15ZUpBMFR1bncgMygB"
                      } }
                    ] } }
                } } } } }
                """.trimIndent(),
            )

        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay?.channelId)
        assertEquals("@LinusTechTips and 2 more", overlay?.channelName)
        assertEquals("https://yt3.test/first", overlay?.channelAvatarUrl)
    }

    @Test
    fun `an elements overlay is unreadable`() {
        val overlay =
            parse(
                """
                { "overlay": { "reelPlayerOverlayRenderer": {
                    "style": "REEL_PLAYER_OVERLAY_STYLE_SHORTS_RIGHT_SIDE",
                    "playerOverlay": { "elementRenderer": { "newElement": {} } }
                } } }
                """.trimIndent(),
            )

        assertNull(overlay)
    }

    @Test
    fun `web collaboration capture`() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_WEB_COLLAB).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertTrue(overlay.channelName.orEmpty().startsWith("@LinusTechTips"))
        assertTrue((overlay.likeCount ?: 0) > 2_000)
        assertTrue((overlay.commentCount ?: 0) >= 62)
        assertEquals("Original Sound", overlay.soundTitle)
        assertNotNull(overlay.relativeTimestamp)
        assertNotNull(overlay.companionVideoId)
    }

    @Test
    fun `web single creator capture`() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_WEB_SINGLE).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay!!.channelId)
        assertEquals("@LinusTechTips", overlay.channelName)
        assertNotNull(overlay.channelAvatarUrl)
        assertNotNull(overlay.title)
    }

    @Test
    fun `mweb capture`() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_MWEB).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertTrue((overlay.likeCount ?: 0) > 2_000)
    }

    @Test
    fun `visionos capture is the legacy shape with an exact like count`() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_VISIONOS).toReelOverlay()

        assertNotNull(overlay)
        assertEquals("Custom PC for DisguisedToast", overlay!!.title)
        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", overlay.channelId)
        assertTrue((overlay.likeCount ?: 0) > 2_000)
        assertTrue((overlay.commentCount ?: 0) >= 62)
        assertNotNull(overlay.channelAvatarUrl)
    }

    @Test
    fun `android vr capture adds the sound pivot`() {
        val overlay = ReelFixture(ReelFixture.ITEM_WATCH_ANDROID_VR).toReelOverlay()

        assertNotNull(overlay?.soundTitle)
        assertNotNull(overlay?.soundThumbnailUrl)
    }

    @Test
    fun `android capture is an elements template and yields nothing`() {
        assertNull(ReelFixture(ReelFixture.ITEM_WATCH_ANDROID_ELEMENTS).toReelOverlay())
    }
}
