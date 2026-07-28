package io.github.aedev.flow.ui.screens.player.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

/**
 * Covers the pure functions the ambient pipeline depends on: the linear-light conversions, the
 * blur, the change detector, and the temporal filter. Each replaced something that used to be done
 * elsewhere — the blur by a full-screen `Modifier.blur`, the smoothing by three Compose animations —
 * so between them they carry the visual behaviour.
 */
class VideoAmbientBackgroundTest {

    private fun grid(cells: Int, r: Float, g: Float, b: Float) =
        FloatArray(cells * 3) { i -> when (i % 3) { 0 -> r; 1 -> g; else -> b } }

    // ---- linear light ----

    @Test
    fun `sRGB round trips through linear`() {
        for (v in 0..255) {
            assertThat(linearToSrgb(srgbToLinear(v))).isEqualTo(v)
        }
    }

    @Test
    fun `linear conversion anchors at black and white`() {
        assertThat(srgbToLinear(0)).isEqualTo(0f)
        assertThat(srgbToLinear(255)).isEqualTo(1f)
        assertThat(linearToSrgb(0f)).isEqualTo(0)
        assertThat(linearToSrgb(1f)).isEqualTo(255)
    }

    @Test
    fun `mid sRGB is far below mid linear`() {
        // The whole reason the spatial path moved to linear: 128/255 is ~0.216 of the light, not
        // half of it. Averaging in sRGB is what made blurred boundaries go muddy.
        assertThat(srgbToLinear(128)).isWithin(0.005f).of(0.2158f)
    }

    @Test
    fun `averaging saturated complementaries stays bright in linear`() {
        // sRGB-averaging pure red and pure green gives (128,128,0) — darker than either input.
        // In linear the same average encodes back to ~188.
        val mixed = linearToSrgb((srgbToLinear(255) + srgbToLinear(0)) / 2f)
        assertThat(mixed).isAtLeast(185)
        assertThat(mixed).isAtMost(190)
    }

    @Test
    fun `near black keeps distinct steps`() {
        // The 4096-entry table exists for this: the curve's slope is 12.92 here, so a coarser LUT
        // would collapse adjacent dim values and band a dim glow.
        val distinct = (0..8).map { linearToSrgb(srgbToLinear(it)) }.distinct()
        assertThat(distinct).hasSize(9)
    }

    // ---- blur ----

    @Test
    fun `blur leaves a uniform grid unchanged`() {
        val buf = grid(8 * 8, 0.25f, 0.5f, 0.75f)
        val expected = buf.copyOf()

        boxBlurLinear(buf, FloatArray(buf.size), 8, 8, 2, 3)

        // Edge clamping must not darken the border, which is what zero padding would do.
        for (i in buf.indices) assertThat(buf[i]).isWithin(1e-5f).of(expected[i])
    }

    @Test
    fun `blur turns a hard edge into a gradient`() {
        val w = 16
        val h = 4
        val buf = FloatArray(w * h * 3)
        for (cell in 0 until w * h) {
            val v = if (cell % w < w / 2) 0f else 1f
            buf[cell * 3] = v; buf[cell * 3 + 1] = v; buf[cell * 3 + 2] = v
        }

        boxBlurLinear(buf, FloatArray(buf.size), w, h, 2, 3)

        val row = (0 until w).map { buf[it * 3] }
        assertThat(row.zipWithNext().all { (a, b) -> a <= b + 1e-6f }).isTrue()
        assertThat(row.any { it > 0.01f && it < 0.99f }).isTrue()
    }

    @Test
    fun `blur preserves total light`() {
        val w = 12
        val h = 12
        val buf = FloatArray(w * h * 3) { (it % 17) / 17f }
        val before = buf.sum()

        boxBlurLinear(buf, FloatArray(buf.size), w, h, 2, 3)

        assertThat(buf.sum()).isWithin(before * 0.02f).of(before)
    }

    @Test
    fun `blur is a no-op for a zero radius`() {
        val buf = FloatArray(48) { it / 48f }
        val copy = buf.copyOf()

        boxBlurLinear(buf, FloatArray(buf.size), 4, 4, 0, 3)

        assertThat(buf).isEqualTo(copy)
    }

