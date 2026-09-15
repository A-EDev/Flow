package io.github.aedev.flow.data.sponsordetection

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class OnDeviceSponsorBenchmarkTest {
    @Before
    fun installModel() {
        installSponsorModelFromTestAssets(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun sweepRuntimeConfigurations() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val transcript =
                listOf(
                    DetectionTranscriptCue(
                        startMs = 0L,
                        endMs = 3_600_000L,
                        text = " normal".repeat(SYNTHETIC_TOKEN_COUNT),
                    ),
                )
            val arguments = InstrumentationRegistry.getArguments()
            val hardwareDefault = SponsorInferenceRuntimeConfig.forAvailableHardware()
            val config =
                SponsorInferenceRuntimeConfig(
                    batchSize = arguments.getString("batchSize")?.toIntOrNull() ?: hardwareDefault.batchSize,
                    intraOpThreads =
                        arguments.getString("intraOpThreads")?.toIntOrNull()
                            ?: hardwareDefault.intraOpThreads,
                )
            Log.i(
                TAG,
                "Benchmark starting: batchSize=${config.batchSize} " +
                    "intraOpThreads=${config.intraOpThreads}",
            )
            val detector =
                OnDeviceSponsorDetector(
                    context = context,
                    playbackPositionMs = { 0L },
                    runtimeConfig = config,
                )
            try {
                var spans = emptyList<SponsorDetectionSpan>()
                val elapsedMs =
                    withTimeoutOrNull(PER_CONFIG_TIMEOUT_MS) {
                        measureTimeMillis {
                            spans = detector.predictCues(transcript)
                        }
                    }
                if (elapsedMs == null) {
                    Log.e(
                        TAG,
                        "Benchmark timed out: batchSize=${config.batchSize} " +
                            "intraOpThreads=${config.intraOpThreads}",
                    )
                } else {
                    Log.i(
                        TAG,
                        "Benchmark result: batchSize=${config.batchSize} " +
                            "intraOpThreads=${config.intraOpThreads} totalMs=$elapsedMs " +
                            "spans=${spans.size}",
                    )
                }
            } finally {
                detector.close()
            }
        }
    }

    private companion object {
        const val TAG = "SponsorBenchmark"
        const val PER_CONFIG_TIMEOUT_MS = 90_000L
        const val SYNTHETIC_TOKEN_COUNT = 5_000
    }
}
