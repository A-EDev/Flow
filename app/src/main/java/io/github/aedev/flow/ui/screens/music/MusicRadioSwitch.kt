package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The queue sheet's endless-radio switch. It shows on for the saved setting or for a station the
 * user started by name, so one tap stops whichever is running; turning it off ends that station too.
 */
internal class MusicRadioSwitch(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicPlayerUiState>,
    private val playerPreferences: PlayerPreferences,
    private val stationActive: StateFlow<Boolean> = EnhancedMusicPlayerManager.radioStationActive,
    private val endStation: () -> Unit = { EnhancedMusicPlayerManager.setRadioStationActive(false) },
) {
    fun observe() {
        scope.launch {
            combine(playerPreferences.musicEndlessRadioEnabled, stationActive) { enabled, station -> enabled || station }
                .collect { on -> uiState.update { it.copy(endlessRadioEnabled = on) } }
        }
    }

    fun set(enabled: Boolean) {
        if (!enabled) endStation()
        scope.launch { playerPreferences.setMusicEndlessRadioEnabled(enabled) }
    }
}
