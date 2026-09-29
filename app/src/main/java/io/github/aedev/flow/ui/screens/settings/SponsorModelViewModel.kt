package io.github.aedev.flow.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.sponsordetection.SponsorModelRepository
import io.github.aedev.flow.data.sponsordetection.SponsorModelState
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SponsorModelViewModel
    @Inject
    constructor(
        private val modelRepository: SponsorModelRepository,
        private val playerPreferences: PlayerPreferences,
        private val playerManager: EnhancedPlayerManager,
    ) : ViewModel() {
        val modelState: StateFlow<SponsorModelState> = modelRepository.state

        val enabled: StateFlow<Boolean> =
            playerPreferences.sponsorOnDeviceEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

        val trainingConsent =
            playerPreferences.sponsorTrainingConsentEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

        val trainingStats = playerManager.sponsorJournalStats

        init {
            modelRepository.refresh()
        }

        fun setEnabled(enabled: Boolean) {
            viewModelScope.launch {
                playerPreferences.setSponsorOnDeviceEnabled(enabled)
                if (enabled && modelRepository.state.value !is SponsorModelState.Installed) {
                    modelRepository.download()
                }
            }
        }

        fun download() = modelRepository.download()

        fun delete() = modelRepository.delete()

        fun setTrainingConsent(enabled: Boolean) {
            viewModelScope.launch { playerPreferences.setSponsorTrainingConsent(enabled) }
        }

        fun refreshTrainingStats() {
            viewModelScope.launch { playerManager.refreshSponsorJournalStats() }
        }

        suspend fun exportTrainingData(output: java.io.OutputStream) = playerManager.exportSponsorTrainingData(output)

        fun clearTrainingData() {
            viewModelScope.launch { playerManager.clearSponsorTrainingData() }
        }
    }
