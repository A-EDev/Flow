package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowMotionTest {
    @Test
    fun `motion durations keep enters slower than exits`() {
        assertTrue(FlowMotion.EXIT_DURATION_MILLIS < FlowMotion.ENTER_DURATION_MILLIS)
        assertTrue(FlowMotion.ENTER_DURATION_MILLIS <= FlowMotion.CONTENT_DURATION_MILLIS)
    }

    @Test
    fun `reduced motion removes timed movement`() {
        assertEquals(0, FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion = true))
        assertEquals(
            FlowMotion.ENTER_DURATION_MILLIS,
            FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion = false),
        )
    }

    @Test
    fun `pressed scale is subtle and reversible`() {
        assertEquals(FlowMotion.PRESSED_SCALE, FlowMotion.scaleFor(isPressed = true, reduceMotion = false), 0.001f)
        assertEquals(1f, FlowMotion.scaleFor(isPressed = false, reduceMotion = false), 0.001f)
        assertEquals(1f, FlowMotion.scaleFor(isPressed = true, reduceMotion = true), 0.001f)
    }
}
