package io.github.aedev.flow.ui.screens.settings.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.utils.UpdateInfo
import io.github.aedev.flow.utils.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeepFlowState(
    val active: Boolean = false,
    val activatedAt: Long = 0L,
    val expireHours: Int = DeepFlowDefaults.EXPIRE_HOURS,
    val saveToHistory: Boolean = false,
)

private object DeepFlowDefaults {
    const val EXPIRE_HOURS = 4
}

/** The update check the settings list runs on request; github builds only. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data object UpToDate : UpdateCheckState

    data object Failed : UpdateCheckState

    data class Available(
        val info: UpdateInfo,
    ) : UpdateCheckState
}

@HiltViewModel
class SettingsHomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val playerPreferences: PlayerPreferences,
    ) : ViewModel() {
        val deepFlow: StateFlow<DeepFlowState> =
            combine(
                playerPreferences.deepFlowActive,
                playerPreferences.deepFlowActivatedAt,
                playerPreferences.deepFlowExpireHours,
                playerPreferences.deepFlowSaveToHistory,
            ) { active, activatedAt, expireHours, saveToHistory ->
                DeepFlowState(active, activatedAt, expireHours, saveToHistory)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DeepFlowState())

        private val _persona = MutableStateFlow<FlowPersona?>(null)
        val persona: StateFlow<FlowPersona?> = _persona.asStateFlow()

        private val _updateCheck = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
        val updateCheck: StateFlow<UpdateCheckState> = _updateCheck.asStateFlow()

        init {
            refreshPersona()
        }

        /** Re-reads the persona; the engine exposes no stream, so the list refreshes it when shown. */
        fun refreshPersona() {
            viewModelScope.launch {
                _persona.value = runCatching { FlowNeuroEngine.getPersona(FlowNeuroEngine.getBrainSnapshot()) }.getOrNull()
            }
        }

        fun setDeepFlowEnabled(enabled: Boolean) {
            viewModelScope.launch { DeepFlowManager.setEnabled(context, enabled) }
        }

        fun setDeepFlowExpireHours(hours: Int) {
            viewModelScope.launch { playerPreferences.setDeepFlowExpireHours(hours) }
        }

        fun setDeepFlowSaveToHistory(enabled: Boolean) {
            viewModelScope.launch { playerPreferences.setDeepFlowSaveToHistory(enabled) }
        }

        fun checkForUpdates() {
            if (!BuildConfig.UPDATER_ENABLED || _updateCheck.value == UpdateCheckState.Checking) return
            _updateCheck.value = UpdateCheckState.Checking
            viewModelScope.launch {
                val result =
                    runCatching { UpdateManager.fetchUpdate(BuildConfig.VERSION_NAME) }
                        .fold(
                            onSuccess = { info -> info?.let { UpdateCheckState.Available(it) } ?: UpdateCheckState.UpToDate },
                            onFailure = { UpdateCheckState.Failed },
                        )
                _updateCheck.value = result
            }
        }

        fun consumeUpdateCheck() {
            _updateCheck.update { if (it == UpdateCheckState.Checking) it else UpdateCheckState.Idle }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
