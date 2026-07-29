package io.github.aedev.flow.player.shorts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShortsPreferenceObserversTest {
    @Test
    fun restartAndStopKeepOnlyCurrentCollectorsActive() = runTest {
        val firstLanguage = MutableStateFlow("original")
        val firstMode = MutableStateFlow("loop")
        val firstSpeed = MutableStateFlow(1f)
        val secondLanguage = MutableStateFlow("en")
        val secondMode = MutableStateFlow("swipe")
        val secondSpeed = MutableStateFlow(1.25f)
        val languages = mutableListOf<String>()
        val modes = mutableListOf<String>()
        val speeds = mutableListOf<Float>()
        val observers = ShortsPreferenceObservers(
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        observers.start(
            preferredAudioLanguage = firstLanguage,
            playbackMode = firstMode,
            playbackSpeed = firstSpeed,
            onPreferredAudioLanguage = languages::add,
            onPlaybackMode = modes::add,
            onPlaybackSpeed = speeds::add,
        )
        runCurrent()

        assertTrue(observers.isRunning)
        assertEquals(listOf("original"), languages)
        assertEquals(listOf("loop"), modes)
        assertEquals(listOf(1f), speeds)

        observers.start(
            preferredAudioLanguage = secondLanguage,
            playbackMode = secondMode,
            playbackSpeed = secondSpeed,
            onPreferredAudioLanguage = languages::add,
            onPlaybackMode = modes::add,
            onPlaybackSpeed = speeds::add,
        )
        runCurrent()

        firstLanguage.value = "fr"
        firstMode.value = "auto"
        firstSpeed.value = 2f
        secondLanguage.value = "de"
        secondMode.value = "loop"
        secondSpeed.value = 1.5f
        runCurrent()

        assertEquals(listOf("original", "en", "de"), languages)
        assertEquals(listOf("loop", "swipe", "loop"), modes)
        assertEquals(listOf(1f, 1.25f, 1.5f), speeds)

        observers.stop()
        secondLanguage.value = "es"
        secondMode.value = "auto"
        secondSpeed.value = 0.75f
        runCurrent()

        assertFalse(observers.isRunning)
        assertEquals(listOf("original", "en", "de"), languages)
        assertEquals(listOf("loop", "swipe", "loop"), modes)
        assertEquals(listOf(1f, 1.25f, 1.5f), speeds)
    }
}
