package io.github.aedev.flow.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentDeduplicationTest {

    @Test
    fun `keeps the first item for each stable key in source order`() {
        val items = listOf(
            TestItem(id = "first", value = "original"),
            TestItem(id = "second", value = "other"),
            TestItem(id = "first", value = "duplicate")
        )

        val result = items.distinctByNonBlankKey(TestItem::id)

        assertEquals(
            listOf(
                TestItem(id = "first", value = "original"),
                TestItem(id = "second", value = "other")
            ),
            result
        )
    }

    @Test
    fun `drops items that cannot provide a stable key`() {
        val items = listOf(
            TestItem(id = "", value = "empty"),
            TestItem(id = "   ", value = "blank"),
            TestItem(id = "valid", value = "kept")
        )

        val result = items.distinctByNonBlankKey(TestItem::id)

        assertEquals(listOf(TestItem(id = "valid", value = "kept")), result)
    }

    private data class TestItem(
        val id: String,
        val value: String
    )
}
