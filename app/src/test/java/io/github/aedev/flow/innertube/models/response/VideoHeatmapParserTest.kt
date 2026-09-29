package io.github.aedev.flow.innertube.models.response

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Test

/**
 * Parsed from a captured watch response. The heatmap is decoration that YouTube has already moved
 * once — from `heatmapRenderer` to an entity mutation — so these cases are mostly about a shape
 * change hiding the graph rather than breaking the watch page.
 */
class VideoHeatmapParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture() =
        json.parseToJsonElement(
            javaClass.classLoader!!
                .getResourceAsStream("watch_next_heatmap.json")!!
                .bufferedReader()
                .use { it.readText() },
        )

    @Test
    fun `the rewatch curve is read from the entity mutation`() {
        val heatmap = VideoHeatmapParser.parse(fixture())

        assertThat(heatmap).isNotNull()
        assertThat(heatmap!!.markers).hasSize(100)
    }

    @Test
    fun `marker times arrive as strings and intensity as a number`() {
        val markers = VideoHeatmapParser.parse(fixture())!!.markers

        assertThat(markers.first().startMs).isEqualTo(0L)
        assertThat(markers.first().durationMs).isEqualTo(2_140L)
        assertThat(markers.first().intensity).isEqualTo(1f)
        assertThat(markers[1].intensity).isWithin(0.0001f).of(0.42176764f)
    }

    @Test
    fun `markers run in order and cover the video`() {
        val markers = VideoHeatmapParser.parse(fixture())!!.markers

        assertThat(markers.map { it.startMs }).isInOrder()
        assertThat(markers.last().startMs).isEqualTo(211_860L)
    }

    @Test
    fun `intensity is clamped to the zero-to-one range`() {
        val markers = VideoHeatmapParser.parse(fixture())!!.markers

        assertThat(markers.all { it.intensity in 0f..1f }).isTrue()
    }

    @Test
    fun `the most replayed highlight is read with its label`() {
        val highlights = VideoHeatmapParser.parse(fixture())!!.highlights

        assertThat(highlights).hasSize(1)
        val highlight = highlights.single()
        assertThat(highlight.label).isEqualTo("Most replayed")
        assertThat(highlight.startMs).isEqualTo(0L)
        assertThat(highlight.endMs).isEqualTo(6_420L)
        assertThat(highlight.markerMs).isEqualTo(2_140L)
    }

    @Test
    fun `a response with no heatmap entity yields null`() {
        val noHeatmap =
            json.parseToJsonElement(
                """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"commentEntityPayload":{}}}]}}}""",
            )

        assertThat(VideoHeatmapParser.parse(noHeatmap)).isNull()
    }

    @Test
    fun `a marker list of another type is ignored`() {
        val chapters =
            json.parseToJsonElement(
                """
                {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":
                {"markersList":{"markerType":"MARKER_TYPE_CHAPTERS","markers":[
                {"startMillis":"0","durationMillis":"100","intensityScoreNormalized":1}]}}}}]}}}
                """.trimIndent(),
            )

        assertThat(VideoHeatmapParser.parse(chapters)).isNull()
    }

    @Test
    fun `a malformed payload hides the graph instead of throwing`() {
        assertThat(VideoHeatmapParser.parse(null)).isNull()
        assertThat(VideoHeatmapParser.parse(JsonNull)).isNull()
        assertThat(VideoHeatmapParser.parse(json.parseToJsonElement("""{"frameworkUpdates":"nope"}"""))).isNull()
        assertThat(VideoHeatmapParser.parse(json.parseToJsonElement("""[1,2,3]"""))).isNull()
    }

    @Test
    fun `markers missing a field are dropped rather than failing the list`() {
        val partial =
            json.parseToJsonElement(
                """
                {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":
                {"markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[
                {"startMillis":"0","durationMillis":"100","intensityScoreNormalized":0.5},
                {"startMillis":"100","intensityScoreNormalized":0.5},
                {"startMillis":"200","durationMillis":"0","intensityScoreNormalized":0.5},
                {"startMillis":"300","durationMillis":"100","intensityScoreNormalized":0.25}]}}}}]}}}
                """.trimIndent(),
            )

        val markers = VideoHeatmapParser.parse(partial)!!.markers

        assertThat(markers.map { it.startMs }).containsExactly(0L, 300L).inOrder()
    }

    @Test
    fun `a highlight with no label is dropped`() {
        val unlabelled =
            json.parseToJsonElement(
                """
                {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":
                {"markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[
                {"startMillis":"0","durationMillis":"100","intensityScoreNormalized":1}],
                "markersDecoration":{"timedMarkerDecorations":[
                {"visibleTimeRangeStartMillis":0,"visibleTimeRangeEndMillis":100}]}}}}}]}}}
                """.trimIndent(),
            )

        val heatmap = VideoHeatmapParser.parse(unlabelled)!!

        assertThat(heatmap.markers).hasSize(1)
        assertThat(heatmap.highlights).isEmpty()
    }
}
