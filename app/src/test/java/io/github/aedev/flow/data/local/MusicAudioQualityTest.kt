package io.github.aedev.flow.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicAudioQualityTest {
    @Test
    fun `auto streams high quality on wifi and medium on mobile data`() {
        assertEquals(MusicAudioQuality.HIGH, MusicAudioQuality.AUTO.resolve(onWifi = true))
        assertEquals(MusicAudioQuality.MEDIUM, MusicAudioQuality.AUTO.resolve(onWifi = false))
    }

    @Test
    fun `a fixed choice ignores the network`() {
        listOf(MusicAudioQuality.HIGH, MusicAudioQuality.MEDIUM, MusicAudioQuality.LOW).forEach { quality ->
            assertEquals(quality, quality.resolve(onWifi = true))
            assertEquals(quality, quality.resolve(onWifi = false))
        }
    }
}
