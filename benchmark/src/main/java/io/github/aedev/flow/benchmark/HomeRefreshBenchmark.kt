package io.github.aedev.flow.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Home load from a cold start to a settled feed, split into the network wave and the ranking
 * passes, so a slow Home can be blamed on the right part (#1301).
 *
 * Run with:
 *   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --no-configuration-cache \
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.github.aedev.flow.benchmark.HomeRefreshBenchmark
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class HomeRefreshBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun homeLoad() {
        val targetPackage =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: error("targetAppId instrumentation argument was not supplied")

        rule.measureRepeated(
            packageName = targetPackage,
            metrics =
                listOf(
                    TraceSectionMetric("neuro.init", TraceSectionMetric.Mode.First),
                    TraceSectionMetric("home.wave1", TraceSectionMetric.Mode.First),
                    TraceSectionMetric("neuro.rank", TraceSectionMetric.Mode.Sum),
                    TraceSectionMetric("neuro.rank", TraceSectionMetric.Mode.Count),
                ),
            compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait { intent ->
                intent.putExtra("io.github.aedev.flow.extra.BENCHMARK_BYPASS_ONBOARDING", true)
            }
            device.wait(Until.hasObject(By.res("home_video_card")), HOME_TIMEOUT_MS)
        }
    }

    private companion object {
        const val HOME_TIMEOUT_MS = 30_000L
    }
}
