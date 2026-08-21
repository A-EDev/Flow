package io.github.aedev.flow.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Generates the app's baseline profile.
 *
 * Run with a device connected (MIUI/HyperOS needs both "USB debugging (Security settings)" and
 * "Install via USB" enabled in Developer options):
 *   ./gradlew :app:generateGithubReleaseBaselineProfile
 *
 * Or on the managed emulator, which needs neither:
 *   ./gradlew :app:generateGithubReleaseBaselineProfile -PbaselineProfileEmulator=true
 *
 * Output lands in app/src/githubRelease/generated/baselineProfiles and should be committed.
 */
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /**
     * Startup only, so `startup-prof.txt` stays a tight subset of `baseline-prof.txt`.
     */
    @Test
    fun startup() =
        collectProfile(includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
        }

    /** The feed scroll journey, captured for the baseline profile only. */
    @Test
    fun scrollFeed() =
        collectProfile(includeInStartupProfile = false) {
            pressHome()
            startActivityAndWait()
            device.waitForIdle(UI_SETTLE_MS)

            flingFeed(Direction.DOWN, times = 3)
            flingFeed(Direction.UP, times = 1)
        }

    private fun collectProfile(
        includeInStartupProfile: Boolean,
        journey: MacrobenchmarkScope.() -> Unit,
    ) = rule.collect(
        packageName =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: error("targetAppId instrumentation argument was not supplied"),
        includeInStartupProfile = includeInStartupProfile,
        profileBlock = journey,
    )

    /**
     * Re-resolves the scrollable container before every gesture.
     */
    private fun MacrobenchmarkScope.flingFeed(
        direction: Direction,
        times: Int,
    ) {
        repeat(times) {
            val feed =
                device.wait(Until.findObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)
                    ?: return
            runCatching {
                feed.setGestureMargin(device.displayWidth / 5)
                feed.fling(direction)
            }
            device.waitForIdle(UI_SETTLE_MS)
        }
    }

    private companion object {
        const val UI_SETTLE_MS = 1_500L
        const val CONTENT_TIMEOUT_MS = 10_000L
    }
}
