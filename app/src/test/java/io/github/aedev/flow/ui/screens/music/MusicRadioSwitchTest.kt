package io.github.aedev.flow.ui.screens.music

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlayerPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicRadioSwitchTest {
    private val setting = MutableStateFlow(false)
    private val station = MutableStateFlow(false)
    private val uiState = MutableStateFlow(MusicPlayerUiState())
    private val preferences =
        mockk<PlayerPreferences> {
            every { musicEndlessRadioEnabled } returns setting
            coEvery { setMusicEndlessRadioEnabled(any()) } answers { setting.value = firstArg() }
        }

    private fun TestScope.switch() =
        MusicRadioSwitch(backgroundScope, uiState, preferences, station) { station.value = false }
            .also {
                it.observe()
                runCurrent()
            }

    @Test
    fun `shows on while a started station plays with the setting off`() =
        runTest {
            switch()
            assertThat(uiState.value.endlessRadioEnabled).isFalse()

            station.value = true
            runCurrent()

            assertThat(uiState.value.endlessRadioEnabled).isTrue()
            assertThat(setting.value).isFalse()
        }

    @Test
    fun `one tap off ends the station and keeps the setting off`() =
        runTest {
            station.value = true
            val switch = switch()

            switch.set(false)
            runCurrent()

            assertThat(station.value).isFalse()
            assertThat(setting.value).isFalse()
            assertThat(uiState.value.endlessRadioEnabled).isFalse()
        }

    @Test
    fun `turning it off with the setting on clears both`() =
        runTest {
            setting.value = true
            station.value = true
            val switch = switch()

            switch.set(false)
            runCurrent()

            assertThat(station.value).isFalse()
            assertThat(setting.value).isFalse()
        }

    @Test
    fun `turning it on saves the setting`() =
        runTest {
            val switch = switch()

            switch.set(true)
            runCurrent()

            assertThat(setting.value).isTrue()
            assertThat(uiState.value.endlessRadioEnabled).isTrue()
        }
}
