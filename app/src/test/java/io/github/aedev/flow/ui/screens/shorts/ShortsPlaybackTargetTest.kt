package io.github.aedev.flow.ui.screens.shorts

import io.github.aedev.flow.data.local.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The target height is part of the playback-stream cache key, so the pager and the ViewModel's
 * prefetch have to agree on it exactly. These pin the shared derivation both now use; if they ever
 * compute it separately again, a prefetch silently resolves one entry and the pager misses it.
 */
class ShortsPlaybackTargetTest {
    @Test
    fun `wifi and cellular select their own configured quality`() {
        assertEquals(
            1080,
            shortsTargetHeight(isWifi = true, wifiQuality = VideoQuality.Q_1080P, cellularQuality = VideoQuality.Q_480P),
        )
        assertEquals(
            480,
            shortsTargetHeight(isWifi = false, wifiQuality = VideoQuality.Q_1080P, cellularQuality = VideoQuality.Q_480P),
        )
    }

    @Test
    fun `the transport is what decides, not which quality is higher`() {
        val onCellular =
            shortsTargetHeight(isWifi = false, wifiQuality = VideoQuality.Q_360P, cellularQuality = VideoQuality.Q_1080P)

        assertEquals(1080, onCellular)
    }

    @Test
    fun `auto resolves to the unconstrained height both paths agree on`() {
        val wifi = shortsTargetHeight(isWifi = true, wifiQuality = VideoQuality.AUTO, cellularQuality = VideoQuality.Q_480P)

        // 0 means "no cap" downstream; the point here is that it is a single stable value rather
        // than each caller substituting its own placeholder.
        assertEquals(VideoQuality.AUTO.height, wifi)
    }
}
