package io.github.aedev.flow.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DateDisplayTest {
    @Test
    fun `stored timestamp ages instead of resetting relative text`() {
        val now = 2_000_000_000_000L
        val publicationTimestamp = now - 25L * 60L * 60L * 1000L

        assertEquals(
            publicationTimestamp,
            resolveDisplayUploadTimestamp(
                date = "1 hour ago",
                timestampFallbackMs = publicationTimestamp,
                nowMillis = now
            )
        )
    }

    @Test
    fun `relative text remains a fallback when no timestamp was stored`() {
        val now = 2_000_000_000_000L

        assertEquals(
            now - 60L * 60L * 1000L,
            resolveDisplayUploadTimestamp(
                date = "1 hour ago",
                timestampFallbackMs = 0L,
                nowMillis = now
            )
        )
    }

    @Test
    fun `stored timestamp cannot make an older relative date look newer`() {
        val now = 2_000_000_000_000L

        assertEquals(
            now - 2L * 365L * 24L * 60L * 60L * 1000L,
            resolveDisplayUploadTimestamp(
                date = "2 years ago",
                timestampFallbackMs = now,
                nowMillis = now
            )
        )
    }
}
