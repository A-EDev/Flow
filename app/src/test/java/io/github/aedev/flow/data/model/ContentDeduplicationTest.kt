package io.github.aedev.flow.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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

    @Test
    fun `merge keeps existing items and adds only new stable keys`() {
        val existing = listOf(
            TestItem(id = "first", value = "existing"),
            TestItem(id = "second", value = "existing")
        )
        val incoming = listOf(
            TestItem(id = "second", value = "duplicate"),
            TestItem(id = "third", value = "new")
        )

        val result = existing.mergeDistinctByNonBlankKey(incoming, TestItem::id)

        assertEquals(
            listOf(
                TestItem(id = "first", value = "existing"),
                TestItem(id = "second", value = "existing"),
                TestItem(id = "third", value = "new")
            ),
            result
        )
    }

    @Test
    fun `or-self normalization preserves list identity when already valid`() {
        val valid = listOf(
            TestItem(id = "first", value = "one"),
            TestItem(id = "second", value = "two")
        )
        val duplicated = valid + TestItem(id = "first", value = "duplicate")

        assertSame(valid, valid.distinctByNonBlankKeyOrSelf(TestItem::id))
        assertNotSame(duplicated, duplicated.distinctByNonBlankKeyOrSelf(TestItem::id))
    }

    @Test
    fun `tracker removes duplicates within and across paging loads`() {
        val tracker = DistinctKeyTracker()

        val firstPage = tracker.filter(
            listOf(
                TestItem(id = "first", value = "original"),
                TestItem(id = "first", value = "same-page duplicate"),
                TestItem(id = "second", value = "original")
            ),
            TestItem::id
        )
        val secondPage = tracker.filter(
            listOf(
                TestItem(id = "second", value = "previous-page duplicate"),
                TestItem(id = "third", value = "new")
            ),
            TestItem::id
        )

        assertEquals(listOf("first", "second"), firstPage.map(TestItem::id))
        assertEquals(listOf("third"), secondPage.map(TestItem::id))
    }

    private data class TestItem(
        val id: String,
        val value: String
    )
}
