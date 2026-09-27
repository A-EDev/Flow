package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    fun `awaiting loaded settings suspends until first emission`() =
        runTest {
            val source = MutableSharedFlow<LifecyclePlaybackSettings>()
            val preferences = LifecyclePlaybackPreferences(source)
            val observingScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(observingScope)
            var loadedSettings: LifecyclePlaybackSettings? = null
            val awaiting = launch { loadedSettings = preferences.awaitLoadedSettings() }

            assertThat(awaiting.isCompleted).isFalse()

            val persistedSettings = LifecyclePlaybackSettings(clipboardLinkOpenEnabled = false)
            source.emit(persistedSettings)
            awaiting.join()
            observingScope.cancel()

            assertThat(loadedSettings).isEqualTo(persistedSettings)
        }

    @Test
    fun `await loaded settings returns disabled first emission`() =
        runTest {
            val source = MutableStateFlow(LifecyclePlaybackSettings(clipboardLinkOpenEnabled = false))
            val preferences = LifecyclePlaybackPreferences(source)
            val observingScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(observingScope)

            assertThat(preferences.awaitLoadedSettings().clipboardLinkOpenEnabled).isFalse()
            observingScope.cancel()
        }

    @Test
    fun `await loaded settings returns latest value after updates`() =
        runTest {
            val source = MutableStateFlow(LifecyclePlaybackSettings(clipboardLinkOpenEnabled = true))
            val preferences = LifecyclePlaybackPreferences(source)
            val observingScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            preferences.observeIn(observingScope)

            assertThat(preferences.awaitLoadedSettings().clipboardLinkOpenEnabled).isTrue()

            source.value = LifecyclePlaybackSettings(clipboardLinkOpenEnabled = false)
            assertThat(preferences.awaitLoadedSettings().clipboardLinkOpenEnabled).isFalse()

            source.value = LifecyclePlaybackSettings(clipboardLinkOpenEnabled = true)
            assertThat(preferences.awaitLoadedSettings().clipboardLinkOpenEnabled).isTrue()
            observingScope.cancel()
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
