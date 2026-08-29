package io.github.aedev.flow.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowMotionTest {
    @Test
    fun `motion durations keep enters slower than exits`() {
        assertTrue(FlowMotion.ExitDurationMillis < FlowMotion.EnterDurationMillis)
        assertTrue(FlowMotion.EnterDurationMillis <= FlowMotion.ContentDurationMillis)
    }

    @Test
    fun `reduced motion removes timed movement`() {
        assertEquals(0, FlowMotion.durationFor(FlowMotion.EnterDurationMillis, reduceMotion = true))
        assertEquals(
            FlowMotion.EnterDurationMillis,
            FlowMotion.durationFor(FlowMotion.EnterDurationMillis, reduceMotion = false),
        )
    }

    @Test
    fun `pressed scale is subtle and reversible`() {
        assertEquals(FlowMotion.PressedScale, FlowMotion.scaleFor(isPressed = true, reduceMotion = false), 0.001f)
        assertEquals(1f, FlowMotion.scaleFor(isPressed = false, reduceMotion = false), 0.001f)
        assertEquals(1f, FlowMotion.scaleFor(isPressed = true, reduceMotion = true), 0.001f)
    }
}
