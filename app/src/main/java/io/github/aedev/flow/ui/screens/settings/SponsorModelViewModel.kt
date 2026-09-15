package io.github.aedev.flow.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.sponsordetection.SponsorModelRepository
import io.github.aedev.flow.data.sponsordetection.SponsorModelState
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
    ) : ViewModel() {
        val modelState: StateFlow<SponsorModelState> = modelRepository.state

        val enabled: StateFlow<Boolean> =
            playerPreferences.sponsorOnDeviceEnabled.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

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
    }
