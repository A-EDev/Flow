package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Test

class PrerollWaitTest {
    private fun placements(json: String): JsonArray = Json.parseToJsonElement(json) as JsonArray

    private fun placement(
        kind: String,
        skipOffset: String?,
    ): String =
        """
        {"adPlacementRenderer":{"config":{"adPlacementConfig":{"kind":"$kind"}},
        "renderer":{"instreamVideoAdRenderer":{${skipOffset?.let { "\"skipOffsetMilliseconds\":$it" }.orEmpty()}}}}}
        """.trimIndent()

    @Test
    fun `a start ad holds the streams for most of its skip offset`() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "5000")}]"))).isEqualTo(4_000L)
    }

    @Test
    fun `a skip offset sent as a string is read the same`() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "\"5000\"")}]"))).isEqualTo(4_000L)
    }

    @Test
    fun `mid-roll and end ads do not hold the start of the video`() {
        val json = "[${placement("AD_PLACEMENT_KIND_MILLISECONDS", "5000")},${placement("AD_PLACEMENT_KIND_END", "5000")}]"

        assertThat(PrerollWait.of(placements(json))).isEqualTo(0L)
    }

    @Test
    fun `a start ad with no skip offset waits the longest, and nothing waits longer`() {
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", null)}]"))).isEqualTo(PrerollWait.MAX_WAIT_MS)
        assertThat(PrerollWait.of(placements("[${placement("AD_PLACEMENT_KIND_START", "60000")}]"))).isEqualTo(PrerollWait.MAX_WAIT_MS)
    }

    @Test
    fun `no ads, or a shape it does not know, means no wait`() {
        assertThat(PrerollWait.of(null)).isEqualTo(0L)
        assertThat(PrerollWait.of(placements("[1,\"x\",{}]"))).isEqualTo(0L)
    }
}
