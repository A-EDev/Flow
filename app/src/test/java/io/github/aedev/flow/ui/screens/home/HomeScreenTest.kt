package io.github.aedev.flow.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    @Test
    fun shortsShelfIndexCountsTheFeaturedVideo() {
        assertEquals(0, featuredFeedShortsInsertionIndex(shortsShelfAfterIndex = 1, videoCount = 4))
        assertEquals(1, featuredFeedShortsInsertionIndex(shortsShelfAfterIndex = 2, videoCount = 4))
        assertEquals(3, featuredFeedShortsInsertionIndex(shortsShelfAfterIndex = 4, videoCount = 4))
    }
}