    // ---- temporal filter ----

    @Test
    fun `filter converges toward the target and then reports no movement`() {
        val current = floatArrayOf(0f, 0f, 0f)
        val target = floatArrayOf(1f, 0.5f, 0.25f)
        val alpha = 1f - exp(-60f / 600f)

        var iterations = 0
        while (step(current, target, alpha) && iterations < 500) iterations++

        assertThat(iterations).isLessThan(500)
        assertThat(step(current, target, alpha)).isFalse()
        for (i in target.indices) assertThat(current[i]).isWithin(0.01f).of(target[i])
    }

    @Test
    fun `filter snaps the residual so convergence is exact`() {
        val current = floatArrayOf(0.5f)
        val target = floatArrayOf(0.5f + 0.0001f)

        // Inside the epsilon the value is taken outright, so it cannot creep forever.
        assertThat(step(current, target, 0.1f)).isFalse()
        assertThat(current[0]).isEqualTo(target[0])
    }

    @Test
    fun `filter is frame rate independent`() {
        val tau = 600f
        val slow = floatArrayOf(0f)
        val fast = floatArrayOf(0f)
        val target = floatArrayOf(1f)

        // 600 ms of smoothing, reached in 10 ticks of 60 ms versus 60 ticks of 10 ms.
        repeat(10) { step(slow, target, 1f - exp(-60f / tau)) }
        repeat(60) { step(fast, target, 1f - exp(-10f / tau)) }

        assertThat(abs(slow[0] - fast[0])).isLessThan(0.01f)
        // One tau of a first-order filter covers 1 - 1/e of the distance.
        assertThat(slow[0]).isWithin(0.02f).of(1f - 1f / Math.E.toFloat())
    }

    @Test
    fun `filter attenuates a three hertz square wave`() {
        // WCAG 2.3.1 allows no more than three flashes per second. tau = 600 ms must keep a 3 Hz
        // black-white input well away from reproducing that swing on screen.
        val current = floatArrayOf(0f)
        val alpha = 1f - exp(-10f / 600f)
        var min = 1f
        var max = 0f
        var target = 1f
        // 6 half-cycles per second at 3 Hz; run 3 s at a 10 ms tick, sampling the last second.
        repeat(300) { tick ->
            if (tick % 17 == 0) target = if (target > 0.5f) 0f else 1f
            step(current, floatArrayOf(target), alpha)
            if (tick > 200) {
                if (current[0] < min) min = current[0]
                if (current[0] > max) max = current[0]
            }
        }
        // Output swing must be a small fraction of the input's full 0..1 swing.
        assertThat(max - min).isLessThan(0.25f)
    }

    // ---- change detection ----

    @Test
    fun `identical frames report no difference`() {
        val a = IntArray(96 * 54) { (0xFF shl 24) or ((it % 256) shl 16) }
        assertThat(meanAbsDiff(a, a.copyOf())).isEqualTo(0)
    }

    @Test
    fun `black against white is the maximum difference`() {
        val black = IntArray(96 * 54)
        val white = IntArray(96 * 54) { (0xFF shl 24) or 0xFFFFFF }
        assertThat(meanAbsDiff(black, white)).isEqualTo(255)
    }

    @Test
    fun `imperceptible drift stays under the update threshold`() {
        val a = IntArray(96 * 54) { (0xFF shl 24) or (100 shl 16) or (100 shl 8) or 100 }
        val b = IntArray(96 * 54) { (0xFF shl 24) or (101 shl 16) or (101 shl 8) or 101 }
        assertThat(meanAbsDiff(a, b)).isLessThan(FRAME_CHANGE_THRESHOLD)
    }

    @Test
    fun `a scene change clears the update threshold`() {
        val a = IntArray(96 * 54) { (0xFF shl 24) or (20 shl 16) or (30 shl 8) or 40 }
        val b = IntArray(96 * 54) { (0xFF shl 24) or (180 shl 16) or (90 shl 8) or 60 }
        assertThat(meanAbsDiff(a, b)).isAtLeast(FRAME_CHANGE_THRESHOLD)
    }
}
