package io.github.aedev.flow.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FlowShowcaseComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun featuredVideoCommunicatesItsFeaturedState() {
        composeRule.setContent {
            MaterialTheme {
                FlowFeaturedVideoCard(
                    video = showcaseVideo,
                    onClick = {},
                )
            }
        }

        composeRule.onNodeWithTag("flow_featured_video").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.label_featured)).assertExists()
    }

    @Test
    fun featuredVideoReportsItsPrimaryAction() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                FlowFeaturedVideoCard(
                    video = showcaseVideo,
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithTag("flow_featured_video").performClick()

        assertTrue(clicked)
    }

    @Test
    fun featuredVideoKeepsChannelNavigationAvailable() {
        var channelClicked = false
        composeRule.setContent {
            MaterialTheme {
                FlowFeaturedVideoCard(
                    video = showcaseVideo,
                    onClick = {},
                    onChannelClick = { channelId -> channelClicked = channelId == "flow-studio" },
                )
            }
        }

        composeRule.onNodeWithText(showcaseVideo.channelName).performClick()

        assertTrue(channelClicked)
    }

    private companion object {
        val showcaseVideo =
            Video(
                id = "showcase-video",
                title = "A calmer way to watch",
                channelName = "Flow Studio",
                channelId = "flow-studio",
                thumbnailUrl = "https://example.com/showcase.jpg",
                duration = 245,
                viewCount = 1_000L,
                uploadDate = "Today",
            )
    }
}
