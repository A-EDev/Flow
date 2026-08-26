package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LifecyclePlaybackPreferencesTest {
    @Test
    fun `settings default to disabled before the preferences load`() =
        runTest {
            val preferences = LifecyclePlaybackPreferences(MutableStateFlow(LifecyclePlaybackSettings()))

            assertThat(preferences.settings).isEqualTo(LifecyclePlaybackSettings())
        }

    @Test
    fun `loaded settings outlive the observing scope`() =
        runTest {
            val source = MutableStateFlow(LifecyclePlaybackSettings())
            val preferences = LifecyclePlaybackPreferences(source)
            val activityScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(activityScope)

            source.value = LifecyclePlaybackSettings(backgroundPlayEnabled = true, shortsPipEnabled = true)
            // The wallpaper-change relaunch that destroys the activity mid-playback (#817).
            activityScope.cancel()

            assertThat(preferences.settings.backgroundPlayEnabled).isTrue()
            assertThat(preferences.settings.shortsPipEnabled).isTrue()
        }

    @Test
    fun `a new observing scope keeps tracking updates`() =
        runTest {
            val source = MutableStateFlow(LifecyclePlaybackSettings())
            val preferences = LifecyclePlaybackPreferences(source)
            val firstScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(firstScope)
            firstScope.cancel()

            val secondScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(secondScope)
            source.value = LifecyclePlaybackSettings(shortsBackgroundPlay = true)

            assertThat(preferences.settings.shortsBackgroundPlay).isTrue()
            secondScope.cancel()
        }
}
