package io.github.aedev.flow.data.shorts

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsContentFilterTest {
    @Test
    fun `reports enabled when the master switch is on`() =
        runTest {
            assertTrue(ShortsContentFilter(flowOf(true)).isEnabled())
        }

    @Test
    fun `reports disabled when the master switch is off`() =
        runTest {
            assertFalse(ShortsContentFilter(flowOf(false)).isEnabled())
        }
}
