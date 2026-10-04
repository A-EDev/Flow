package io.github.aedev.flow.ui.components.shared

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryEchoFilterTest {
    @Test
    fun `a late echo of an earlier edit is not applied`() {
        val filter = QueryEchoFilter()
        filter.sent("a")
        filter.sent("ab")

        assertFalse(filter.shouldApply("a"))
        assertFalse(filter.shouldApply("ab"))
    }

    @Test
    fun `a query the field never sent is applied`() {
        val filter = QueryEchoFilter()
        filter.sent("a")

        assertTrue(filter.shouldApply("cats"))
    }

    @Test
    fun `once the caller catches up an earlier text set from outside is applied again`() {
        val filter = QueryEchoFilter()
        filter.sent("c")
        filter.sent("ca")
        filter.sent("")
        assertFalse(filter.shouldApply(""))

        assertTrue(filter.shouldApply("ca"))
    }

    @Test
    fun `typing back to an earlier text is still an echo`() {
        val filter = QueryEchoFilter()
        filter.sent("a")
        filter.sent("ab")
        filter.sent("a")

        assertFalse(filter.shouldApply("a"))
        assertTrue(filter.shouldApply("ab"))
    }
}
