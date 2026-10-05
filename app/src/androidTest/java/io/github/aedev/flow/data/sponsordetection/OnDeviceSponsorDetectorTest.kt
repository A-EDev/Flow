package io.github.aedev.flow.data.sponsordetection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class OnDeviceSponsorDetectorTest {
    @Before
    fun installModel() {
        installSponsorModelFromTestAssets(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun releasedOnnxMatchesPythonGoldensForAllCategories() =
        runBlocking {
            val detector = OnDeviceSponsorDetector(ApplicationProvider.getApplicationContext())
            val categories = mutableSetOf<String>()
            try {
                goldenCases().forEach { case ->
                    val fields = case.jsonObject
                    val cues =
                        listOf(
                            DetectionTranscriptCue(
                                0,
                                fields.getValue("duration_ms").jsonPrimitive.long,
                                fields.getValue("text").jsonPrimitive.content,
                            ),
                        )
                    val actual = detector.predictCues(cues)
                    val expected = fields.getValue("spans").jsonArray
                    assertEquals("Span count for " + fields.getValue("source_example_id"), expected.size, actual.size)
                    expected.zip(actual).forEach { (reference, prediction) ->
                        val span = reference.jsonObject
                        val category = span.getValue("category").jsonPrimitive.content
                        categories += prediction.category
                        assertEquals(category, prediction.category)
                        assertTrue(abs(span.getValue("start_ms").jsonPrimitive.long - prediction.startMs) <= 1)
                        assertTrue(abs(span.getValue("end_ms").jsonPrimitive.long - prediction.endMs) <= 1)
                        assertEquals(span.getValue("confidence").jsonPrimitive.double, prediction.confidence, 0.0001)
                    }
                }
                assertEquals(setOf("sponsor", "selfpromo", "interaction"), categories)
            } finally {
                detector.close()
            }
        }

    @Test
    fun closedSessionCanBeRecreatedAndReused() =
        runBlocking {
            val fields = goldenCases().first().jsonObject
            val detector = OnDeviceSponsorDetector(ApplicationProvider.getApplicationContext())
            val cues =
                listOf(
                    DetectionTranscriptCue(
                        0,
                        fields.getValue("duration_ms").jsonPrimitive.long,
                        fields.getValue("text").jsonPrimitive.content,
                    ),
                )
            try {
                val first = detector.predictCues(cues)
                val reused = detector.predictCues(cues)
                detector.close()
                val recreated = detector.predictCues(cues)

                assertTrue(first.isNotEmpty())
                assertEquals(first, reused)
                assertEquals(first, recreated)
            } finally {
                detector.close()
            }
        }

    private fun goldenCases() =
        InstrumentationRegistry.getInstrumentation().context.assets.open("inference_goldens.json").use { input ->
            Json
                .parseToJsonElement(input.bufferedReader().readText())
                .jsonObject
                .getValue("cases")
                .jsonArray
        }
}
