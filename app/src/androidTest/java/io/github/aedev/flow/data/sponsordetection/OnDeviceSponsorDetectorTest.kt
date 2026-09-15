package io.github.aedev.flow.data.sponsordetection

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnDeviceSponsorDetectorTest {
    @Before
    fun installModel() {
        installSponsorModelFromTestAssets(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun validatedOrtModelDetectsSponsorCopy() =
        runBlocking {
            val detector = OnDeviceSponsorDetector(ApplicationProvider.getApplicationContext())
            try {
                val spans =
                    detector.predictCues(
                        listOf(
                            DetectionTranscriptCue(
                                startMs = 0,
                                endMs = 10_000,
                                text =
                                    "This episode is brought to you by Acme. " +
                                        "Visit acme dot com and use code FLOW for twenty percent off.",
                            ),
                        ),
                    )

                assertEquals(1, spans.size)
                assertEquals(0, spans.single().startMs)
                assertEquals(10_000, spans.single().endMs)
                assertEquals(0.940055, spans.single().confidence, 0.01)
            } finally {
                detector.close()
            }
        }

    @Test
    fun closedSessionCanBeRecreatedAndReused() =
        runBlocking {
            val detector = OnDeviceSponsorDetector(ApplicationProvider.getApplicationContext())
            val cues =
                listOf(
                    DetectionTranscriptCue(
                        startMs = 0,
                        endMs = 10_000,
                        text =
                            "This episode is brought to you by Acme. " +
                                "Visit acme dot com and use code FLOW for twenty percent off.",
                    ),
                )
            try {
                val first = detector.predictCues(cues)
                val reused = detector.predictCues(cues)
                detector.close()
                val recreated = detector.predictCues(cues)

                assertEquals(first, reused)
                assertEquals(first.size, recreated.size)
                assertEquals(first.single().startMs, recreated.single().startMs)
            } finally {
                detector.close()
            }
        }
}
